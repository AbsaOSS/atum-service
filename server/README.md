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
older server is not supported (it silently loses the parents). Applying the server requires the new database migrations,
see [database/README.md](../database/README.md).

| Client | Against this server |
|--------|---------------------|
| Agent up to v0.5.x | API v1 (deprecated), unchanged |
| Agent v0.6 - v0.10 | works, but a newly created child **no longer inherits** the parent's measure definitions (now opt-in); a `409` of `PATCH .../ancestors` (cycle, or the child already has descendants) is ignored by these agents |
| Agent of this release | full feature set, measure-definition copy is opt-in |
| Agent of this release | an **older** server: the parents are lost |

### Linking behaviour (API v2)

- `POST /api/v2/partitionings` is an idempotent create-or-link for 0..N parents: `201` created, `200` already existing,
  `404` unknown parent, `409` cycle, or an existing partitioning that already has descendants would get a new parent.
- `copyMeasureDefinitions` (default `false`) copies the parents' measure definitions to a newly created partitioning only.
  Additional data is never copied by `POST`.
- `PATCH /api/v2/partitionings/{partitioningId}/ancestors` links an existing partitioning to a parent (same `409` rules).

Details of the linking rules, known limitations and repair recipes are in [database/README.md](../database/README.md);
the agent side is described in [agent/README.md](../agent/README.md).

### REST API Reference

The REST API exposes a Swagger Documentation UI which documents all the HTTP endpoints exposed.
It can be found at **{REST_API_HOST}/docs/** (e.g. `http://localhost:8080/docs/`)

### Observability metrics

Optionally you can run server with monitoring that collects metrics about http communication and/or jvm/zio
runtime. `intervalInSeconds` parameter refers to frequency of data collection from its runtime environment.
Monitoring of http communication is based on intercepting of http calls therefore `intervalInSeconds` parameter does not
apply.

```
{
  monitoring {
    # monitoring of http communication
    http {
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

When monitoring enabled, the application exposes `http://localhost:8080/metrics`
and/or `http://localhost:8080/zio-metrics` endpoints which can be scraped by Prometheus.
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
