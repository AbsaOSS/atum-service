# Atum Web Service

## How to build and run

To create a war file that can be deployed to tomcat just run:

```shell
> sbt package
```

If you want to quickly build and run from sbt you can run using the command below. This deploys it to `localhost:8080`.
This is possible thanks to [xsbt-web-plugin](https://github.com/earldouglas/xsbt-web-plugin)

```shell
> sbt
sbt:Atum Service> tomcat:start
sbt:Atum Service> tomcat:stop
```

### REST API Reference 

The REST API exposes a Swagger Documentation UI which documents all the HTTP endpoints exposed.
It can be found at **{REST_API_HOST}/swagger-ui/** (e.g. `http://localhost:8080/swagger-ui/`)
