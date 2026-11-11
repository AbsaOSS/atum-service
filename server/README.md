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

A `POST /api/v2/partitionings` body in the v0.6 - v0.10 shape (`parentPartitioningId`, no `copyMeasureDefinitions`)
still copies the parent's measure definitions to a newly created child, as before; otherwise the flag defaults to
`false`.

When linking parent partitionings (via `POST /api/v2/partitionings` or `PATCH /api/v2/partitionings/{id}/ancestors`),
the server enforces that only leaf partitionings (those with no existing descendants) can be linked under new parents,
and rejects cyclic dependencies with an `HTTP 409 Conflict`.

### Lineage & Provenance Endpoints (API v2)

The server provides endpoints for inspecting and navigating dataset lineage:

- `GET /api/v2/partitionings/{partitioningId}/parents`: Returns the direct (1-hop) parents of a partitioning.
- `GET /api/v2/partitionings/{partitioningId}/children`: Returns the direct (1-hop) children of a partitioning (downstream impact analysis).
- `GET /api/v2/partitionings/{partitioningId}/lineage`: Returns the complete lineage sub-graph (`nodes` + `edges`) centered on a partitioning (`?direction=both|upstream|downstream&depth=1..20`).
- `GET /api/v2/partitionings/{partitioningId}/ancestors`: Returns all transitive ancestors of a partitioning.
- `GET /api/v2/partitionings/{partitioningId}/descendants`: Returns all transitive descendants of a partitioning (members of its
  main flow, excluding itself); a convenience for `main-flow` + `GET /api/v2/flows/{flowId}/partitionings`.
- `GET /api/v2/partitionings/{partitioningId}/main-flow`: Returns the main flow associated with the partitioning.
- `GET /api/v2/flows/{flowId}/partitionings`: Returns all partitionings belonging to a flow, i.e. its primary
  partitioning plus all of its transitive descendants, almost like a complement to:
  `GET /api/v2/partitionings/{partitioningId}/ancestors`.

### Version compatibility

**Always upgrade the server before the agents.** The server accepts what released agents send; a newer agent against an
older server is to be avoided.

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
