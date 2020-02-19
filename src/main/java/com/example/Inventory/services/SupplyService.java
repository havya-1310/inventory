package com.example.Inventory.services;

import com.example.Inventory.models.*;
import com.google.gson.Gson;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@Service
public class SupplyService {

    public Logger logger = Logger.getLogger("myLogger");
    public FileHandler fileHandler = new FileHandler("/Users/havyapanchal/Desktop/LogFiles/logs_1.log");
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ProductService productService;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    public SupplyService() throws IOException {
    }

    public Supply addSupply(Supply supply) {
        Query query = new Query();

        return mongoTemplate.save(supply);
    }

    public boolean reduceSupply(String vendorId, String prodId, int qty, String user_id) {
        logger.addHandler(fileHandler);
        SimpleFormatter simpleFormatter = new SimpleFormatter();
        fileHandler.setFormatter(simpleFormatter);

        Calendar calendar = Calendar.getInstance();
        Date timestamp = calendar.getTime();

        Order order = new Order(user_id, prodId, vendorId, qty, timestamp);
        mongoTemplate.save(order);

        Query query = new Query();
        CompositeKey key = new CompositeKey(prodId, vendorId);
        query.addCriteria(Criteria.where("id").is(key));
        Supply supply = mongoTemplate.findOne(query, Supply.class);

        if (supply.getQty() < qty) {
            logger.warning("Invalid attempt for purchase by UserID: " + user_id);
            return false;
        } else {
            supply.setQty(supply.getQty() - qty);
            mongoTemplate.save(supply);
            logger.info("UserID: " + user_id + " purchased ProdID: " + prodId
                    + " from VendorID: " + vendorId + " of qty: " + qty);
            return true;
        }

    }

    public void addSupply(String vendorId, String prodId, int qty, int price) {
        Calendar calendar = Calendar.getInstance();
        Date timestamp = calendar.getTime();
        logger.addHandler(fileHandler);
        SimpleFormatter simpleFormatter = new SimpleFormatter();
        fileHandler.setFormatter(simpleFormatter);
        Query query = new Query();

        CompositeKey temp = new CompositeKey(prodId, vendorId);

        query.addCriteria(Criteria.where("id").is(temp));

        Supply supply = mongoTemplate.findOne(query, Supply.class);

        Product product = productService.findProductById(prodId);

        if (supply == null) {

            Supply supp = new Supply(qty, temp, price, timestamp);
            logger.info("VendorID: " + vendorId + " initiated supply of ProdID: " + prodId);
            mongoTemplate.save(supp);
        } else {
            supply.setPrice(price);
            supply.setQty(supply.getQty() + qty);
            logger.info("Vendor: " + vendorId + " increased supply for productID: " + prodId
                    + " by qty: " + qty);

            mongoTemplate.save(supply);
        }
        product.setPrice(Math.min(price, product.getPrice()));
        mongoTemplate.save(product, "product");
    }

