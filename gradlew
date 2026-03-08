#!/bin/sh

#
# Graduate Gradle wrapper script
#

# Add some default JVM options from the environment
if [ -z "$JAVA_OPTS" ]; then
  JAVA_OPTS="-Xmx1024m"
fi

# Determine the Java command to use to run the wrapper.
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/bin/java" ]; then
        # Execution status of the last command
        JAVACMD="$JAVA_HOME/bin/java"
    else
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || {
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
        exit 1
    }
fi

# Get original path to this script
APP_PATH=$(readlink -f "$0")
APP_HOME=$(dirname "$APP_PATH")

# Setup the classpath
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Execute Gradle
exec "$JAVACMD" $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
