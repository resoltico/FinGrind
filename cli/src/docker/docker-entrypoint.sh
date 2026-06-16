#!/bin/sh

set -eu

readonly app_home="/opt/fingrind"
readonly runtime_java="${app_home}/runtime/bin/java"
readonly application_jar="${app_home}/lib/app/fingrind.jar"
readonly application_module="dev.erst.fingrind.cli/dev.erst.fingrind.cli.App"

exec "${runtime_java}" \
    --enable-native-access=dev.erst.fingrind.cli \
    -D{{sqliteBundleHomeSystemProperty}}="${app_home}" \
    -Dfingrind.runtime.distribution={{containerRuntimeDistribution}} \
    --module-path "${application_jar}" \
    --module "${application_module}" \
    "$@"
