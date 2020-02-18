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
public class SupplyFun {

    public Logger logger = Logger.getLogger("myLogger");
    public FileHandler fileHandler = new FileHandler("/Users/havyapanchal/Desktop/LogFiles/logs_1.log");
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ProductFun productFun;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    public SupplyFun() throws IOException {
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
        System.out.println("prodId: " + prodId + " ven: " + vendorId);
        CompositeKey key = new CompositeKey(prodId, vendorId);
        query.addCriteria(Criteria.where("id").is(key));
        Supply supply = mongoTemplate.findOne(query, Supply.class);
        System.out.println(supply.getQty() + " and " + qty);
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

        Product product = productFun.findProductById(prodId);

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
        System.out.println(supply.size() + " is size");
        List<ViewSupply> ls = new ArrayList<ViewSupply>();
        Product product = productFun.findProductById(productId);
        for (int i = 0; i < supply.size(); i++) {
            //System.out.println(i) ;
            String vendorId = supply.get(i).getId().getVendorId();
            String prodid = supply.get(i).getId().getProdId();
            if (!(productId.equals(prodid)))
                continue;
            int price = supply.get(i).getPrice();
            Vendor vendor = mongoTemplate.findById(vendorId, Vendor.class);
            int qty = supply.get(i).getQty();
            System.out.println(product.getProdId() + " " + vendor.getVendorId() + " " + qty + " " + price);
            ls.add(new ViewSupply(product, vendor, qty, price));
        }
        Collections.sort(ls);
        return ls;
    }

    public void addVendorTransaction(String vendorId, String productId, int qty, int price) throws ExecutionException, InterruptedException {

        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        Date temp = new Date(Long.MIN_VALUE);
        System.out.println(temp);
        int buffer = 0;
        for (int i = 0; i < transactions.size(); i++) {
            String vid = transactions.get(i).getVendorId();
            String pid = transactions.get(i).getProdId();
            int currentPrice = transactions.get(i).getPrice();
            Date transactionTime = transactions.get(i).getTimeStamp();
            if (vid.equals(vendorId) && pid.equals(productId) && price == currentPrice && temp.compareTo(transactionTime) < 0) {
                temp = transactionTime;
                buffer = transactions.get(i).getAvailableQty();
            }
        }
        Calendar calendar = Calendar.getInstance();
        Date currentTime = calendar.getTime();
        Transaction transaction = new Transaction(productId, vendorId, qty, price, currentTime, buffer + qty);
        mongoTemplate.save(transaction, "transaction");
        Product product = productFun.findProductById(productId);
        product.setPrice(Math.min(product.getPrice(), price));
        Gson gson = new Gson();
        String json = gson.toJson(product);
        UpdateRequest request = new UpdateRequest("elasticsearch", "product", productId);
        request.doc(json, XContentType.JSON);
        elasticsearchTemplate.getClient().update(request).get();
    }

