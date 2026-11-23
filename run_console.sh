#!/usr/bin/env bash
# ==============================================================================
# CloudSim 4.0 Isolation & Density Study - Linux / macOS Console Launcher
# Package: isolationstudy
# Owned by Person 3
# ==============================================================================

set -e
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$BASE_DIR"

# 1. Resolve Java commands
JAVAC_CMD="javac"
JAVA_CMD="java"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC_CMD="$JAVA_HOME/bin/javac"
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# 2. Verify Library Dependencies
CP="cloud-iso-study/lib/cloudsim-4.0.jar:cloud-iso-study/lib/commons-math3-3.6.1.jar"

if [ ! -f "cloud-iso-study/lib/cloudsim-4.0.jar" ]; then
    echo "[ERROR] cloud-iso-study/lib/cloudsim-4.0.jar not found!"
    echo "Please place the CloudSim 4.0 JAR in cloud-iso-study/lib/."
    exit 1
fi

if [ ! -f "cloud-iso-study/lib/commons-math3-3.6.1.jar" ]; then
    echo "[ERROR] cloud-iso-study/lib/commons-math3-3.6.1.jar not found!"
    echo "Please place the commons-math3-3.6.1 JAR in cloud-iso-study/lib/."
    exit 1
fi

# 3. Compile sources into target/classes
mkdir -p target/classes
find src/isolationstudy -name "*.java" > sources_linux.tmp
"$JAVAC_CMD" -cp "$CP" -d target/classes @sources_linux.tmp
rm -f sources_linux.tmp

# 4. Execute Main entry point
"$JAVA_CMD" -cp "target/classes:$CP" isolationstudy.Main "$@"

