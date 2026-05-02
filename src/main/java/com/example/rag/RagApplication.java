package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 应用启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.example.rag.mapper")
public class RagApplication {

    /** 启动 Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
