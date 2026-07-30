#!/usr/bin/env bash
# Verify that the GitHub release for the current tag exists as one published release with the
# canonical complete archive-and-checksum asset set attached and internally consistent.

set -euo pipefail

readonly script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./release-tag-support.sh
source "${script_dir}/release-tag-support.sh"
# shellcheck source=./verify-github-release-support.sh
source "${script_dir}/verify-github-release-support.sh"

readonly VERIFY_GITHUB_RELEASE_SCRIPT_DIR="${script_dir}"
readonly FINGRIND_RELEASE_PAYLOAD_ROOT="${FINGRIND_RELEASE_PAYLOAD_ROOT:-$(cd -P -- "${script_dir}/.." && pwd)}"

verify_github_release_main "$@"
