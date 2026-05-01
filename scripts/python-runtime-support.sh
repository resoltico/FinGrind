#!/usr/bin/env bash
# Keep Python bytecode artifacts out of the repository during shell-driven verification flows.

prepare_python_runtime_env() {
    export PYTHONDONTWRITEBYTECODE=1
    if [[ -z "${PYTHONPYCACHEPREFIX:-}" ]]; then
        export PYTHONPYCACHEPREFIX="${TMPDIR:-/tmp}/fingrind-python-pycache.$$.$RANDOM"
    fi
}
