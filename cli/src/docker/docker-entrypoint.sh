#!/bin/sh

set -eu

readonly runtime_java="/opt/fingrind/runtime/bin/java"
readonly application_jar="/opt/fingrind/app/fingrind.jar"

exec "${runtime_java}" \
    --enable-native-access=ALL-UNNAMED \
    -Dfingrind.runtime.distribution={{containerRuntimeDistribution}} \
    -jar "${application_jar}" \
    "$@"
