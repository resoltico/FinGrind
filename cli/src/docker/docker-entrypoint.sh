#!/bin/sh

set -eu

readonly app_home="/opt/fingrind"
readonly runtime_java="${app_home}/runtime/bin/java"
readonly application_jar="${app_home}/lib/app/fingrind.jar"
readonly application_module="dev.erst.fingrind.cli/dev.erst.fingrind.cli.App"
readonly working_directory="$(pwd -P)"

exec "${runtime_java}" \
    --enable-native-access=dev.erst.fingrind.cli \
    --add-opens=java.base/java.nio=dev.erst.fingrind.cli \
    --add-exports=java.base/sun.nio=dev.erst.fingrind.cli \
    -D{{sqliteBundleHomeSystemProperty}}="${app_home}" \
    -Duser.home="${working_directory}" \
    -Dfingrind.runtime.distribution={{containerRuntimeDistribution}} \
    --module-path "${application_jar}" \
    --module "${application_module}" \
    "$@"
