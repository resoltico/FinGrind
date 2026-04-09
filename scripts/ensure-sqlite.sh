#!/usr/bin/env bash
# Ensure the pinned SQLite toolchain exists locally and print the sqlite3 binary path.

set -euo pipefail

source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/sqlite-tooling.sh"

sqlite_install_if_missing
printf '%s\n' "${FINGRIND_SQLITE_BINARY_PATH}"
