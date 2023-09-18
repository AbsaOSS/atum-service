# Atum Service

Atum Service is a data completeness and accuracy application meant to be used for data processed by Apache Spark.

One of the challenges regulated industries face is the requirement to track and prove that their systems preserve
the accuracy and completeness of data. In an attempt to solve this data processing problem in Apache Spark applications,
we propose the approach implemented in this application.

The purpose of Atum Service is to add the ability to specify "checkpoints" in Spark applications. These checkpoints 
are used to designate when and what metrics are calculated to ensure that critical input values have not been modified 
as well as allow for quick and efficient representation of the completeness of a dataset. Additional metrics can also 
be defined at any checkpoint. This application does not implement any checks or validations against these control
measures, i.e. it does not act on them - it is, rather, solely focused on capturing them.

The library provides a concise and dynamic way to track completeness and accuracy of data produced from source through
a pipeline of Spark applications. All metrics are calculated at a DataFrame level using various aggregation functions
and are stored on a single central place in relational database. Comparing control metrics for various checkpoints 
is not only helpful for complying with strict regulatory frameworks, but also helps during development and debugging
of your Spark-based data processing.


## Modules

### Agent `agent/`
This module is intended to replace the current [Atum](https://github.com/AbsaOSS/atum) repository. 
It provides functionality for establishing and pushing control metrics to the API located in `server/`.

For more information, see the [Vocabulary section](#Vocabulary) or `agent/README.md` for more technical documentation.

### Server `server/`
An API under construction that communicates with the Agent and with the persisting storage. It also provides measure 
configuration to the agent.

The server accepts metrics potentially from several agents and saves them into database. It will be also able to send
the metrics back if requested. 

Important note: server never receives any real data - it only works with the metadata and metrics defined by the agent! 

See `server/README.md` for more technical documentation.

### Data Model `model/`

This module defines a set of Data Transfer Objects which are Atum-specific objects that carry data that are being 
passed from agent to server and vice versa.

## Vocabulary

### Atum Agent

Basically, the agent is supposed to be embedded into your application and its responsibility is to measure the
given metrics and send the results to the server. It acts as an entity responsible for spawning the Atum Context 
and communicating with the server.

User of the Atum Agent must provide Partitioning with Measurements, and execute Checkpoint calculation.

### Atum Context
 
This is a main entity responsible for actually performing calculations on a Spark DataFrame. Each Atum Context is 
related to particular Partitioning - or to put it in other words, Atum Context contains all measurements for a specific 
partition that are supposed to be done against a given DataFrame

### Checkpoint

Each checkpoint defines a set of Measurements that are associated with certain Partitioning.
A Checkpoint is defined on the agent, and the server only accepts it.

Atum Context stores information about the set of Measures associated with specific Partitions, but the calculations
of required metrics are performed when the Checkpoint operation is being called. 

### Data Flow

The journey of a dataset throughout data transformations and pipelines. It captures the whole journey even if it 
involves multiple applications or ETL pipelines. 

### Partitioning

Atum Partitioning uniquely defines a particular dataset (or a subset of a dataset, using Sub-Partitions) that we 
want to apply particular metrics on. It's similar to data partitioning in HDFS or Data Lake.
The order of individual Partitions in a given Partitioning matters.

Each partitioning also may define additional metadata - as a map-like structure with which you can store various 
attributes associated with a given partitioning, that you can potentially use later in the application. 
Some ideas for these can be a name of your application, ETL Pipeline, or your Spark job, owners of it, just to 
name a few.

### Measure

A Measure defines what and how a single metric should be calculated. So it's a type of control metric to compute, 
such as count, sum, or hash, that also defines a list of columns (if applicable) that should be used when actually 
executing the calculation against a given Spark DataFrame.

Some Measures define no columns (such as `count`), some require one column (such as `sum` of values for particular 
column), and some require more columns (such as `hash`)

### Measurement 

Practically speaking, it's a map-like structure of Measures and their results. 

Now, to connect it all together, each Checkpoint is a result of particular Measurement (containing individual Measures
and their results), defined on certain Partitioning.

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
