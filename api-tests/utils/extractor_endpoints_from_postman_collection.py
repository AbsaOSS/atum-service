"""
extractor_endpoints_from_postman_collection.py

This script extracts API endpoint mappings from a Postman collection JSON file.

Usage:
    python extractor_endpoints_from_postman_collection.py <path-to-postman-json> <output_csv> [--append]

Arguments:
    path-to-postman-json    : The path to the Postman collection JSON file.
    output_csv              : The name of the output CSV file.
    --append                : If set, the script appends to the output CSV file.

Example:
    python extractor_endpoints_from_postman_collection.py postman_collection.json output.csv --append

"""

import csv
import argparse
import json

def extract_mappings(postman_json_path, output_csv, append: bool):
    def extract_from_items(items, csvwriter):
        for item in items:
            # Check if the item name starts with "Test:"
            if item.get('name', '').startswith('Test:'):
                # Get the description of the folder
                description = item.get('description', '')

                # Find "Covers endpoints:" in the description
                if 'Covers endpoints:' in description:
                    # Extract the endpoints listed under "Covers endpoints:"
                    endpoints = description.split('Covers endpoints:')[1].strip().split('\n')
                    endpoints = [
                        endpoint.strip()[1:].replace(" ", "")
                        for endpoint in endpoints
                        if endpoint.strip() and endpoint.strip().startswith('-') and ":" in endpoint
                    ]

                    # Write the extracted endpoints to the CSV file
                    for endpoint in endpoints:
                        csvwriter.writerow([endpoint])
                else:
                    print(f"Error: No section 'Covers endpoints:' found in folder '{item.get('name')}'.")

            # Recursively process nested items
            if 'item' in item:
                extract_from_items(item['item'], csvwriter)

    # open the received json file
    with open(postman_json_path, 'r', encoding='utf-8') as f:
        postman_collection = json.load(f)

    mode = 'a' if append else 'w'
    with open(output_csv, mode, newline='', encoding='utf-8') as csvfile:
        csvwriter = csv.writer(csvfile)
        if not append:
            csvwriter.writerow(['extracted_values'])

        # Start the recursive extraction
        extract_from_items(postman_collection.get('item', []), csvwriter)

    print(f"Extracted endpoints from {postman_json_path} and saved to {output_csv}")


if __name__ == "__main__":
    # Command-line arguments setup
    parser = argparse.ArgumentParser(description="Extract API endpoint mappings from a Postman collection JSON file.")
    parser.add_argument('directory', type=str, help="The root directory to search from.")
    parser.add_argument('output_csv', type=str, help="The output CSV file name.")
    parser.add_argument('--append', action='store_true', help="If set, the script appends to the output CSV file.")

    # Parse arguments
    args = parser.parse_args()
    postman_json_path = args.directory
    output_file = args.output_csv
    append = args.append

    print(f"Postman JSON file: {postman_json_path}")
    print(f"Output CSV file: {output_file}")
    print(f"Append mode: {append}")

    # Run the mapping extraction
    extract_mappings(postman_json_path, output_file, append)
