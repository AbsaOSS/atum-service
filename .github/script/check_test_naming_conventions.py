import os
import re
import argparse


# Patterns to identify test suite bases and any inheritances
TEST_BASE_PATTERNS = [
    r"class\s+(\w+)\s+extends\s+(\w+)",
    r"trait\s+(\w+)\s+extends\s+(\w+)",
    r"class\s+(\w+)\s+extends\s+.*\s+with\s+(\w+)",
    r"trait\s+(\w+)\s+extends\s+.*\s+with\s+(\w+)"
]

TEST_NAME_PATTERN = r".*(UnitTest|UnitSpec|IntegrationTest|IntegrationSpec)\.scala$"


def parse_test_bases(file_content):
    """ Parse file content to find all defined test bases. """
    bases = set()
    for pattern in TEST_BASE_PATTERNS:
        matches = re.finditer(pattern, file_content, re.MULTILINE)
        for match in matches:
            base = match.group(2)
            if base not in bases:
                bases.add(match.group(2))
    return bases


def is_test_suite(file_path, known_bases):
    """Check if the file is a test suite by looking for extensions of known test bases."""
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()

        pattern_is_suite = r"extends\s+(" + "|".join(re.escape(base) for base in known_bases) + r")(\s+with\s+.*)?"
        pattern_is_parent = r"class\s+(" + "|".join(re.escape(base) for base in known_bases) + r")"

        is_suite = re.search(pattern_is_suite, content, re.MULTILINE)
        is_parent = re.search(pattern_is_parent, content, re.MULTILINE)

    if is_suite and not is_parent:
        return True

    return False


def scan_tests(test_directory):
    pattern = re.compile(TEST_NAME_PATTERN)
    missed_tests = []
    known_bases = set()

    # First pass: identify all base test classes or traits
    for root, dirs, files in os.walk(test_directory):
        for file in files:
            if file.endswith(".scala"):
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    known_bases.update(parse_test_bases(content))

    print("Detected class parent classes: " + str(known_bases))

    # Second pass: check for test suites not matching naming conventions
    for root, dirs, files in os.walk(test_directory):
        for file in files:
            if file.endswith(".scala"):
                file_path = os.path.join(root, file)
                if is_test_suite(file_path, known_bases) and not pattern.match(file):
                    missed_tests.append(file_path)

    return missed_tests


def parse_arguments():
    """Set up CLI argument parsing."""
    parser = argparse.ArgumentParser(description="Check Scala test file naming conventions.")
    parser.add_argument('paths', nargs='+', help='A list of directory paths to scan for Scala test files.')
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_arguments()
    missed = []
    for test_dir in args.paths:
        missed.extend(scan_tests(test_dir))
    if missed:
        print("Tests not matching any pattern:")
        for test in missed:
            print(test)
    else:
        print("All test files match the expected patterns.")

    exit(len(missed))

