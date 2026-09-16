#!/usr/bin/env bash
# Builds target/OldRaidMechanics-1.0.0.jar
set -euo pipefail
cd "$(dirname "$0")"

./mvn.sh -q -DskipTests package

echo
echo "built: $(pwd)/target/OldRaidMechanics-1.0.0.jar"