    public List<ViewSupply> getProduct(String productId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("prodId").is(productId));
        List<Supply> supply = mongoTemplate.findAll(Supply.class);
        List<ViewSupply> ls = new ArrayList<ViewSupply>();
        Product product = productService.findProductById(productId);
        for (int i = 0; i < supply.size(); i++) {
            String currentVendorId = supply.get(i).getId().getVendorId();
            String currentProductId = supply.get(i).getId().getProdId();
            if (!(productId.equals(currentProductId)))
                continue;
            int price = supply.get(i).getPrice();
            Vendor vendor = mongoTemplate.findById(currentVendorId, Vendor.class);
            int qty = supply.get(i).getQty();
            ls.add(new ViewSupply(product, vendor, qty, price));
        }
        Collections.sort(ls);
        return ls;
    }

    public void addVendorTransaction(String vendorId, String productId, int qty, int price) throws ExecutionException, InterruptedException {

        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        Date temp = new Date(Long.MIN_VALUE);
        int buffer = 0;
        for (int i = 0; i < transactions.size(); i++) {
            String currentVendorId = transactions.get(i).getVendorId();
            String currentProductId = transactions.get(i).getProdId();
            int currentPrice = transactions.get(i).getPrice();
            Date transactionTime = transactions.get(i).getTimeStamp();
            if (currentVendorId.equals(vendorId) && currentProductId.equals(productId) && price == currentPrice && temp.compareTo(transactionTime) < 0) {
                temp = transactionTime;
                buffer = transactions.get(i).getAvailableQty();
            }
        }
        Calendar calendar = Calendar.getInstance();
        Date currentTime = calendar.getTime();
        Transaction transaction = new Transaction(productId, vendorId, qty, price, currentTime, buffer + qty);
        mongoTemplate.save(transaction, "transaction");

        Product product = productService.findProductById(productId);
        product.setPrice(Math.min(product.getPrice(), price));

        Gson gson = new Gson();
        String json = gson.toJson(product);
        UpdateRequest request = new UpdateRequest("elasticsearch", "product", productId);
        request.doc(json, XContentType.JSON);

        elasticsearchTemplate.getClient().update(request).get();
    }

    public List<ViewSupply> findProductByTransaction(String productId) {
        Product product = productService.findProductById(productId);
        Query query = new Query();
        query.addCriteria(Criteria.where("prodId").is(productId));
        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        Map<String, Date> sdMap = new HashMap<>();
        Map<String, Map<Integer, Integer>> container = new HashMap<>();
        for (int i = 0; i < transactions.size(); i++) {
            String vendorId = transactions.get(i).getVendorId();
            Date transactionDate = transactions.get(i).getTimeStamp();
            if (sdMap.containsKey(vendorId) == false) {
                sdMap.put(vendorId, transactionDate);
                Map<Integer, Integer> mp = new HashMap<>();
                mp.put(transactions.get(i).getAvailableQty(), transactions.get(i).getPrice());
                container.put(vendorId, mp);
            } else {
                if (sdMap.get(vendorId).compareTo(transactionDate) < 0)
                {
                    sdMap.remove(vendorId);
                    sdMap.put(vendorId, transactionDate);
                    Map<Integer, Integer> mp = new HashMap<>();
                    mp.put(transactions.get(i).getAvailableQty(), transactions.get(i).getPrice());
                    if (container.containsKey(vendorId)) {
                        container.replace(vendorId, mp);
                    } else {
                        container.put(vendorId, mp);
                    }
                }
            }
        }
        List<ViewSupply> list = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Integer>> entry : container.entrySet()) {
            String vendorId = entry.getKey();
            Vendor vendor = mongoTemplate.findById(vendorId, Vendor.class);
            Map<Integer, Integer> mp = entry.getValue();
            for (Map.Entry<Integer, Integer> entry1 : mp.entrySet()) {
                list.add(new ViewSupply(product, vendor, entry1.getKey(), entry1.getValue()));
            }
        }
        return list;
    }

    public void addUserTransaction(String vendorId, String productId, int qty, int price) {

        Calendar calendar = Calendar.getInstance();
        Date currentTime = calendar.getTime();
        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        Date temp = new Date(Long.MIN_VALUE);
        int buffer = 0;
        for (int i = 0; i < transactions.size(); i++) {
            String currentVendorId = transactions.get(i).getVendorId();
            String currentProductId = transactions.get(i).getProdId();
            int currentPrice = transactions.get(i).getPrice();
            Date transactionTime = transactions.get(i).getTimeStamp();
            if (currentVendorId.equals(vendorId) && currentProductId.equals(productId) && price == currentPrice && temp.compareTo(transactionTime) < 0) {
                temp = transactionTime;
                buffer = transactions.get(i).getAvailableQty();
            }
        }
        Transaction transaction = new Transaction(productId, vendorId, -qty, price, currentTime, buffer - qty);
        mongoTemplate.save(transaction, "transaction");
    }

    public List<Supply> getSupply() {
        return mongoTemplate.findAll(Supply.class);
    }
}
