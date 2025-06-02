"""
extractor_project_endpoints.py

This script extracts API endpoint mappings from Java and Scala source files within a specified directory.
It identifies annotations such as @RequestMapping and @Get|Post|Put|DeleteMapping and extracts the endpoint values.

Usage:
    python extractor_project_endpoints.py <directory> <output_csv> [--file_filters <file_extensions>] [--exclude <exclude_files>]

Arguments:
    directory        : The root directory to search for source files.
    output_csv       : The name of the output CSV file.
    --file_filters   : Space-separated list of file extensions to include (default: .java .scala).
    --exclude        : Space-separated list of file names or patterns to exclude.

Example:
    python extractor_project_endpoints.py src output.csv --file_filters .java .scala --exclude Test.java Example.scala

Functions:
    extract_mappings(directory, file_extensions, output_csv, exclude_files)
        Extracts API endpoint mappings from the specified directory and writes them to a CSV file.

    main()
        Parses command-line arguments and invokes the extract_mappings function.

"""

import os
import re
import csv
import argparse

def extract_mappings(directory, file_extensions, output_csv, exclude_files):
    # Regex patterns for annotations
    # Regex patterns to match @RequestMapping and @Get|Post|Put|DeleteMapping annotations
    request_mapping_pattern = re.compile(
        r'@RequestMapping\s*\(\s*value\s*=\s*Array\(\s*["\'](.*?)["\']\s*\)', re.DOTALL
    )
    general_mapping_pattern = re.compile(
        r'@(\w+Mapping)\s*\(\s*value\s*=\s*(Array\()?["\'](.*?)["\']', re.DOTALL
    )
    mappings = []

    # Walk through the directory
    for root, _, files in os.walk(directory):
        if "src" not in root:
            continue

        for file in files:
            if any(file.endswith(ext) for ext in file_extensions):
                file_path = os.path.join(root, file)

                # Convert to absolute path
                absolute_file_path = os.path.abspath(file_path)

                # Check if the file should be excluded
                if any(excluded in file for excluded in exclude_files):
                    continue

                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                    # Find all @RequestMapping values in the file (typically one or few per file)
                    request_matches = request_mapping_pattern.findall(content)
                    base_mappings = '+'.join(request_matches)

                    # Find and extract each @?Mapping value
                    general_matches = general_mapping_pattern.findall(content)

                    # Process matches and clean output
                    for match in general_matches:
                        mapping_type: str = match[0]  # e.g., GetMapping, PostMapping, etc.
                        mapping_value: str = match[2]  # The endpoint value inside "..."

                        if base_mappings in mapping_value:
                            continue

                        mapping_formatted = mapping_type.upper().replace("MAPPING", "").strip()
                        mappings.append([absolute_file_path, f"{mapping_formatted}:{base_mappings}{mapping_value}"])

    # Write output to CSV
    with open(output_csv, 'w', newline='', encoding='utf-8') as csvfile:
        csvwriter = csv.writer(csvfile)
        csvwriter.writerow(['path_to_file', 'extracted_values'])
        csvwriter.writerows(mappings)
        print(f"Extracted {len(mappings)} REST API endpoints")

    print(f"CSV file generated: {output_csv}")
    return len(mappings)

if __name__ == "__main__":
    # Command-line arguments setup
    parser = argparse.ArgumentParser(description="Map API endpoints from files with @RequestMapping and @GetMapping annotations.")
    parser.add_argument('directory', type=str, help="The root directory to search from.")
    parser.add_argument('output_csv', type=str, help="The output CSV file name.")
    parser.add_argument('--file_filters', nargs='+', type=str, default=['.java', '.scala'],
                        help="Comma-separated file extensions to include (e.g., '.java .scala'). Default is '.java .scala'.",
                        required=False)
    parser.add_argument('--exclude', nargs='+', type=str, default='',
                        help="Comma-separated list of file names or patterns to exclude.",
                        required=False)

    # Parse arguments
    args = parser.parse_args()
    directory_to_search = args.directory
    output_file = args.output_csv
    file_filters = args.file_filters
    exclude_files = args.exclude

    print(f"Searching directory: {directory_to_search}")
    print(f"Output CSV file: {output_file}")
    print(f"File filters: {file_filters}")
    print(f"Exclude files: {exclude_files}")

    # Run the mapping extraction
    extract_mappings(directory_to_search, file_filters, output_file, exclude_files)
