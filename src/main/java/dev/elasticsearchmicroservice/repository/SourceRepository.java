package dev.elasticsearchmicroservice.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.elasticsearchmicroservice.dto.LogSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static dev.elasticsearchmicroservice.constant.LogConstants.DUPLICATE_STATUS;
import static dev.elasticsearchmicroservice.constant.SourceConstants.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SourceRepository {
    private static final List<SortOptions> GROUP_NAME_SORT = List.of(
            SortOptions.of(s -> s
                    .field(f -> f
                            .field(GROUP_FIELD)
                            .order(SortOrder.Asc))),
            SortOptions.of(s -> s
                    .field(f -> f
                            .field(NAME_FIELD)
                            .order(SortOrder.Asc)))
    );

    private final ElasticsearchClient elasticsearchClient;

    /**
     * Creates the source index with the mappings from {@code elasticsearch/source-index-mapping.json} when it does
     * not exist yet.
     *
     * @throws UncheckedIOException when Elasticsearch cannot be reached or the mapping file cannot be read
     */
    public void createSourceIndexIfMissing() {
        try {
            if (elasticsearchClient.indices().exists(e -> e.index(SOURCE_INDEX)).value()) {
                return;
            }

            try (var mapping = new ClassPathResource(SOURCE_INDEX_MAPPING_PATH).getInputStream()) {
                elasticsearchClient.indices().create(c -> c
                        .index(SOURCE_INDEX)
                        .withJson(mapping));
            }
            log.info("Created index '{}'.", SOURCE_INDEX);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create index '%s'.".formatted(SOURCE_INDEX), e);
        }
    }

    /**
     * Stores a source with {@code group:name} as document id. A {@code create} operation never overwrites, so an
     * existing name within the group is reported instead of replaced. The index is refreshed so the source is
     * listed immediately.
     *
     * @param source source to store
     * @return {@code true} when stored, {@code false} when the group already has a source with this name
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public Boolean insertSource(LogSource source) {
        try {
            elasticsearchClient.create(c -> c
                    .index(SOURCE_INDEX)
                    .id(getId(source.getGroup(), source.getName()))
                    .refresh(Refresh.True)
                    .document(source));

            return true;
        } catch (ElasticsearchException e) {
            if (DUPLICATE_STATUS.equals(e.status())) {
                return false;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to insert source '%s' in group '%s'.".formatted(source.getName(), source.getGroup()),
                    e
            );
        }
    }

    /**
     * Lists all sources of all groups, sorted by group, then name.
     *
     * @return all sources; empty when none was added yet
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public List<LogSource> getSources() {
        return searchSources(Query.of(q -> q.matchAll(m -> m)), MAX_SOURCES);
    }

    /**
     * Lists the sources of one group, sorted by name.
     *
     * @param group group name
     * @return the group's sources; empty when the group does not exist
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public List<LogSource> getSourcesForGroup(String group) {
        return searchSources(termQuery(GROUP_FIELD, group), MAX_SOURCES);
    }

    /**
     * Finds a source by group and name.
     *
     * @param group group name
     * @param name  source name
     * @return the source, or empty when the group has no source with this name
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public Optional<LogSource> getSource(String group, String name) {
        var query = Query.of(q -> q
                .bool(b -> b
                        .filter(termQuery(GROUP_FIELD, group))
                        .filter(termQuery(NAME_FIELD, name))));

        return searchSources(query, 1).stream().findFirst();
    }

    /**
     * Finds the source that writes into the given index.
     *
     * @param index index name
     * @return the source, or empty when no source uses this index
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public Optional<LogSource> getSourceForIndex(String index) {
        return searchSources(termQuery(INDEX_FIELD, index), 1).stream().findFirst();
    }

    /**
     * Deletes a source definition; its log index is not touched. The index is refreshed so the source disappears
     * from the listing immediately.
     *
     * @param group group name
     * @param name  source name
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public void deleteSource(String group, String name) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(SOURCE_INDEX)
                    .id(getId(group, name))
                    .refresh(Refresh.True));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete source '%s' in group '%s'.".formatted(name, group), e);
        }
    }

    /**
     * Searches sources, sorted by group, then name. A missing source index means no sources.
     *
     * @param query filter
     * @param size  maximum number of sources returned
     * @return matching sources
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    private List<LogSource> searchSources(Query query, Integer size) {
        try {
            return elasticsearchClient.search(s -> s
                                    .index(SOURCE_INDEX)
                                    .ignoreUnavailable(true)
                                    .size(size)
                                    .query(query)
                                    .sort(GROUP_NAME_SORT),
                            LogSource.class)
                    .hits()
                    .hits()
                    .stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to search sources in '%s'.".formatted(SOURCE_INDEX), e);
        }
    }

    /**
     * Builds an exact-match query on a keyword field.
     *
     * @param field keyword field
     * @param value exact value
     * @return term query
     */
    private Query termQuery(String field, String value) {
        return Query.of(q -> q
                .term(t -> t
                        .field(field)
                        .value(value)));
    }

    /**
     * Builds the document id of a source. Neither part can contain {@code :}, so the id is unambiguous.
     *
     * @param group group name
     * @param name  source name
     * @return {@code group:name}
     */
    private String getId(String group, String name) {
        return group + ID_SEPARATOR + name;
    }
}
