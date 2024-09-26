#!/bin/sh

# These two are locations known and used in the Dockerfile.
CUSTOM_CONFIG_LOCATION="/opt/config/resource.conf"
JAR_LOCATION="/opt/app/atum-service.jar"

# Why `exec` - mostly for a correct termination signal handling & finishing the application.
#
# Explanation: The exec command replaces the shell process with the Java process, so the Java process (our application)
#   becomes the main process (PID 1) in the container. Docker automatically sends termination signals
#   (SIGTERM, SIGINT, etc.) to PID 1, so the Java process will directly receive the signals and can terminate cleanly.
#
#   When you define ENTRYPOINT in a Dockerfile and it points to a shell script, as it is in our case, what actually
#   happens is that the shell starts first, executes the script, and then runs whatever commands are inside that script.
#   For example, without exec, the process flow looks like this:
#     1. Docker starts the container, and the shell process (/bin/sh) is launched.
#     2. The shell process begins executing this `docker_entrypoint.sh` script.
#     3. Inside the script, if you run java, the shell spawns a child process to run the java command, while the shell
#       process remains the parent process (still PID 1). But what we want is our Java to have PID 1 to receive
#       termination signals.
if [ -f "${CUSTOM_CONFIG_LOCATION}" ]; then
    echo "Running with custom config"
    exec java -Dconfig.file="${CUSTOM_CONFIG_LOCATION}" -jar "${JAR_LOCATION}"
else
    echo "Running with default config"
    exec java -jar "${JAR_LOCATION}"
fi
