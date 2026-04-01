#!/bin/sh
set -e

if [ -z "$JAVA_HOME" ] ; then
    JAVACMD="java"
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -r "$MAVEN_WRAPPER_PROPERTIES" ]; then
    MAVEN_URL=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

DIST_NAME="$(basename "$MAVEN_URL" .zip)"
MAVEN_HOME="$HOME/.m2/wrapper/dists/$DIST_NAME"

if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Maven from $MAVEN_URL"
    mkdir -p "$MAVEN_HOME"
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL "$MAVEN_URL" -o "$MAVEN_HOME/maven.zip"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "$MAVEN_URL" -O "$MAVEN_HOME/maven.zip"
    else
        echo "Error: curl or wget required" >&2
        exit 1
    fi
    unzip -q "$MAVEN_HOME/maven.zip" -d "$MAVEN_HOME"
    rm -f "$MAVEN_HOME/maven.zip"
fi

for dir in "$MAVEN_HOME"/apache-maven-*; do
    if [ -d "$dir" ] && [ -x "$dir/bin/mvn" ]; then
        exec "$dir/bin/mvn" "$@"
    fi
done

echo "Error: Maven distribution not found in $MAVEN_HOME" >&2
exit 1
