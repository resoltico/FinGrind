#!/usr/bin/env bash
# Canonical fixed-stage contract for the root check.sh entrypoint.

readonly check_stage_ids=(
    quality-gates
    jazzer-check
    cli-bundle
    bundle-smoke
    release-surface-scripts
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

# Canonical host-independent regressions for Windows-owned command surfaces. The mandatory local
# preflight runs these through a real pinned pwsh process, but they remain parser/pure-behavior
# checks and are not evidence of native Windows execution.
readonly check_windows_contract_preflight_script_paths=(
    scripts/test-provision-powershell-runtime.sh
    scripts/test-powershell-quality-tools.sh
    scripts/test-powershell-quality-runner.sh
    scripts/test-verify-windows-publication-surface.sh
    scripts/test-bundle-smoke-powershell.sh
    scripts/test-gradle-wrapper-support-powershell.sh
    scripts/test-gradlew-powershell-owner.sh
    scripts/test-gradlew-bat-wrapper.sh
    scripts/test-windows-portable-archive-path-policy.sh
    scripts/test-setup-msvc-dev-cmd.sh
    scripts/test-configure-windows-defender-build-exclusions.sh
    scripts/test-collect-windows-ci-failure-evidence.sh
)

readonly check_stage5_executable_script_paths=(
    scripts/test-run-quality-gates-hygiene.sh
    scripts/test-release-surface-portable-search.sh
    scripts/test-modular-jar-descriptor-self-heal.sh
    scripts/test-devcontainer-workflow.sh
    scripts/test-repo-verification-lock.sh
    scripts/test-ci-release-surface-workflow.sh
    scripts/test-container-workflow-timeout.sh
    scripts/test-promote-container-image.sh
    scripts/test-prepare-release-version.sh
    scripts/test-read-contract-values.sh
    scripts/test-verify-runner-identity.sh
    scripts/check-windows-contract.sh
    scripts/test-bundle-archive-pruning.sh
    scripts/test-bundle-archive-reproducibility.sh
    scripts/test-verify-bundle-archive-contract.sh
    scripts/test-release-smoke-workflow.sh
    scripts/test-release-check-contract.sh
    scripts/test-distribution-freshness-workflow.sh
    scripts/test-verify-release-pr-gate.sh
    scripts/test-release-protocol-pr-diff-fallback.sh
    scripts/test-release-protocol-worktree-handoff.sh
    scripts/test-verify-release-candidate-tag.sh
    scripts/test-verify-release-merge-handoff.sh
    scripts/test-verify-public-container-surface.sh
    scripts/test-gradle-wrapper-support.sh
    scripts/test-gradle-invocation-lease.sh
    scripts/test-jazzer-stale-class-pruning.sh
    scripts/test-jazzer-stale-resource-pruning.sh
    scripts/test-check-monitor-common.sh
    scripts/test-check-monitor-runner.sh
    scripts/test-check-process-support.sh
    scripts/test-check-stage-contract.sh
    scripts/test-check-windows-contract.sh
    scripts/test-python-runtime-support.sh
    scripts/test-operator-help-surfaces.sh
    scripts/test-jazzer-fuzz-all-wrapper.sh
    scripts/test-jazzer-active-wrapper-timeout.sh
    scripts/test-jazzer-verification-wrapper-routing.sh
    scripts/test-jazzer-replay-wrapper.sh
    scripts/test-jazzer-seed-wrapper.sh
    scripts/test-no-product-bigdecimal.sh
    scripts/test-render-sqlite-schema-doc.sh
    scripts/test-source-checkout-launcher.sh
    scripts/test-verify-structural-governance.sh
    scripts/test-publish-github-release.sh
    scripts/test-resolve-release-latest-policy.sh
    scripts/test-verify-github-release.sh
    scripts/test-verify-security-policy-surface.sh
    scripts/test-verify-release-primary-checkout.sh
    scripts/test-verify-release-repo-settings.sh
    scripts/test-configure-release-tag-rulesets.sh
    scripts/test-verify-release-workflow-initiator.sh
    scripts/test-reconcile-release-primary-checkout.sh
    scripts/test-verify-sqlite-runtime-contract.sh
    scripts/test-verify-sqlite-runtime-powershell.sh
    scripts/test-verify-jacoco-artifacts.sh
    scripts/test-verify-build-logic-plugin-jar.sh
    scripts/test-verify-repo-hygiene.sh
    scripts/validate-devcontainer.sh
    scripts/verify-source-checkout-sqlite-runtime.sh
    scripts/verify-direct-java-sqlite-runtime.sh
)

readonly check_stage5_shell_only_script_paths=(
    scripts/verify-release-candidate-tag.sh
    scripts/verify-release-pr-gate.sh
    scripts/verify-release-merge-handoff.sh
)

check_stage_usage_lines() {
    printf '%s\n' \
        '  1. scripts/run-quality-gates.sh (repo hygiene + structural governance + check coverage + included build-logic check)' \
        '  2. jazzer/bin/check' \
        '  3. :cli:bundleCliArchive' \
        '  4. scripts/bundle-smoke.sh (bundle acceptance workflow)' \
        "  5. $(check_stage5_usage_command)" \
        '  6. scripts/docker-smoke.sh (Docker acceptance workflow)'
}

check_stage5_usage_command() {
    printf '%s\n' './scripts/check-release-surface-scripts.sh'
}

check_stage_execute() {
    local stage_id=$1
    local stage_label=$2
    local check_repo_root=$3

    case "${stage_id}" in
        quality-gates)
            run_quality_gate_stage "${stage_id}" "${stage_label}"
            ;;
        jazzer-check)
            run_shell_stage \
                "${stage_id}" \
                "${stage_label}" \
                "${check_repo_root}/jazzer/bin/check" \
                ${gradle_args[@]+"${gradle_args[@]}"}
            ;;
        cli-bundle)
            run_stage "${stage_id}" "${stage_label}" "${check_repo_root}" :cli:bundleCliArchive
            ;;
        bundle-smoke)
            run_shell_stage "${stage_id}" "${stage_label}" "${check_repo_root}/scripts/bundle-smoke.sh"
            ;;
        release-surface-scripts)
            run_shell_stage "${stage_id}" "${stage_label}" \
                "${check_repo_root}/scripts/check-release-surface-scripts.sh"
            ;;
        docker-smoke)
            run_shell_stage "${stage_id}" "${stage_label}" "${check_repo_root}/scripts/docker-smoke.sh"
            ;;
        *)
            die "unsupported fixed stage id from ${stage_contract_script}: ${stage_id}"
            ;;
    esac
}
