"""
endpoints_coverage.py

This script measures the coverage of API endpoints in a project by comparing the extracted endpoints from source files with the used endpoints in Postman collections.

Usage:
    python endpoints_coverage.py <source_csv> <postman_csv> <output_csv>

Arguments:
    source_csv      : The CSV file containing the extracted API endpoints from source files.
    postman_csv     : The CSV file containing the extracted API endpoints from Postman collections

Output:
    The environment variables:
    - TOTAL_EXPECTED_TESTS - amount of extracted mappings from source files == expected amount of tests.
    - COVERED_TESTS - amount of mappings that are covered by the Postman collection.

Example:
    python extractor_endpoints_from_postman_collection.py postman_collection.json output.csv

Environment Variables:
    GITHUB_ENV       : Required to be set. The script appends the measured numbers to this file.

"""

import os
import sys
import csv
import argparse
from collections import Counter

def extract_mappings(path_to_source_csv, path_to_postman_csv):
    # load the source csv file
    with open(path_to_source_csv, 'r', encoding='utf-8') as source_file:
        source_reader = csv.reader(source_file)
        source_endpoints = {row[1] for row in source_reader if row[1] != 'extracted_values'}

    # load the postman csv file
    with open(path_to_postman_csv, 'r', encoding='utf-8') as postman_file:
        postman_reader = csv.reader(postman_file)
        postman_endpoints = Counter(row[0] for row in postman_reader if row[0] != 'extracted_values')
        print(f"postman's endpoints {len(postman_endpoints)}")

    # get TOTAL_EXPECTED_TESTS
    total_expected_items = len(source_endpoints)
    covered_items = 0
    for endpoint in source_endpoints:
        count = postman_endpoints.get(endpoint, 0)
        print(f"Endpoint: {endpoint}, Count in Postman: {count}")
        if count > 0:
            covered_items += 1

    for postman_endpoint in postman_endpoints:
        if postman_endpoint not in source_endpoints:
            print(f"Endpoint in Postman but not in source: {postman_endpoint}")

    # Print to console for debugging
    print(f"Total Expected Items: {total_expected_items}")
    print(f"Covered Items: {covered_items}")

    # Write the results to the GitHub environment file
    github_env_path = os.getenv('GITHUB_ENV')
    if github_env_path:
        with open(github_env_path, 'a') as env_file:
            env_file.write(f"EXPECTED_ITEMS={total_expected_items}\n")
            env_file.write(f"COVERED_ITEMS={covered_items}\n")

if __name__ == "__main__":
    # Command-line arguments setup
    parser = argparse.ArgumentParser(description="Measure the coverage of API endpoints in a project by comparing the extracted endpoints from source files with the used endpoints in Postman collections.")
    parser.add_argument('source_csv', type=str, help="The CSV file containing the extracted API endpoints from source files.")
    parser.add_argument('postman_csv', type=str, help="The CSV file containing the extracted API endpoints from Postman collections.")

    # Parse arguments
    args = parser.parse_args()
    path_to_source_csv = args.source_csv
    path_to_postman_csv = args.postman_csv

    print(f"Source CSV file: {path_to_source_csv}")
    print(f"Postman CSV file: {path_to_postman_csv}")

    # Run the mapping extraction
    extract_mappings(path_to_source_csv, path_to_postman_csv)
