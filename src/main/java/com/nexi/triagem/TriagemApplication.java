package com.nexi.triagem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TriagemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriagemApplication.class, args);
    }
}
