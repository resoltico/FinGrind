#!/usr/bin/env bash
# Canonical fixed-stage contract for the root check.sh entrypoint.

readonly check_stage_ids=(
    quality-gates
    jazzer-check
    cli-bundle
    bundle-smoke
    shell-syntax
    docker-smoke
)

readonly check_stage_labels=(
    'Stage 1/6: running quality gates'
    'Stage 2/6: running Jazzer deterministic tests and regression replay'
    'Stage 3/6: building self-contained CLI bundle archive'
    'Stage 4/6: running self-contained bundle acceptance test'
    'Stage 5/6: checking release-surface shell scripts'
    'Stage 6/6: running Docker acceptance test'
)

readonly check_stage5_executable_script_paths=(
    scripts/test-devcontainer-workflow.sh
    scripts/test-ci-shell-syntax-workflow.sh
    scripts/test-container-workflow-timeout.sh
    scripts/test-prepare-release-version.sh
    scripts/test-read-contract-values.sh
    scripts/test-bundle-smoke-powershell.sh
    scripts/test-release-smoke-workflow.sh
    scripts/test-release-protocol-pr-diff-fallback.sh
    scripts/test-verify-jacoco-snapshot.sh
    scripts/test-verify-release-candidate-tag.sh
    scripts/test-verify-release-merge-handoff.sh
    scripts/test-verify-public-container-surface.sh
    scripts/test-gradlew-bat-wrapper.sh
    scripts/test-gradle-wrapper-support.sh
    scripts/test-check-process-support.sh
    scripts/test-check-stage-contract.sh
    scripts/test-jazzer-fuzz-all-wrapper.sh
    scripts/test-verify-github-release.sh
    scripts/test-verify-release-primary-checkout.sh
    scripts/test-verify-managed-sqlite-runtime.sh
    scripts/validate-devcontainer.sh
    scripts/verify-managed-sqlite-runtime.sh
    scripts/verify-jacoco-snapshot.sh
)

readonly check_stage5_shell_only_script_paths=(
    scripts/verify-release-candidate-tag.sh
    scripts/verify-release-merge-handoff.sh
)

check_stage_usage_lines() {
    printf '%s\n' \
        '  1. check coverage' \
        '  2. jazzer check' \
        '  3. :cli:bundleCliArchive' \
        '  4. scripts/bundle-smoke.sh (bundle acceptance workflow)' \
        "  5. $(check_stage5_usage_command)" \
        '  6. scripts/docker-smoke.sh (Docker acceptance workflow)'
}

check_stage5_usage_command() {
    printf '%s\n' './scripts/check-shell-syntax.sh'
}

check_stage_execute() {
    local stage_id=$1
    local stage_label=$2
    local check_repo_root=$3

    case "${stage_id}" in
        quality-gates)
            run_stage "${stage_id}" "${stage_label}" "${check_repo_root}" check coverage
            ;;
        jazzer-check)
            run_stage "${stage_id}" "${stage_label}" "${check_repo_root}/jazzer" check
            ;;
        cli-bundle)
            run_stage "${stage_id}" "${stage_label}" "${check_repo_root}" :cli:bundleCliArchive
            ;;
        bundle-smoke)
            run_shell_stage "${stage_id}" "${stage_label}" "${check_repo_root}/scripts/bundle-smoke.sh"
            ;;
        shell-syntax)
            run_shell_stage "${stage_id}" "${stage_label}" \
                "${check_repo_root}/scripts/check-shell-syntax.sh"
            ;;
        docker-smoke)
            run_shell_stage "${stage_id}" "${stage_label}" "${check_repo_root}/scripts/docker-smoke.sh"
            ;;
        *)
            die "unsupported fixed stage id from ${stage_contract_script}: ${stage_id}"
            ;;
    esac
}
