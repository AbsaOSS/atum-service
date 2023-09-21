# Atum

## Modules

### Agent `agent/`
This module is intended to replace the current [Atum](https://github.com/AbsaOSS/atum) repository. 
It provides libraries for establishing and pushing them to the API located in `server/`.
See `agent/README.md`.

### Server `server/`
An API under construction that communicates with the Agent and with the persistent storage. 
It also provides measure configuration to the `AtumAgent`. 
See `server/README.md`.

## How to generate Code coverage report
```sbt
sbt jacoco
```
Code coverage wil be generated on path:
```
{project-root}/atum-service/target/spark{spark_version}-jvm-{scala_version}/jacoco/report/html
{project-root}/atum-service-test/target/jvm-{scala_version}/jacoco/report/html
```

## How to Run in IntelliJ

To make this project runnable via IntelliJ, do the following:
- Make sure that your Spring related configuration in `server/src/main/resources/application.properties` 
  is configured according to your needs
- Create a new Spring Boot configuration (see the screenshot below)

![Intellij Spring Boot Run Configuration](https://github.com/AbsaOSS/atum-service/assets/5686168/63648615-03eb-45fb-99b3-f09e9aeebbb4")
Intellij Run Configuration for Spring Boot, Server configuration

## How to Release

Please see [this file](RELEASE.md) for more details.
