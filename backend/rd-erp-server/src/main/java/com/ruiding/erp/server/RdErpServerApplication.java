package com.ruiding.erp.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cn.iocoder.yudao.module", "com.ruiding.erp"})
@MapperScan(basePackages = "com.ruiding.erp.dal.mysql", annotationClass = Mapper.class)
public class RdErpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RdErpServerApplication.class, args);
    }

}
