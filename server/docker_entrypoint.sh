#!/bin/sh

# These two are locations known and used in the Dockerfile.
CUSTOM_CONFIG_LOCATION="/opt/config/resource.conf"
JAR_LOCATION="/opt/app/atum-service.jar"

if [ -f "${CUSTOM_CONFIG_LOCATION}" ]; then
    echo "Running with custom config"
    java -Dconfig.file="${CUSTOM_CONFIG_LOCATION}" -jar "${JAR_LOCATION}"
else
    echo "Running with default config"
    java -jar "${JAR_LOCATION}"
fi
