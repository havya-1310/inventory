package com.example.Inventory.controllers;

import com.example.Inventory.models.Product;
import com.example.Inventory.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class ProductServiceController {

    @Autowired
    private ProductService productService;

    @PostMapping("/inventory/product")
    public void saveProduct(@RequestBody Product product) throws IOException{
        productService.saveProduct(product) ;
    }

    @GetMapping("/inventory/product")
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/inventory/product/elastic/search")
    public List<Product> getProductsFromSearch(@RequestParam("query") String query) {
        return productService.findProductByNameAndDescription(query);
    }
    @GetMapping("/inventory/product/autocomplete")
    public List<Product> getAutocomplete(@RequestParam("query") String query) {
        return productService.search(query);
    }

}
