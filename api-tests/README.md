# API tests

- [Pre-requisites](#prerequisites)
- [Setup](#setup)
- [Backup](#backup)
- [Collection Types](#collection-types)
- [Terms](#terms)
- [Run Tests](#run-tests)

## Prerequisites
- Postman v11.34.5 (used for creating and running Postman collections)
- Node.js >= v16
- Newman >= v6 (used for running Postman collections in CLI)
- Running Atum Service with a migrated database (see [Run Tests](#run-tests))

## Setup
- Import the Postman environment `Atum-Service_LOCAL_env.postman_environment.json` into Postman.
  - This file sets up env variables (`host`, defaults to `http://localhost:8080`).
- Import the Postman collection for `1-shot` test:
  - `1_shot/Atum-Service_1-Shot_test_flow.postman_collection.json`
    - **Flow** collection (see [Collection Types](#collection-types)) covering the published REST API endpoints.
    - Requests depend on values created by previous requests, so the folders and requests must run in order.

## Backup
- Export the Postman collections and replace the current ones in the project repository. (Rename to avoid spaces in the file name)
  - Why: To have a backup of the current state of the collections.
- Each collection is exported separately.

## Collection Types
Note: Each Postman collection run in sequence from the top to the bottom.

Currently, only the **flow** collection exists; the **isolated** type is reserved for future simple endpoint tests.

- **Isolated one**:
  - Contains only one request call per endpoint.
  - Cover only simple cases of simple endpoints - CRUD ones are covered in **flow** type.
- **Flow**:
  - Contains multiple request calls in form of a flow.
  - Cover complex cases of endpoints - CRUD ones are covered here.
  - The flow collection consist from multiple folders, each folder represents one named test with required preparations.
    - Folders allows to:
      - keep documentation test headers
      - split multiple tests related to one flow

## Terms
- `1-shot` category: Each REST API endpoint should be covered here only one call (ideally).
  - Why: To reach the goal of the minimalist test with coverage of all published application endpoints.
  - Goal: All requests have to pass without any error. Test should be defined with this goal.

## Run Tests

### Start Atum Service Locally
- Start PostgreSQL and migrate the database, see [database README](../database/README.md#deployment).
- Start the server (it listens on `http://localhost:8080`):
```zsh
sbt "project server" "runMain za.co.absa.atum.server.Main"
```

### Local Using Postman
- Open Postman.
- Import the Postman env file.
- Import the Postman collection.
- Select the environment for the collection.
- Run the collection.

### Local Using Newman
```yaml
cd api-tests

newman run ./1_shot/Atum-Service_1-Shot_test_flow.postman_collection.json \
--environment Atum-Service_LOCAL_env.postman_environment.json --reporters cli,csv --insecure \
--reporter-csv-export 1_shot_test_run_1.csv
```
