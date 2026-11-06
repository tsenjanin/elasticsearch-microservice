package dev.elasticsearchmicroservice.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "remote-log")
public class RemoteLogProperties {
    private String username;
    @ToString.Exclude
    private String password;
}
