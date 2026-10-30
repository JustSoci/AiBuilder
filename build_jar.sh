#!/usr/bin/env bash
set -e
echo "Building AI Builder Forge 1.20.1 JAR..."
gradle build --no-daemon
echo "Done! Your compiled mod is located at: build/libs/aibuilder-1.0.0.jar"
