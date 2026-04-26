#!/usr/bin/env bash
# Aggregates the shared monitoring helpers for the root check.sh entrypoint.

check_monitor_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=/dev/null
source "${check_monitor_support_dir}/check-monitor-common.sh"
# shellcheck source=/dev/null
source "${check_monitor_support_dir}/check-monitor-progress.sh"
# shellcheck source=/dev/null
source "${check_monitor_support_dir}/check-monitor-diagnostics.sh"
# shellcheck source=/dev/null
source "${check_monitor_support_dir}/check-monitor-runner.sh"

unset check_monitor_support_dir
