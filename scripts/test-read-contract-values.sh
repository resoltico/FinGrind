#!/usr/bin/env bash
# Run the canonical contract-reader regression suite.

set -euo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${script_dir}/test-read-contract-values.py"
