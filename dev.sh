#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export GRADLE_USER_HOME="$ROOT/.cache/gradle"
export TMPDIR="$ROOT/.tmp"
mkdir -p "$GRADLE_USER_HOME" "$TMPDIR"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
cd "$ROOT"
PLATFORM="${1:-neoforge}"
if [ "$#" -gt 0 ]; then shift; fi
if [ "$#" -eq 0 ]; then set -- build; fi
if [ "$PLATFORM" = fabric ]; then
    exec sh "$ROOT/fabric/gradlew" -p "$ROOT/fabric" "$@"
fi
exec sh "$ROOT/gradlew" "$@"
