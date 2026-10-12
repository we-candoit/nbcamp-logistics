#!/bin/sh
set -e
JAR=$(ls build/libs/*.jar 2>/dev/null | grep -v plain | head -1)

if [ -z "$JAR" ] || [ -n "$(find src -newer "$JAR" | head -1)" ]; then
  sh ./gradlew bootJar -x test --no-daemon
  JAR=$(ls build/libs/*.jar | grep -v plain | head -1)
fi

exec java -jar "$JAR"