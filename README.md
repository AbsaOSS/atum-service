# Atum Service

Atum Service is a data completeness and accuracy application meant to be used for data being processed by Apache Spark.

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
{project-root}/atum-service/target/spark{spark_version}-jvm-{scala_version}/jacoco/report/html
{project-root}/atum-service-test/target/jvm-{scala_version}/jacoco/report/html
```

## How to Run in IntelliJ

To make this project runnable via IntelliJ, do the following:
- Make sure that your configuration in `server/src/main/resources/reference.conf` 
  is configured according to your needs


## How to Release

Please see [this file](RELEASE.md) for more details.
