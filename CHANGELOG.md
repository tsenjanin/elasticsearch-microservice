# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

A push to `main` releases when `pom.xml` is at a new `X.Y.Z` and the newest entry below is `## [X.Y.Z] - YYYY-MM-DD`
(see "Releasing" in the README).

## [0.1.1] - 12.11.2026.

### Fixed
- The app container no longer exits on startup (so Swagger UI and the API are reachable again). Logback tried to
  write `src/main/resources/logs` under the read-only `/app`; the example `logback.xml` moved to
  `src/main/resources/examples/` and is no longer loaded. The app logs to the console, so `podman logs` shows them.

## [0.1.0] - 12.11.2026.

First release, planned as `0.1.0`.

### Added
- Sources organized in groups, stored in the `log-source` index (`POST /source`, `GET /source`, `DELETE /source`,
  `DELETE /source/group`).
- `POST /log/fetch?group=…` imports ECS logs from every source of a group into each source's own index, with
  content-based deduplication.
- `DELETE /log` removes logs older than 5 days from every source index.
- Podman compose stack with Elasticsearch, Kibana (trimmed to Discover, Data Views and Dev Tools) and the app.
- Released container images on `ghcr.io/tsenjanin/elasticsearch-microservice`, pinned on servers with `APP_VERSION`.

### Changed
- **Breaking:** `POST /log/fetch` no longer accepts `beginDate` and `endDate`; it always imports today's files (the
  active `.log` and today's rolled archives).
- Each file download from a source waits 500 ms first, so requests to the log server are spaced out.
- `docker-compose.yml` pulls the released image instead of building it; builds from source use
  `docker-compose.build.yml`.

### Removed
- **Breaking:** the local PoC import `POST /log` and the sample log files in `src/main/resources/logs`.
