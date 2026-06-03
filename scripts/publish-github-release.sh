#!/usr/bin/env bash
# Converge the GitHub release draft for the current tag onto the expected staged asset state.

set -euo pipefail

readonly script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./publish-github-release-support.sh
source "${script_dir}/publish-github-release-support.sh"

publish_release_main "$@"
