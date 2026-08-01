package com.lawyus.snackstore.product.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.lawyus.snackstore.product.search.client")
@EnableScheduling
@SpringBootApplication
public class ProductSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductSearchServiceApplication.class, args);
    }
}
