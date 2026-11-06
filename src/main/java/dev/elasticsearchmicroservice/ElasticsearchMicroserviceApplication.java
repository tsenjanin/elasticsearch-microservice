package dev.elasticsearchmicroservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ElasticsearchMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElasticsearchMicroserviceApplication.class, args);
    }

}
