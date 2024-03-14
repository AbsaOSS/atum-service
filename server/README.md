# Atum Web Service

## How to build and run

To create a jar file that can be executed:

```shell
> sbt clean "project server" assembly
> java -jar server/target/jvm-2.13/*.jar
```

If you want to quickly build and run from sbt you can run using the command below (alternatively you can execute za.co.absa.atum.server.Main within your IDE). This deploys it to `localhost:8080`.

```shell
sbt "server/runMain za.co.absa.atum.server.Main"
```

### REST API Reference 

The REST API exposes a Swagger Documentation UI which documents all the HTTP endpoints exposed.
It can be found at **{REST_API_HOST}/docs/** (e.g. `http://localhost:8080/docs/`)

### Observability metrics

Optionally you can run server with monitoring that collects metrics about http communication and/or jvm/zio runtime. `intervalInSeconds` parameter refers to frequency of data collection from its runtime environment.
Monitoring of http communication is based on intercepting of http calls therefore `intervalInSeconds` parameter does not apply.

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

When monitoring enabled, the application exposes `http://localhost:8080/metrics` and/or `http://localhost:8080/zio-metrics` endpoints which can be scraped by Prometheus.
For testing purposes there is [docker-compose.yml](./docker-compose.yml) file which can be used to start up dockerized Prometheus and Grafana instances. Prometheus scraping configs are defined in [prometheus.yml](./prometheus.yml) file. 
