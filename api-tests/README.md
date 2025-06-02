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
- Import the Postman collection `Atum-Service_DEV_env.postman_environment.json` into Postman. 
  - This file will set up env variables.
- Import the Postman collections for `1-shot` test:
  - `1_shot/Atum-Service_1-Shot_test_isolated_calls.postman_collection.json`
    - Contains all tests for REST API endpoints where one request call is enough for `1-shot` coverage.
    - Each test calls one REST API endpoint **in isolation**.

## Backup
- Export the Postman collections and replace the current ones in the project repository. (Rename to avoid spaces in the file name)
  - Why: To have a backup of the current state of the collections.
- Each collection is exported separately.

## Collection Types
Note: Each Postman collection run in sequence from the top to the bottom.

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

### Local Using Postman
- Open Postman.
- Import the Postman env file.
- Import the Postman collection.
- Select the environment for the collection.
- Run the collection.

### Local Using Newman
```yaml
cd {working_directory}

newman run ./1_shot/{SOME_FILE}.postman_collection.json \
--environment Atum-Service_DEV_env.postman_environment.json --reporters cli,csv --insecure \
--reporter-csv-export 1_shot_test_run_1.csv
```

## Utils

### Extract Project Endpoints
The python script `extract_project_endpoints.py` extracts all endpoints from the project source code. 

- The output is used during CI run to check if all endpoints are covered by API tests.
- The output example:
```yaml
path_to_file,extracted_values
/{path-to-file}/DomainManagementController.scala,GET:/api-v2/domain-management/queries/domain-primary-owner/{domainID}
/{path-to-file}/DomainManagementController.scala,GET:/api-v2/domain-management/queries/domain-name/{domainID}
/{path-to-file}/DomainManagementController.scala,GET:/api-v2/domain-management/queries/domain/{domainID}
```

### Extract Endpoints covered in API Test
The python script `extract_endpoints_from_postman_collection.py` extracts all endpoints from the Postman collection.
As the source of covered endpoints, it uses the test folder's descriptions.

- The output example:
```yaml
extracted_values
GET:/api-v2/info/version
GET:/api-v2/user-identity-info/users/{userID}
```

## Endpoints Coverage Measurement
The python script `endpoints_coverage.py` measures the coverage of the endpoints in the project source code and the API tests.
As inputs are used the extracted endpoints from the project source code and the API tests.
- The script creates an environment variables `TOTAL_EXPECTED_TESTS` and `COVERED_TESTS`.
- The script prints a list of endpoints detected from source code and count of their usage in Postman API tests. **Use to find missing coverage easily!**
- The script prints a list of endpoints detected in Postman API tests and not present in the source code. **POSSIBLE BUGS in tests!**

## Postman Csv Report Analyser
The python script `postman_csv_report_analysis.py` analyses the Postman CSV reports and creates an environment variables `SUCCESS_COUNT` and `FAILED_COUNT`.
