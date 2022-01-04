# Atum Web Service

## How to build and run

To create a war file that can be deployed to tomcat just run:

```shell
> sbt package
```

If you want to quick build and run from sbt you can run. This deploys it to `localhost:8080`.
This is possible thank to [xsbt-web-plugin](https://github.com/earldouglas/xsbt-web-plugin)

```shell
> sbt
sbt:Atum Service> tomca:start
sbt:Atum Service> tomca:stop
```
