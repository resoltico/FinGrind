#!/bin/sh

set -eu

readonly app_home="/opt/fingrind"
readonly runtime_java="${app_home}/runtime/bin/java"
readonly application_jar="${app_home}/lib/app/fingrind.jar"
readonly application_module="fingrind/dev.erst.fingrind.cli.App"

exec "${runtime_java}" \
    --enable-native-access=fingrind \
    -D{{bundleHomeSystemProperty}}="${app_home}" \
    -Dfingrind.runtime.distribution={{containerRuntimeDistribution}} \
    --module-path "${application_jar}" \
    --module "${application_module}" \
    "$@"
