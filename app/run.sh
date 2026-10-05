#!/bin/sh
# Runs GreenCorridor. Build it first with: mvn package
cd "$(dirname "$0")" && java -jar target/GreenCorridor.jar
