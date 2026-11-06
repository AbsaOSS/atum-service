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

## Setup

- Import the Postman collection `Atum-Service_LOCAL_env.postman_environment.json` into Postman.
    - This file will set up env variables.
- Import the Postman collections for `1-shot` test:
    - `1_shot/Atum-Service_1-Shot_test_flow.postman_collection.json` (the only collection in this directory)
        - A **flow** collection: its folders run in sequence from the top to the bottom, each folder is one named test
          with its own preparations.
        - Covers the health endpoints, the v2 partitioning, ancestors, additional data, checkpoint and flow endpoints,
          and the deprecated v1 `createPartitioning` / `createCheckpoint` calls.

## Backup

- Export the Postman collections and replace the current ones in the project repository. (Rename to avoid spaces in the
  file name)
    - Why: To have a backup of the current state of the collections.
- Each collection is exported separately.

## Collection Types

Note: Each Postman collection run in sequence from the top to the bottom.

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

### Running locally (Newman against a local server)

The collection creates (uniquely named) data, so run it only against a **local or test stack**, never against a shared
or production host. 

The environment file `Atum-Service_LOCAL_env.postman_environment.json` points to `host=http://localhost:8080`.

1. Start a PostgreSQL database and migrate it as described in [`database/README.md`](../database/README.md#deployment).
2. Start the server (see [`server/README.md`](../server/README.md)); `GET http://localhost:8080/health` has to answer `200`.
3. Run Newman from this directory:

```yaml
cd api-tests

newman run ./1_shot/Atum-Service_1-Shot_test_flow.postman_collection.json \
--environment Atum-Service_LOCAL_env.postman_environment.json --reporters cli,json --insecure \
--reporter-json-export 1_shot_test_run.json
```

