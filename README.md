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


## How to Release

Please see [this file](RELEASE.md) for more details.