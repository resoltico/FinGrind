#!/usr/bin/env bash
# Guard the repo-owned structural-governance verifier surface.

set -euo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=verify-structural-governance-test-support.sh
source "${script_dir}/verify-structural-governance-test-support.sh"

run_structural_governance_regressions

printf 'structural governance verifier regression: success\n'
