package dev.elasticsearchmicroservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteLogClient {
    private final RestClient remoteLogRestClient;

    /**
     * Fetches the HTML listing of log files of one source.
     *
     * @param url full listing URL of the source
     * @return raw HTML; empty when the service returns no body
     * @throws org.springframework.web.client.RestClientException when the request fails
     */
    public String getLogListing(URI url) {
        log.info("Fetching log listing '{}'.", url);
        var listing = remoteLogRestClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        return listing == null ? "" : listing;
    }

    /**
     * Downloads one log file.
     *
     * @param uri absolute file location, resolved from an href in the listing
     * @return file content; empty when the service returns no body
     * @throws org.springframework.web.client.RestClientException when the request fails
     */
    public byte[] getLogFile(URI uri) {
        log.info("Downloading log file '{}'.", uri);
        var content = remoteLogRestClient.get()
                .uri(uri)
                .retrieve()
                .body(byte[].class);

        return content == null ? new byte[0] : content;
    }
}
