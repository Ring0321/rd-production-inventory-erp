package com.ruiding.erp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cn.iocoder.yudao.module", "com.ruiding.erp"})
public class RdErpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RdErpServerApplication.class, args);
    }

}
