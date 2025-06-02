"""
postman_csv_report_analysis.py

This script perform analysis of Postman's CSV report to extract the amount of successful and failed tests.

Usage:
    python postman_csv_report_analysis.py <postman_csv>

Arguments:
    postman_csv     : Space-separated list of Postman's CSV report files.

Output:
    The environment variables:
    - SUCCESS_COUNT - amount of successful tests.
    - FAILED_COUNT - amount of failed tests.

Example:
    python postman_csv_report_analysis.py postman_report.csv

Environment Variables:
    GITHUB_ENV       : Required to be set. The script appends the measured numbers to this file.

"""

import os
import sys
import csv
import argparse

def extract_mappings(path_to_postman_csv_files):
    successful_tests = 0
    failed_tests = 0

    for path_to_postman_csv in path_to_postman_csv_files:
        with open(path_to_postman_csv, 'r', encoding='utf-8') as postman_file:
            postman_reader = csv.reader(postman_file)
            headers = next(postman_reader)  # Skip the header row

            # get SUCCESS_COUNT and FAILED_COUNT
            for row in postman_reader:
                if "Act:" in row[headers.index('requestName')]:
                    executed_count = int(row[headers.index('executedCount')])
                    failed_count = int(row[headers.index('failedCount')])
                    if executed_count > 0 and failed_count == 0:
                        successful_tests += 1
                    else:
                        failed_tests += 1

    # Print to console for debugging
    print(f"Total tests: {successful_tests + failed_tests}")
    print(f"Succesful tests: {successful_tests}")
    print(f"Failed tests: {failed_tests}")

    # Write the results to the GitHub environment file
    github_env_path = os.getenv('GITHUB_ENV')
    if github_env_path:
        with open(github_env_path, 'a') as env_file:
            env_file.write(f"TOTAL_TESTS={successful_tests + failed_tests}\n")
            env_file.write(f"SUCCESS_TESTS={successful_tests}\n")
            env_file.write(f"FAILED_TESTS={failed_tests}\n")

if __name__ == "__main__":
    # Command-line arguments setup
    parser = argparse.ArgumentParser(description="Extract amount of successful and failed tests from Postman's CSV report.")
    parser.add_argument('postman_csv_files', nargs='+', type=str, help="The Postman CSV report file.")

    # Parse arguments
    args = parser.parse_args()
    path_to_postman_csv = args.postman_csv_files

    print(f"Postman CSV file: {path_to_postman_csv}")

    # Run the mapping extraction
    extract_mappings(path_to_postman_csv)
