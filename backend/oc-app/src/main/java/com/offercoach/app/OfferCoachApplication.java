package com.offercoach.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.offercoach")
@ConfigurationPropertiesScan("com.offercoach")
@MapperScan(basePackages = "com.offercoach", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class OfferCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferCoachApplication.class, args);
    }
}
