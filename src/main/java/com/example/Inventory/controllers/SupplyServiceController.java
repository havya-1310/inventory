package com.example.Inventory.controllers;

import com.example.Inventory.services.SupplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
public class SupplyServiceController {

    @Autowired
    SupplyService supplyService;

    @CrossOrigin
    @PostMapping("/inventory/supply")
    public boolean addSupply(@RequestParam(name = "vendorId") String vendorId, @RequestParam(name = "prodId") String productId, @RequestParam(name = "qty") int qty, @RequestParam(name = "price") int price) throws ExecutionException, InterruptedException {
        supplyService.addSupply(vendorId, productId, qty, price);
        supplyService.addVendorTransaction(vendorId,productId,qty,price) ;
        return true;
    }

}

