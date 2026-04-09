#!/usr/bin/env bash
# Execute the pinned FinGrind sqlite3 binary, provisioning it from sqlite.org if needed.

set -euo pipefail

source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/sqlite-tooling.sh"

sqlite_install_if_missing
exec "${FINGRIND_SQLITE_BINARY_PATH}" "$@"
