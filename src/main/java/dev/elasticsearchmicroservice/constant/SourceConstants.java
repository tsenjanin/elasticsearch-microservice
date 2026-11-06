package dev.elasticsearchmicroservice.constant;

import java.util.Set;
import java.util.regex.Pattern;

public final class SourceConstants {
    public static final String SOURCE_INDEX = "log-source";
    public static final String SOURCE_INDEX_MAPPING_PATH = "elasticsearch/source-index-mapping.json";
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,49}$");
    public static final Pattern INDEX_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,199}$");
    public static final Pattern DATA_STREAM_INDEX_PATTERN = Pattern.compile("^logs-.+-.+$");
    public static final Integer MAX_SOURCES = 1000;
    public static final Set<String> HTTP_SCHEMES = Set.of("http", "https");
    public static final String GROUP_FIELD = "group";
    public static final String NAME_FIELD = "name";
    public static final String INDEX_FIELD = "index";
    public static final String ID_SEPARATOR = ":";
    public static final String INDEX_SEPARATOR = "-";
    public static final String CURRENT_DIRECTORY = ".";

    private SourceConstants() {
    }
}
