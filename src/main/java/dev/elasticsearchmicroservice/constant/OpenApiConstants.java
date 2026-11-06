package dev.elasticsearchmicroservice.constant;

public final class OpenApiConstants {
    public static final String API_TITLE = "Elasticsearch Microservice API";
    public static final String API_DESCRIPTION = """
            Imports ECS-formatted application logs from groups of registered sources into Elasticsearch, \
            one index per source, and removes logs past the retention period.""";
    public static final String API_VERSION = "0.0.1";

    private OpenApiConstants() {
    }
}
