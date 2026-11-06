package dev.elasticsearchmicroservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    /**
     * HTTP client for the external log sources, built from Spring Boot's auto-configured builder so its message
     * converters and timeouts apply.
     * <p>
     * Every request carries HTTP Basic credentials (same as {@code curl -u username:password}) when
     * {@code remote-log.username} is set. All sources share these credentials, so they are sent to every registered
     * source; files are only downloaded from under the directory of a source's listing URL.
     *
     * @param builder             auto-configured builder
     * @param remoteLogProperties credentials shared by all log sources
     * @return client for calls to the external log sources
     */
    @Bean
    public RestClient remoteLogRestClient(RestClient.Builder builder, RemoteLogProperties remoteLogProperties) {
        var username = remoteLogProperties.getUsername();
        if (username == null || username.isBlank()) {
            return builder.build();
        }

        return builder
                .defaultHeaders(headers -> headers.setBasicAuth(username, remoteLogProperties.getPassword()))
                .build();
    }
}
