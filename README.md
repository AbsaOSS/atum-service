# Atum Service

- [Atum Service](#atum-service)
    - [Modules](#modules)
        - [Agent `agent/`](#agent-agent)
        - [Server `server/`](#server-server)
        - [Data Model `model/`](#data-model-model)
        - [Database `database/`](#database-database)
    - [Vocabulary](#vocabulary)
        - [Atum Agent](#atum-agent)
        - [Partitioning](#partitioning)
        - [Atum Context](#atum-context)
        - [Measure](#measure)
        - [Measurement](#measurement)
        - [Checkpoint](#checkpoint)
        - [Data Flow](#data-flow)
    - [How to generate Code coverage report](#how-to-generate-code-coverage-report)
    - [How to Run in IntelliJ](#how-to-run-in-intellij)
    - [How to Run Tests](#how-to-run-tests)
        - [Test controls](#test-controls)
        - [Run Unit Tests](#run-unit-tests)
        - [Run Integration Tests](#run-integration-tests)
    - [How to Release](#how-to-release)

Atum Service is a data completeness and accuracy application meant to be used for data processed by Apache Spark.

One of the challenges regulated industries face is the requirement to track and prove that their systems preserve
the accuracy and completeness of data. In an attempt to solve this data processing problem in Apache Spark applications,
we propose the approach implemented in this application.

The purpose of Atum Service is to add the ability to specify "checkpoints" in Spark applications. These checkpoints 
are used to designate when and what metrics are calculated to ensure that critical input values have not been modified 
as well as allow for quick and efficient representation of the completeness of a dataset. This application does not 
implement any checks or validations against these control measures, i.e. it does not act on them - Atum Service is, 
rather, solely focused on capturing them.

The application provides a concise and dynamic way to track completeness and accuracy of data produced from source 
through a pipeline of Spark applications. All metrics are calculated at a DataFrame level using various aggregation 
functions and are stored on a single central place, in a relational database. Comparing control metrics for various 
checkpoints is not only helpful for complying with strict regulatory frameworks, but also helps during development 
and debugging of your Spark-based data processing.


## Modules

### Agent `agent/`
This module is intended to replace the current [Atum](https://github.com/AbsaOSS/atum) repository. 
It provides functionality for computing and pushing control metrics to the API located in `server/`.

For more information, see the [Vocabulary section](#Vocabulary) or `agent/README.md` for more technical documentation.

#### Spark 2.4 support
Because there are some java level incompatibilities between Spark 2.4 and Spark 3.x whe build on Java 11+, we have to 
drop support for Spark 2.4. If you need the agent to work with Spark 2.4 follow these steps:
* Switch to Java 8
* In `'build.sbt'` change the matrix rows, to be Spark 2.4 and Scala 2.11 for modules _agent_ and _model_
* Build these two modules and use them in your project

### Server `server/`
An API under construction that communicates with the Agent and with the persistent storage. It also provides measure 
configuration to the agent.

The server accepts metrics potentially from several agents and saves them into database. In the future, it will be also 
able to send the metrics definitions back if requested. 

Important note: the server never receives any real data - it only works with the metadata and metrics defined 
by the agent! 

See `server/README.md` for more technical documentation.

### Data Model `model/`

This module defines a set of Data Transfer Objects. These are Atum-specific objects that carry data that are being 
passed from agent to server and vice versa.

### Database `database/`

This module contains a set of scripts that are used to create and maintain the database models. It also contains 
integration tests that are used to verify the logic of our database functions.
The database tests are integration tests in nature. Therefore, a few conditions applies:
* The tests are excluded from task `test` and are run only by a dedicated `dbTest` task (`sbt dbTest`).
* The database structures must exist on the target database 
  (follow the [deployment instructions in database module](database/README.md#Deployment)).
* The connection information to the DB is provided in file `database/src/test/resources/database.properties` 
  (See `database.properties.template` for syntax).

## Vocabulary

This section defines a vocabulary of words and phrases used across the codebase or this documentation.

### Atum Agent

Basically, the agent is supposed to be embedded into your application and its responsibility is to measure the
given metrics and send the results to the server. It acts as an entity responsible for spawning the `Atum Context` 
and communicating with the server.

A user of the Atum Agent must provide certain `Partitioning` with a set of `Measures` he or she wants to calculate, 
and execute the `Checkpoint` operation. A server details are also needed to be configured.

### Partitioning

`Atum Partitioning` uniquely defines a particular dataset (or a subset of a dataset, using Sub-Partitions) that we
want to apply particular metrics on. It's similar to data partitioning in HDFS or Data Lake.
The order of individual `Partitions` in a given `Partitioning` matters. It's a map-like structure in which the order
of keys (partition names) matters.

It's possible to define an additional metadata along with `Partitioning` - as a map-like structure with which 
you can store various attributes associated with a given `Partitioning`, that you can potentially 
use later in your application. Just to give you some ideas for these: 
* a name of your application, ETL Pipeline, or your Spark job
* a list of owners of your application or your dataset
* source system of a given dataset
* and more

### Atum Context
 
This is a main entity responsible for actually performing calculations on a Spark DataFrame. Each `Atum Context` is 
related to particular `Partitioning` - or to put in other words, each `Atum Context` contains all `Measures` 
for a specific data, defined by a given `Partitioning`, that are supposed to be calculated.

### Measure

A `Measure` defines what and how a single metric should be calculated. So it's a type of control metric to compute, 
such as count, sum, or hash, that also defines a list of columns (if applicable) that should be used when actually 
executing the calculation against a given Spark DataFrame.

Some `Measures` define no columns (such as `count`), some require exactly one column (such as `sum` of values for 
particular column), and some require more columns (such as `hash` function).

### Measurement 

Practically speaking, a single `Measurement` contains a `Measure` and result associated with it. 

### Checkpoint

Each `Checkpoint` defines a sequence of `Measurements` (containing individual `Measures` and their results) that are 
associated with certain `Partitioning`.

A `Checkpoint` is defined on the agent side, the server only accepts it.

`Atum Context` stores information about a set of `Measures` associated with specific `Partitioning`, but the
calculations of individual metrics are performed only after the `Checkpoint` operation is being called.
We can even say, that `Checkpoint` is a result of particular `Measurements` (verb).

### Data Flow

The journey of a dataset throughout various data transformations and pipelines. It captures the whole journey,
even if it involves multiple applications or ETL pipelines.


## How to generate Code coverage report
```sbt
sbt jacoco
```

Code coverage wil be generated on path:
```
{project-root}/{module}/target/jvm-{scala_version}/jacoco/report/html
```

## How to Run in IntelliJ

To make this project runnable via IntelliJ, do the following:
- Make sure that your configuration in `server/src/main/resources/reference.conf` 
  is configured according to your needs

## How to Run Tests

### Test controls

See the commands configured in the `.sbtrc` [(link)](https://www.scala-sbt.org/1.x/docs/Best-Practices.html#.sbtrc) file to provide different testing profiles.

### Run Unit Tests
Use the `test` command to execute all unit tests, skipping all other types of tests. 
```
sbt test
```

### Run Integration Tests
Use the `testIT` command to execute all Integration tests, skipping all other test types.
```
sbt testIT
```

Use the `testDB` command to execute all Integration tests in `database` module, skipping all other tests and modules.
- Hint: project custom command
```
sbt testDB
```


## How to Release

Please see [this file](RELEASE.md) for more details.
