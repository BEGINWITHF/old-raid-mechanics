#!/usr/bin/env bash
# Runs Apache Maven with an MSYS/Git-Bash safe invocation.
#
# The mvn shell script shipped with Maven passes MSYS style paths (/c/...) to a native
# java.exe, which cannot resolve them, so this wrapper starts the classworlds launcher
# directly with Windows paths.
#
# Override MAVEN_HOME_DIR / JAVA_BIN if your install lives somewhere else.
set -euo pipefail

MAVEN_HOME_DIR="${MAVEN_HOME_DIR:-/c/tools/apache-maven-3.9.11}"
JAVA_BIN="${JAVA_BIN:-/c/Program Files/Java/jdk-25.0.4/bin/java}"

to_win() {
    cygpath -w "$1" 2>/dev/null || printf '%s' "$1"
}

CLASSWORLDS="$(ls "$MAVEN_HOME_DIR"/boot/plexus-classworlds-*.jar | head -n 1)"

exec "$JAVA_BIN" \
    -classpath "$(to_win "$CLASSWORLDS")" \
    -Dclassworlds.conf="$(to_win "$MAVEN_HOME_DIR/bin/m2.conf")" \
    -Dmaven.home="$(to_win "$MAVEN_HOME_DIR")" \
    -Dmaven.multiModuleProjectDirectory="$(to_win "$PWD")" \
    org.codehaus.plexus.classworlds.launcher.Launcher "$@"
