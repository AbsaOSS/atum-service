# Atum Web Service

## How to build and run

To create a jar file that can be executed:

```shell
> sbt clean "project server" assembly
> java -jar server/target/jvm-2.13/*.jar
```

If you want to quickly build and run from sbt you can run using the command below (alternatively you can execute
za.co.absa.atum.server.Main within your IDE). This deploys it to `localhost:8080`.

```shell
sbt "server/runMain za.co.absa.atum.server.Main"
```

### API v1 is deprecated

All `/api/v1/*` endpoints are **deprecated** (flagged in Swagger) and behave exactly as in v0.10.0. Only agents up to
v0.5.x use them; agents v0.6.0+ use API v2 (`POST /api/v2/partitionings`,
`POST /api/v2/partitionings/{partitioningId}/checkpoints`). API v1 will be removed in a later release.

### Version compatibility

**Always upgrade the server before the agents.** The server accepts what released agents send; a newer agent against an
older server is to be avoided.

**Server and database migrations are deployed together.** From v0.11.0 the lineage functions live in the `lineage`
schema and `runs.get_partitioning_ancestors` is dropped, so a v0.10.0 server binary does not work against a migrated
database. Rolling the server back means restoring the database too.

### Lineage and discovery endpoints

Partitionings are linked to their parents when they are created (`POST /api/v2/partitionings` with
`parentPartitioningIds`) or afterwards (`PATCH /api/v2/partitionings/{id}/ancestors`, leaf partitionings only). Each
link is stored both as flow membership (transitive) and as an explicit direct edge:

| Endpoint (`/api/v2/partitionings/...`)                              | Returns                                                                                                                      | Based on        |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|-----------------|
| `search?keys=&from=&to=&latest-first=`                              | Paginated distinct partitionings matching partial keys and/or checkpoint activity                                            | GIN + time idx  |
| `{id}/parents`, `{id}/children`                                     | Direct neighbours, with `linkedBy` / `linkedAt`                                                                              | edges           |
| `{id}/ancestors`, `{id}/descendants`                                | All transitive ancestors / descendants, paginated                                                                            | flow membership |
| `{id}/lineage?direction=both\|upstream\|downstream&depth=1..20`     | Sub-graph (nodes + edges); `truncated=true` if the 5,000-node/direction cap hit, `expandable*NodeIds` for boundary nodes     | edges           |

Edges exist only for links written from v0.11.0 on; for older links `parents` and `children` are empty and `lineage`
returns only the focal node, while `ancestors` and `descendants` still answer. The design is in
[ADR 01](../adrs/01-lineage-capability.md), the consumer view in
[ADR 02](../adrs/02-consumption-pattern-for-audits.md).

### REST API Reference

The REST API exposes a Swagger Documentation UI which documents all the HTTP endpoints exposed.
It can be found at **{REST_API_HOST}/docs/** (e.g. `http://localhost:8080/docs/`)

### Observability metrics

Optionally you can run server with monitoring that collects metrics about http communication, Hikari database
connection pool, and/or jvm/zio runtime. `intervalInSeconds` parameter refers to frequency of data collection from its
runtime environment. Monitoring of http communication and Hikari is not interval-based, therefore `intervalInSeconds`
parameter does not apply.

```
{
  monitoring {
    # monitoring of http communication
    http {
      enabled=true
    }
    # monitoring of database via Hikari
    hikari {
      enabled=true
    }
    # monitoring of jvm and zio
    jvm {
      enabled=true
      intervalInSeconds=5
    }
  }
}
```

When monitoring is enabled, the application exposes `http://localhost:8080/metrics`,
`http://localhost:8080/hikari-metrics`, and/or `http://localhost:8080/zio-metrics` endpoints which can be scraped by
Prometheus.
For testing purposes there is [docker-compose.yml](./docker-compose.yml) file which can be used to start up dockerized
Prometheus and Grafana instances. Prometheus scraping configs are defined in [prometheus.yml](./prometheus.yml) file.

# Build & run docker image

```shell
sbt clean assembly

docker build -t absaoss/atum-service:latest --progress=plain --no-cache \
--build-arg BUILD_PROXY=http://zproxycloud.intra.absaafrica:80 \
--build-arg SSL=false \
--build-arg LDAP_SSL_CERTS_PATH=./certs \
--build-arg SSL_DNAME="CN=*.my.domain.com, OU=project1, O=mycorp, L=Johannesburg, ST=Gauteng, C=za" ./

# If you want to run it against AWS, you need to add AWS related credentials into the docker container, possibly via 
# environment variables; but you can also run it against your a local Postgres DB (in host or another Docker container).
docker run -v /absolute/path/resource.conf:/opt/config/resource.conf \
  -e AWS_ACCESS_KEY_ID="abc" -e AWS_SECRET_ACCESS_KEY="def" -e AWS_SESSION_TOKEN="xyz" \
  -p 8080:8080 -p 8443:8443 absaoss/atum-service:latest

# reference.conf file has to be configured as below when running with ssl enabled
ssl {
    enabled=true
    keyStorePassword=changeit
    keyStorePath="/etc/ssl/certs/selfsigned.jks"
  }
```