    public List<ViewSupply> findProductByTransaction(String productId) {
        /*String previous = "";
        List<Transaction> transactions = mongoTemplate.query(Transaction.class).all();
        Collections.sort(transactions);
        Product product = productFun.findProductById(productId);
        List<ViewSupply> viewSupplies = new ArrayList<ViewSupply>();
        Map<String, Boolean> flag = new HashMap<String, Boolean>();
        Set<Map<Object,Object>> set = new HashSet<>() ;
        for (int i = 0; i < transactions.size(); i++) {
            String currentVendorId = transactions.get(i).getVendorId();
            String currentProductId = transactions.get(i).getProdId();

            if (flag.containsKey(currentVendorId) == false && productId.equals(currentProductId)) {
                Map<Object,Object> temp = new HashMap<>() ;
                temp.put("productId",productId);
                temp.put("vendorId",currentVendorId);
                int price1 = transactions.get(i).getPrice();
                temp.put("price",(int) price1);
                Vendor vendor = mongoTemplate.findById(currentVendorId, Vendor.class);
                //Do for loop on PID,VID and price and find all transaction with these values and
                // Give latest transaction of that list
                List<Transaction> configTransactions = new ArrayList<>() ;
                previous = currentVendorId;
                viewSupplies.add(new ViewSupply(product,vendor,transactions.get(i).getAvailableQty(),price1));//not useful
                flag.put(currentVendorId, true);
                set.add(temp) ;
            }
        }
        for(int i=0;i<viewSupplies.size();i++)
        {
            System.out.println(viewSupplies.get(i).getQty());
        }
        return viewSupplies;*/
        Product product = productFun.findProductById(productId);
        Query query = new Query();
        query.addCriteria(Criteria.where("prodId").is(productId));
        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        Map<String, Date> sdMap = new HashMap<>();
        Map<String, Map<Integer, Integer>> container = new HashMap<>();
        for (int i = 0; i < transactions.size(); i++) {
            String vId = transactions.get(i).getVendorId();
            Date currDate = transactions.get(i).getTimeStamp();
            if (sdMap.containsKey(vId) == false) {
                sdMap.put(vId, currDate);
                Map<Integer, Integer> mp = new HashMap<>();
                mp.put(transactions.get(i).getAvailableQty(), transactions.get(i).getPrice());
                container.put(vId, mp);
            } else {
                if (sdMap.get(vId).compareTo(currDate) < 0) // currDate is latest
                {
                    sdMap.remove(vId);
                    sdMap.put(vId, currDate);
                    Map<Integer, Integer> mp = new HashMap<>();
                    mp.put(transactions.get(i).getAvailableQty(), transactions.get(i).getPrice());
                    if (container.containsKey(vId)) {
                        container.replace(vId, mp);
                    } else {
                        container.put(vId, mp);
                    }
                }
            }
        }
        List<ViewSupply> list = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Integer>> entry : container.entrySet()) {
            String vId = entry.getKey();
            Vendor vendor = mongoTemplate.findById(vId, Vendor.class);
            Map<Integer, Integer> mp = entry.getValue();
            for (Map.Entry<Integer, Integer> entry1 : mp.entrySet()) {
                list.add(new ViewSupply(product, vendor, entry1.getKey(), entry1.getValue()));
            }
        }
        return list;
    }

    public void addUserTransaction(String vendorId, String productId, int qty, int price) {
        /*Criteria byVendorId = Criteria.where("vendorId").is(vendorId);
        Criteria byProdId = Criteria.where("prodId").is(prodId);
        Criteria byPrice = Criteria.where("price").is(price);
        Criteria criteria = new Criteria().andOperator(byVendorId, byPrice);
        Criteria criteria1 = new Criteria().andOperator(criteria,byProdId) ;
        Criteria criteria = new Criteria().andOperator(byPrice,byProdId,byVendorId) ;
        Query query = new Query(criteria);
        Transaction transaction = mongoTemplate.findOne(query,Transaction.class) ;
        transaction.setQty(transaction.getQty() - qty);*/

        Calendar calendar = Calendar.getInstance();
        Date currentTime = calendar.getTime();
        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        Date temp = new Date(Long.MIN_VALUE);
        int buffer = 0;
        for (int i = 0; i < transactions.size(); i++) {
            String vid = transactions.get(i).getVendorId();
            String pid = transactions.get(i).getProdId();
            int currentPrice = transactions.get(i).getPrice();
            Date transactionTime = transactions.get(i).getTimeStamp();
            if (vid.equals(vendorId) && pid.equals(productId) && price == currentPrice && temp.compareTo(transactionTime) < 0) {
                temp = transactionTime;
                buffer = transactions.get(i).getAvailableQty();
            }
        }
        System.out.println();
        Transaction transaction = new Transaction(productId, vendorId, -qty, price, currentTime, buffer - qty);
        mongoTemplate.save(transaction, "transaction");
    }

    public List<Supply> getSupply() {
        return mongoTemplate.findAll(Supply.class);
    }
}
