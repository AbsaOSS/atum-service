# Atum Service

[![Build](https://github.com/AbsaOSS/atum-service/actions/workflows/build.yml/badge.svg)](https://github.com/AbsaOSS/atum-service/actions/workflows/build.yml)
[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity)
![Java 11](https://img.shields.io/badge/Java_11-ED8B00?style=flat&logo=openjdk&logoColor=black)

| Atum Server                                                                                                                                                                                                         | Atum Agent                                                                                                                                                                                                        | Atum Model | Atum Reader                                                                                                                                                                                                  |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [![GitHub release](https://img.shields.io/github/release/AbsaOSS/atum-service.svg)](https://GitHub.com/AbsaOSS/atum-service/releases/) | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/za.co.absa.atum-service/atum-agent-spark3_2.13/badge.svg)](https://central.sonatype.com/search?q=atum-agent&namespace=za.co.absa.atum-service) | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/za.co.absa.atum-service/atum-model_2.13/badge.svg)](https://central.sonatype.com/search?q=atum-model&namespace=za.co.absa.atum-service) | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/za.co.absa.atum-service/atum-reader_2.13/badge.svg)](https://central.sonatype.com/search?q=atum-reader&namespace=za.co.absa.atum-service) |                                                                             




- [Atum Service](#atum-service)
    - [Motivation](#motivation)
    - [Features](#features)
    - [Modules](#modules)
        - [Agent `agent/`](#agent-agent)
        - [Reader `reader/`](#agent-agent)
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
    - [Usage](#usage)
        - [Atum Agent routines](#atum-agent-routines)
        - [Control measurement types](#control-measurement-types)
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

## Motivation

Big Data strategy for a company usually includes data gathering and ingestion processes.
That is the definition of how data from different systems operating inside a company
are gathered and stored for further analysis and reporting. An ingestion processes can involve
various transformations like:
* Converting between data formats (XML, CSV, etc.)
* Data type casting, for example converting XML strings to numeric values
* Joining reference tables. For example this can include enriching existing
  data with additional information available through dictionary mappings.
  This constitutes a common ETL (Extract, Transform and Load) process.

During such transformations, sometimes data can get corrupted (e.g. during casting), records can
get added or lost. For instance, *outer joining* a table holding duplicate keys can result in records explosion.
And *inner joining* a table which has no matching keys for some records will result in loss of records.

In regulated industries it is crucial to ensure data integrity and accuracy. For instance, in the banking industry
the BCBS set of regulations requires analysis and reporting to be based on data accuracy and integrity principles.
Thus it is critical at the ingestion stage to preserve the accuracy and integrity of the data gathered from a
source system.

The purpose of Atum is to provide means of ensuring no critical fields have been modified during the processing and no 
records are added or lost. To do this the library provides an ability to calculate *control numbers* of explicitly 
specified columns using a selection of agregate function. We call the set of such measurements at a given time
a *checkpoint* and each value - a result of the function computation - we call a *control measurement*. Checkpoints can 
be calculated anytime between Spark transformations and actions, so as at the start of the process or after its end.

We assume the data for ETL are processed in a series of batch jobs. Let's call each data set for a given batch
job a *batch*. All checkpoints are calculated for a specific batch.

## Features

TBD

## Modules

### Agent `agent/`
This module is intended to replace the current [Atum](https://github.com/AbsaOSS/atum) repository. 
It provides functionality for computing and pushing control metrics to the API located in `server/`.

For more information, see the [Vocabulary section](#Vocabulary) or `agent/README.md` for more technical documentation.

#### Spark 2.4 support
Because there are some java level incompatibilities between Spark 2.4 and Spark 3.x when build on Java 11+, we have to 
drop support for Spark 2.4. If you need the agent to work with Spark 2.4 follow these steps:
* Switch to Java 8
* In `'build.sbt'` change the matrix rows, to be Spark 2.4 and Scala 2.11 for modules _agent_ and _model_
* Build these two modules and use them in your project

### Reader `reader/`
**NB!**  
_This module is not yet implemented to an operational abilities and therefore not yet released._

This module is intended to be used whenever an application wants to read the metrics stored by the _Atum Service_. It
offers classes and methods to read the metrics from the database shielding away the complexity of accessing the _Atum Server_
directly.

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

## Usage

### Atum Agent routines

TBD

### Control measurement types

The control measurement of one or more columns is an aggregation function result executed over the dataset. It can be 
calculated differently depending on the column's data type, on business requirements and function used. This table 
represents all currently supported measurement types (aka measures):

| Type                               | Description                                                   |
|------------------------------------|:--------------------------------------------------------------|
| AtumMeasure.RecordCount            | Calculates the number of rows in the dataset                  |
| AtumMeasure.DistinctRecordCount    | Calculates DISTINCT(COUNT(()) of the specified column         |
| AtumMeasure.SumOfValuesOfColumn    | Calculates SUM() of the specified column                      |
| AtumMeasure.AbsSumOfValuesOfColumn | Calculates SUM(ABS()) of the specified column                 |
| AtumMeasure.SumOfHashesOfColumn    | Calculates SUM(CRC32()) of the specified column               |
| Measure.UnknownMeasure             | Custom measure where the data are provided by the application |

[//]: # (| controlType.aggregatedTruncTotal    | Calculates SUM&#40;TRUNC&#40;&#41;&#41; of the specified column       |)

[//]: # (| controlType.absAggregatedTruncTotal | Calculates SUM&#40;TRUNC&#40;ABS&#40;&#41;&#41;&#41; of the specified column  |)


## How to generate Code coverage report

- Use java version 11.

```sbt
sbt jacoco
```

The HTML and XML reports of coverage will be generated on the path:

```
{project-root}/{module}/target/**/jacoco/report/index.html
{project-root}/{module}/target/**/jacoco/report/jacoco.xml
```

> `**` - depends on the module setup

## How to Run in IntelliJ

To make this project runnable via IntelliJ, do the following:
- Make sure that your configuration in `server/src/main/resources/reference.conf` 
  is configured according to your needs
- When building within an IDE sure to have the option `-language:higherKinds` on in the compiler options, as it's often not picked up from the SBT project settings.

## How to Run Tests

### Test controls

See the commands configured in the `.sbtrc` [(link)](https://www.scala-sbt.org/1.x/docs/Best-Practices.html#.sbtrc) file to provide different testing profiles.

### Run Unit Tests
Use the `test` command to execute all unit tests, skipping all other types of tests. 
```sbt
sbt test
```

### Run Integration Tests
Use the `testIT` command to execute all Integration tests, skipping all other test types.
```sbt
sbt testIT
```

### Run All Standard Tests

Use the `testAllStandard` command to execute all unit and integration tests except for the special ones mentioned below.
These still won't require any real DB or service to be present.
```sbt
sbt testAllStandard
```

### Run Special Tests 

These usually have dependency on some system or service being present on the machine where the tests are being executed.
These also can be performance or penetration tests. Basically the point is that these tests require special
setup and are usually slower to execute.

Use the `testDB` command to execute all Integration tests in `database` module, skipping all other tests and modules.
- Hint: project custom command, requiring a real DB to be present and configured
```sbt
sbt testDB
```

If you want to run all Agent <-> Server compatibility tests, use the following command.
- Hint: project custom command, requiring a real DB and service to be present and configured on the system
```sbt
sbt testCompatibility
```

## How to Release

Please see [this file](RELEASE.md) for more details.
