#!/usr/bin/env bash
# Builds Drift and creates a jpackage app-image for the CURRENT OS (used to verify packaging outside the IDE on Linux).
# For the Windows installer use scripts/package.ps1 on a Windows machine.
set -euo pipefail
cd "$(dirname "$0")/.."
MVN=${MVN:-mvn}
$MVN -B -DskipTests package
rm -rf target/jpackage-input target/dist
mkdir -p target/jpackage-input/lib
cp target/drift.jar target/jpackage-input/
cp target/lib/*.jar target/jpackage-input/lib/
VERSION=$($MVN -q -B help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null | tail -1)
jpackage --type app-image --name Drift --app-version "$VERSION" --vendor "Drift" \
  --input target/jpackage-input --main-jar drift.jar --main-class com.drift.DriftLauncher \
  --icon packaging/drift.png --dest target/dist \
  --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.sql,java.xml,java.prefs,jdk.unsupported,jdk.crypto.ec \
  --java-options "-Dfile.encoding=UTF-8" --java-options "-Xmx512m"
echo "Packaged: target/dist/Drift"
