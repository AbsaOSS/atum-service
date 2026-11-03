# API tests

- [Pre-requisites](#prerequisites)
- [Setup](#setup)
- [Backup](#backup)
- [Tests Creation Rules](#tests-creation-rules)
- [Terms](#terms)
- [Run Tests](#run-tests)
- [Utils](#utils)
- [Endpoints Coverage Measurement](#endpoints-coverage-measurement)

## Prerequisites
- Postman v11.34.5 (used for creating and running Postman collections)
- Node.js >= v16
- Newman >= v6 (used for running Postman collections in CLI)

## Setup
- Import the Postman collection `Atum-Service_LOCAL_env.postman_environment.json` into Postman. 
  - This file will set up env variables.
- Import the Postman collections for `1-shot` test:
  - `1_shot/Atum-Service_1-Shot_test_flow.postman_collection.json` (the only collection in this directory)
    - A **flow** collection: its folders run in sequence from the top to the bottom, each folder is one named test with its own preparations.
    - Covers the health endpoints, the v2 partitioning, ancestors, additional data, checkpoint and flow endpoints, the deprecated v1 `createPartitioning` / `createCheckpoint` calls and, in the last folder, the consolidated POST and linking behaviour of v1.1 (see [Coverage of the v1.1 folder](#coverage-of-the-v11-folder)).

## Backup
- Export the Postman collections and replace the current ones in the project repository. (Rename to avoid spaces in the file name)
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
The collection creates (uniquely named) data, so run it only against a **local or test stack**, never against a shared or production host.
The environment file `Atum-Service_LOCAL_env.postman_environment.json` points to `host=http://localhost:8080`.

1. Start a PostgreSQL database and migrate it as described in [`database/README.md`](../database/README.md#resetting-a-development-database)
   (a database that already applied an earlier V0.10.0.x migration of the unreleased v1.1 work has to be reset first, `flywayClean` alone is not enough).
2. Start the server (see [`server/README.md`](../server/README.md)); `GET http://localhost:8080/health` has to answer `200`.
3. Run Newman from this directory:
```yaml
cd api-tests

newman run ./1_shot/Atum-Service_1-Shot_test_flow.postman_collection.json \
--environment Atum-Service_LOCAL_env.postman_environment.json --reporters cli,json --insecure \
--reporter-json-export 1_shot_test_run.json
```
The run is repeatable against the same database: the first request of the v1.1 folder always generates a fresh `v11_suffix` (`v11_<Date.now()>_<random>`) and clears every other `v11_*` variable, so an aborted earlier run (or a run started in the middle of the folder) cannot leak state into the next one, and every partitioning key of the folder is new. The original folders use `api_tests_<Date.now()>`.
Requests of the v1.1 folder that need an id captured by an earlier request fail with an explicit "run the folder from its first request" error instead of a misleading assertion when started in the middle. The only fixed ids are the "unknown parent" probes (`9007199254740991`, and `9007199254740000+i` for the 101-id cap); they assume that no database sequence ever reaches that range.

### Coverage of the v1.1 folder
The last folder, `Test: consolidated POST and linking (v1.1)`, is appended after the original folders and uses only its own collection variables (prefix `v11_`); it has 25 requests (the whole collection 49). It is an HTTP-contract smoke test with one happy path per behaviour; boundaries and edge cases (self-link, the 100-parent boundary, an absent copy flag, the PATCH copy-flag matrix, concurrency) are covered by the database and server tests.
It checks:
- `POST /api/v2/partitionings` of a new partitioning answers `201` with a `Location` header; the same POST again (even with another author) answers `200`, the same id, the same `Location` and the **stored** author.
- A single new parent can be linked to an already existing partitioning through the same POST (`200`, same id and `Location`; the ancestors are exactly that parent); a **multi-parent POST** (a new child with two valid parents in one call) puts the child into the flows of both parents.
- Flow membership is asserted as **exact sets** (the flow of a parent contains exactly the parent, its children and the expected descendants, nothing else), so a leak of an unrelated partitioning into a flow fails the run.
- **Descendant propagation:** when a partitioning that already has a child is linked under a new parent with `PATCH`, the child's existing descendants appear in the new parent's flow.
- `copyMeasureDefinitions: true` copies the parent measure definitions to a **new** child, `false` copies none; additional data is never copied by the POST.
- Error contract, one request each: a cycle is rejected with `409` (POST of a partitioning under its own descendant); an unknown parent answers `404` and the child is not created; 101 distinct parents answer `400`; `PATCH /api/v2/partitionings/{id}/ancestors` without the required copy flags answers `400`. Error responses are asserted by status code and envelope shape (`requestId`, `message`), not by the wording of the message.
- `GET /api/v2/partitionings/{id}/ancestors` answers `200` with the exact set of the direct parents.
