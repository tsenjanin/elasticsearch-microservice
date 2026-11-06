# elasticsearch-microservice

Spring Boot service that imports ECS-formatted application logs into Elasticsearch, so they can be explored in
Kibana. Logs are read from registered sources (HTML directory listings of `.log` / `.log.gz` files), organized in
groups and fetched one group at a time, each source into its own index. Logs are removed again after 5 days.

## Access links

| What | URL | Notes |
|---|---|---|
| Swagger UI | http://localhost:8085/swagger-ui.html | Compose stack; `localhost:8080` for a local run |
| OpenAPI spec | http://localhost:8085/v3/api-docs | JSON; `localhost:8080` for a local run |
| Kibana | http://localhost:5601 | Compose stack only |
| Elasticsearch | http://localhost:9200 | Only when running a standalone dev instance (see [Run locally](#run-locally)); not published by the compose stack |

## Endpoints

| Method | Path | Profile | What it does |
|---|---|---|---|
| `POST` | `/source?group=…&name=…&url=…&index=…` | all | Registers a source in a group (see below; `index` is optional) |
| `GET` | `/source` | all | Lists all groups with their sources |
| `DELETE` | `/source?group=…&name=…` | all | Removes one source of a group **and deletes its index with all its logs** |
| `DELETE` | `/source/group?group=…` | all | Removes a whole group: **every source and its index with all its logs** |
| `POST` | `/log/fetch?group=…&beginDate=yyyy-MM-dd&endDate=yyyy-MM-dd` | all | Imports logs from every source of the group for the date range |
| `DELETE` | `/log` | all | Deletes logs whose `@timestamp` is older than 5 days, in the index of every source of every group |
| `POST` | `/log?beginDate=yyyy-MM-dd&endDate=yyyy-MM-dd` | `local` only | Imports the sample files in `src/main/resources/logs` into `application-logs-local` (PoC) |

### Sources and groups

Every source belongs to a group. A group exists as long as it has sources: adding the first source with a new
`group` creates it, and deleting its last source (or the whole group) removes it. A fetch imports one group at a
time.

- `group`: `^[a-z0-9][a-z0-9-]{0,49}$`.
- `name`: unique within its group, same rule as `group`. The same name can exist in different groups.
- `url`: the full URL of the listing page, stored exactly as entered (`http` or `https`).
- `index` (optional): the index for the source's logs. Defaults to `application-logs-<group>-<name>`. It must match
  `^[a-z0-9][a-z0-9._-]{0,199}$`, must not match `logs-*-*` (that pattern is taken by a built-in Elasticsearch
  data-stream template), and must not already be used by another source. It may name an existing index, for
  example the old `application-logs`, whose logs are then kept. Deleting the source deletes that index too.

`GET /source` returns the groups and their sources, both sorted by name:

```json
[
  { "group": "prod", "sources": [
      { "name": "billing", "url": "https://other.example.com/files/index.html", "index": "billing-logs" },
      { "name": "public-api", "url": "https://logs.example.com/public-api/logs/",
        "index": "application-logs-prod-public-api" } ] },
  { "group": "staging", "sources": [
      { "name": "public-api", "url": "https://staging.example.com/logs/",
        "index": "application-logs-staging-public-api" } ] }
]
```

Sources are stored in the `log-source` index, so they survive restarts (as long as the `es-data` volume exists).
Errors: `400` for a missing or invalid group, name, URL or index; `409` for a name already in the group or an
index already used by another source; `404` for an unknown source or group.

**Upgrading from a version without groups:** the old `log-source` index has no `group` field. Delete it once in
Kibana Dev Tools (`DELETE log-source`) and register the sources again. The log indices are not touched.

### Imports

`group` and both dates are required; the dates are inclusive. `POST /log/fetch` imports only the sources of that
group (`404` for an unknown group) and returns one entry per source, sorted by name:

```json
[
  { "source": "public-api", "index": "application-logs-prod-public-api",
    "processed": 23, "inserted": 17, "duplicates": 2, "rejected": 4, "error": null },
  { "source": "billing", "index": "billing-logs",
    "processed": 0, "inserted": 0, "duplicates": 0, "rejected": 0,
    "error": "I/O error on GET request for \"https://logs.example.com/billing/\": ..." }
]
```

A failing source does not stop the others: its entry has an `error` and zero counts. Batches stored before the
failure stay stored and count as `duplicates` on a retry. The local PoC import returns a single entry without
`source`, `index` and `error`.

`processed = inserted + duplicates + rejected`. Every line gets an id derived from its content, so re-importing the
same range never stores a line twice in the same index; already stored lines are counted as `duplicates`. The same
line coming from two sources is stored in both indices. Lines that are not valid ECS JSON (missing `@timestamp` /
`message`, or truncated) are `rejected`.

### Remote log server format

- The listing is an HTML page (e.g. an Apache directory index); every `href` is resolved against the source's `url`,
  the same way a browser would. Links outside the directory of that URL are ignored.
- `whatever.log`: the active file, plain text, always counted as **today** (in the app's time zone).
- `whatever-yyyy-MM-dd.N.log.gz`: rolled files, gzip archives of `whatever-yyyy-MM-dd.N.log`. Several per day.
- Files are selected by the date in their name; files outside the range are never downloaded.
- Requests use HTTP Basic auth (same as `curl -u username:password`). All sources share one username/password, so
  they are sent to every registered source. Only register hosts you trust with them.

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `remote-log.username` | `REMOTE_LOG_USERNAME` | empty (no auth sent) |
| `remote-log.password` | `REMOTE_LOG_PASSWORD` | empty |
| `spring.elasticsearch.uris` | `SPRING_ELASTICSEARCH_URIS` | `http://localhost:9200` |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | none, so the default profile `local` applies |

Where to put the values:

- **Compose stack:** `.env` in the project root (`cp .env.example .env`, then edit).
- **Local run:** `application-local.properties` in the project root, loaded with the `local` profile.

Both files hold credentials and are git-ignored; neither ends up in the container image.

## Run with podman compose

Runs Elasticsearch, Kibana and the app. The first `up` builds the app image, which needs internet access for the
base images and Maven dependencies.

```bash
cp .env.example .env              # once, then fill in REMOTE_LOG_USERNAME / REMOTE_LOG_PASSWORD
podman compose up -d              # start (builds the app image if missing)
podman compose up -d --build app  # rebuild and restart the app after code changes
podman compose ps                 # status; elasticsearch should be "healthy"
```

If `podman compose` fails with `failed to connect to the docker API at unix:///run/user/1000/podman/podman.sock`,
start the Podman socket once:

```bash
systemctl --user enable --now podman.socket
```

or install `podman-compose` (`sudo dnf install podman-compose`).

### Network layout

| Service | Networks | Internet | From the host |
|---|---|---|---|
| elasticsearch | `backend` (internal) | none | not reachable, use Kibana Dev Tools |
| kibana | `backend`, `frontend` | telemetry and newsfeed off | `127.0.0.1:5601` |
| kibana-setup | `backend` (internal) | none | not reachable; exits after configuring Kibana |
| app | `backend`, `egress` | yes, for the remote log server | `127.0.0.1:8085` |

In the container the app runs with the `container` profile, so the local PoC import (`POST /log`) is disabled.

### Logs

```bash
podman logs -f elasticsearch-microservice          # app, follow
podman logs --tail 100 elasticsearch-microservice  # app, last 100 lines
podman compose logs -f                             # all services
podman logs -f elasticsearch                       # or: kibana
```

### Stop and clean up

```bash
podman compose stop               # stop, keep containers
podman compose down               # remove containers and networks, keep stored logs
podman compose down -v            # also delete the es-data volume (ALL stored logs)
podman compose down -v --rmi all  # also delete the images
podman compose rm -sf app         # remove only the app container
```

### Autostart on boot

`elasticsearch`, `kibana` and `app` use `restart: unless-stopped`. At boot, Podman's `podman-restart.service` starts
every container with that policy, and lingering lets the user service run without logging in. One-time setup:

```bash
systemctl --user enable podman-restart.service
loginctl enable-linger "$USER"
```

- Run `podman compose up -d` once; only existing containers are started at boot.
- A container stopped with `podman compose stop` stays stopped after a reboot, and `down` removes the containers, so
  nothing starts until the next `up`.
- At boot all containers start at once, without the `depends_on` order: Kibana and the app wait for Elasticsearch on
  their own. `kibana-setup` does not run at boot; its settings are already stored in Elasticsearch.
- To turn it off: `systemctl --user disable podman-restart.service` (and `loginctl disable-linger "$USER"` if
  nothing else needs it).

## Run locally

The compose stack does not publish Elasticsearch to the host. To develop against the app on the host, start a
throwaway Elasticsearch first:

```bash
podman run --rm -d --name es-dev -p 127.0.0.1:9200:9200 \
        -e discovery.type=single-node -e xpack.security.enabled=false \
        docker.elastic.co/elasticsearch/elasticsearch:9.4.8
curl localhost:9200/_cluster/health                     # wait until status is green or yellow
```

Then:

```bash
mvn spring-boot:run                                     # runs with the "local" profile
podman stop es-dev                                      # when done (--rm removes it)
```

Run it from the project root so `application-local.properties` is picked up.

## Build and test

```bash
mvn compile                                             # build
mvn test                                                # tests
mvn clean install -DskipTests                           # clean build of the jar
podman build -f Containerfile -t localhost/elasticsearch-microservice:latest .   # image only
```

## Kibana

The compose stack trims Kibana down to log access. The one-shot `kibana-setup` service
([kibana/kibana-setup.mjs](kibana/kibana-setup.mjs)) runs after every `up`. It hides every feature of the default
space except Discover, saved Discover queries, Data View Management, Dev Tools and Advanced Settings, and makes
Discover the start page. To keep more, add feature ids to `KIBANA_KEEP_FEATURES` in `docker-compose.yml` (e.g.
`dashboard_v2`), then
run `podman compose up -d kibana-setup`. Its log (`podman logs kibana-setup`) lists the kept and hidden feature ids.

Dates are shown as ISO 8601 without offset for everyone, e.g. `2026-11-05T14:30:26.901`, always in `Europe/Zagreb`
(the log servers' zone, daylight saving included), whatever the viewer's browser zone is. Kibana stores
`@timestamp` as an absolute instant, so it cannot show the offset a line was written with; the display zone is what
makes the times match the servers. Set by `KIBANA_DATE_FORMAT`, `KIBANA_DATE_NANOS_FORMAT` (moment.js syntax) and
`KIBANA_TIME_ZONE` (an IANA zone, or `Browser`) in the `kibana-setup` service. They are applied on every `up`, so a
change made in Advanced Settings is reset; change them in compose instead.

Advanced Settings is kept for the theme: ☰ → **Stack Management** → **Advanced Settings** → **Appearance** →
**Dark mode** (dark, light or follow the system), then reload the page. Kibana has no theme switch in its header, and
the per-user one (the user profile) needs security, which is off. The setting applies to everyone using this
Kibana.

Playground, Agent Builder and the Security section (Attack discovery, Launchpad) come from plugins whose menu entries
are not controlled by features, so they are switched off with flags in the `kibana` service's `command` instead
(`--xpack.searchPlayground.enabled=false`, `--xpack.agentBuilder.enabled=false`,
`--xpack.securitySolution.enabled=false`).
Changing them needs `podman compose up -d kibana`.

Every source has its own index, so create one data view per source you want to look at:

1. Open http://localhost:5601, then ☰ → **Stack Management** → **Data Views** → **Create data view**.
2. Use the source's index as the name and index pattern (e.g. `application-logs-prod-public-api`, or a custom
   index such as `billing-logs`), set the timestamp field to `@timestamp`, then **Save**. With default index names,
   `application-logs-prod-*` covers a whole group and `application-logs-*` every source.
3. ☰ → **Discover**, pick the data view from the top left (that's how you switch between sources), and set the
   time range (top right) to cover the imported days. The default of 15 minutes is usually empty.

If Discover only shows **Add integrations**, there is no data yet: run an import first.

Useful queries in ☰ → **Dev Tools**:

```
GET _cat/indices/application-logs-*?v   # one index per source (custom index names are listed separately)
GET log-source/_search                  # registered sources
GET application-logs-prod-public-api/_count
GET application-logs-prod-public-api/_search?size=5
GET application-logs-prod-public-api/_mapping
DELETE application-logs-prod-public-api # wipe one source's logs; the index is recreated on the next import
```

Filters in the Discover search bar (KQL): `log.level : "ERROR"`, `message : "payment"`,
`service.name : "public-api"`.

The log index mapping lives in `src/main/resources/elasticsearch/log-index-mapping.json`. It only applies when an
index is created, so delete the index after changing it.

## Maintainer

Tomislav Senjanin - ([TODO](mailto:todo)) - Lead Maintainer & Creator
