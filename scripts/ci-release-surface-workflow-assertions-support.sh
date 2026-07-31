#!/usr/bin/env bash
# Shared workflow-shape assertions for the release CI surface contract.

workflow_job_block_from() {
    local source_file=$1
    local job_name=$2
    awk -v job_name="${job_name}" '
        $0 == "  " job_name ":" {
            active = 1
        }
        active {
            if ($0 ~ /^  [A-Za-z0-9_-]+:$/ && $0 != "  " job_name ":") {
                exit
            }
            print
        }
    ' "${source_file}"
}

workflow_job_block() {
    workflow_job_block_from "${workflow_file}" "$1"
}

workflow_step_block() {
    local job_block=$1
    local step_name=$2

    printf '%s\n' "${job_block}" | awk -v step_name="${step_name}" '
        $0 == "      - name: " step_name {
            active = 1
        }
        active {
            if ($0 ~ /^      - name: / && $0 != "      - name: " step_name) {
                exit
            }
            print
        }
    '
}

workflow_literal_matrix_rows() {
    awk '
        $0 == "        include:" {
            inside_matrix = 1
            next
        }
        inside_matrix && $0 == "    steps:" {
            emit_row()
            exit
        }
        inside_matrix && /^          - runner: / {
            emit_row()
            runner = $0
            sub(/^          - runner: /, "", runner)
            next
        }
        inside_matrix && /^            classifier: / {
            classifier = $0
            sub(/^            classifier: /, "", classifier)
            next
        }
        inside_matrix && /^            operatingSystemId: / {
            operating_system_id = $0
            sub(/^            operatingSystemId: /, "", operating_system_id)
            next
        }
        inside_matrix && /^            architectureId: / {
            architecture_id = $0
            sub(/^            architectureId: /, "", architecture_id)
            next
        }
        function emit_row() {
            if (runner != "") {
                print runner "|" classifier "|" operating_system_id "|" architecture_id
                runner = ""
                classifier = ""
                operating_system_id = ""
                architecture_id = ""
            }
        }
        END {
            emit_row()
        }
    '
}

assert_literal_runner_matrix() {
    local label=$1
    local job_block=$2
    local expected_rows=$3
    local actual_rows

    if grep -Fq 'fromJson(' <<< "${job_block}"; then
        die "${label} still derives a runner matrix from workflow-output data"
    fi
    if [[ "$(grep -Fxc '    runs-on: ${{ matrix.runner }}' <<< "${job_block}")" -ne 1 ]]; then
        die "${label} no longer routes its literal runner matrix through exactly one matrix.runner runs-on owner"
    fi
    actual_rows="$(printf '%s\n' "${job_block}" | workflow_literal_matrix_rows | LC_ALL=C sort)"
    expected_rows="$(printf '%s\n' "${expected_rows}" | LC_ALL=C sort)"
    [[ "${actual_rows}" == "${expected_rows}" ]] || die \
        "${label} no longer declares exactly the approved literal target/runner matrix"
}

workflow_job_permission_rows() {
    awk '
        $0 == "    permissions:" {
            in_permissions = 1
            next
        }
        in_permissions && /^      [A-Za-z0-9_-]+: (read|write|none)$/ {
            permission = $0
            sub(/^      /, "", permission)
            print permission
            next
        }
        in_permissions {
            exit
        }
    '
}

assert_job_permissions() {
    local label=$1
    local job_block=$2
    local expected_rows=$3
    local actual_rows

    actual_rows="$(printf '%s\n' "${job_block}" | workflow_job_permission_rows | LC_ALL=C sort)"
    expected_rows="$(printf '%s\n' "${expected_rows}" | LC_ALL=C sort)"
    [[ -n "${actual_rows}" ]] || die "${label} has no explicit permissions block"
    [[ "${actual_rows}" == "${expected_rows}" ]] || die \
        "${label} permissions are not the audited least-privilege set: ${actual_rows}"
}

assert_ci_required_artifact_owners() {
    [[ -f "${workflow_file}" ]] || die "missing CI workflow at ${workflow_file}"
    [[ ! -e "${retired_wrapper_workflow}" ]] || die \
        "wrapper validation still lives outside the release-blocking CI graph"
    [[ -f "${release_publication_contract_reader}" ]] || die \
        "missing release-publication contract reader at ${release_publication_contract_reader}"
    [[ -f "${developer_doc}" ]] || die "missing developer reference at ${developer_doc}"
    [[ -f "${developer_ci_doc}" ]] || die "missing CI reference at ${developer_ci_doc}"
    [[ -f "${developer_gradle_doc}" ]] || die "missing Gradle reference at ${developer_gradle_doc}"
    [[ -f "${developer_release_publication_doc}" ]] || die \
        "missing release-publication reference at ${developer_release_publication_doc}"
    [[ -f "${release_workflow_file}" ]] || die "missing release workflow at ${release_workflow_file}"
}

assert_devcontainer_change_inputs() {
    local devcontainer_changes_job=$1
    local devcontainer_input

    for devcontainer_input in \
        '.dockerignore' \
        'scripts/provision-powershell-runtime.py' \
        'scripts/powershell_provisioning_cli.py' \
        'scripts/powershell_provisioning_tree.py' \
        'scripts/powershell_runtime.py' \
        'scripts/powershell_runtime_archives.py' \
        'scripts/powershell_runtime_cache.py' \
        'scripts/powershell_runtime_installation.py' \
        'scripts/powershell_runtime_metadata.py' \
        'scripts/powershell_runtime_models.py' \
        'gradle/fingrind-build.properties'; do
        grep -Fq "'${devcontainer_input}'" <<< "${devcontainer_changes_job}" || die \
            "devcontainer change detection no longer covers ${devcontainer_input}"
    done
}

assert_ci_bootstrap_and_windows_publication_contract() {
    grep -Fq 'Run the canonical root verification gate' "${workflow_file}" || die \
        "CI workflow no longer advertises the canonical root verification gate"
    grep -Fq './check.sh --no-daemon --console=plain' "${workflow_file}" || die \
        "CI workflow no longer runs the canonical root verification gate"
    grep -Fq 'wrapper-validation:' "${workflow_file}" || die \
        "CI workflow no longer owns Gradle wrapper validation"
    grep -Fq 'name: Gradle wrapper validation' "${workflow_file}" || die \
        "CI workflow no longer gives wrapper validation one explicit owner"
    grep -Fq 'gradle/actions/wrapper-validation@' "${workflow_file}" || die \
        "CI workflow no longer verifies the checked-in Gradle wrapper"
    grep -Fq 'fingrindUvVersion=' "${workflow_file}" || die \
        "CI workflow no longer resolves the pinned uv launcher version from build metadata"
    grep -Fq 'fingrindZuluVersion=' "${workflow_file}" || die \
        "CI workflow no longer resolves the exact Zulu release version from build metadata"
    grep -Fq 'ORG_GRADLE_PROJECT_fingrindUvExecutable' "${workflow_file}" || die \
        "CI workflow no longer exports the pinned uv launcher path for Gradle-owned Python tool tasks"
    grep -Fq 'sysconfig.get_path' "${workflow_file}" || die \
        "CI workflow no longer resolves the uv launcher scripts path through Python sysconfig"
    grep -Fq 'Prove canonical attestation codec determinism on Unix' "${workflow_file}" || die \
        "CI workflow no longer proves canonical attestation bytes on every published Unix bundle target"
    if ! grep -A8 -F 'Prove canonical attestation codec determinism on Unix' "${workflow_file}" | \
        grep -Fq -- "--tests 'dev.erst.fingrind.core.attestation.*'"; then
        die \
            "CI workflow no longer runs the canonical attestation codec conformance suite on Unix targets"
    fi
    for native_proof in \
        'Windows runner identity verification' \
        'Windows build-logic verification' \
        'Windows attestation codec verification' \
        'Windows deep Unicode SQLite path verification' \
        'Windows direct-Java SQLite runtime verification' \
        'Windows source-checkout SQLite runtime verification' \
        'Windows CLI bundle build' \
        'Windows CLI bundle smoke verification'; do
        grep -Fq "${native_proof}" "${windows_publication_verifier}" || die \
            "shared Windows publication verifier no longer owns ${native_proof}"
    done
    grep -Fq ':sqlite:test' "${windows_publication_verifier}" || die \
        "shared Windows publication verifier no longer runs the SQLite test surface"
    grep -Fq 'SqliteNativeOpenAndRekeyTest.openCreatesAndReopensAProtectedBookAtADeepUnicodePath' \
        "${windows_publication_verifier}" || die \
        "shared Windows publication verifier no longer exercises a deep Unicode protected-book path"
    grep -Fq 'Get-FinGrindWindowsPublicationPlan' "${windows_publication_verifier}" || die \
        "native Windows publication adapter no longer delegates artifact policy to the filesystem adapter"
    grep -Fq 'windows_publication_policy.py' "${windows_publication_verifier}" || die \
        "native Windows publication adapter no longer admits the cross-platform policy owner"
    if grep -Fq 'ReportedCliBuildDirectory' "${windows_publication_verifier}" || \
        grep -Fq 'ReportedCliBuildDirectory' "${windows_publication_support}" || \
        rg -Fq 'ReportedCliBuildDirectory' \
            "${windows_publication_policy}" \
            "${windows_publication_plan_policy}" \
            "${windows_publication_manifest_policy}" \
            "${windows_publication_protocol_policy}" \
            "${windows_publication_policy_boundary}"; then
        die "shared Windows publication policy still lets a target checkout select its build directory"
    fi
    grep -Fq 'windows_publication_policy_protocol' "${windows_publication_policy}" || die \
        "isolated Windows publication entrypoint no longer delegates to the protocol owner"
    grep -Fq 'build_publication_plan' "${windows_publication_plan_policy}" || die \
        "cross-platform Windows publication policy no longer derives the canonical target cli/build directory"
    grep -Fq 'must not traverse a reparse point' "${windows_publication_support}" || die \
        "Windows filesystem adapter no longer rejects reparse-point artifact or output paths"
    grep -Fq 'fingrind-{project_version}-{normalized_classifier}.zip' "${windows_publication_plan_policy}" || die \
        "cross-platform Windows publication policy no longer derives the canonical archive name from target metadata"
    grep -Fq 'attestation codec conformance suite on every target' "${developer_doc}" || die \
        "developer reference no longer describes the five-platform attestation codec proof"
    for powershell_doc in \
        "${developer_ci_doc}" \
        "${developer_gradle_doc}" \
        "${developer_release_publication_doc}"; do
        grep -Fq "PowerShell \`${required_pwsh_version}\`" "${powershell_doc}" || die \
            "PowerShell documentation no longer states the exact metadata-pinned runtime in ${powershell_doc}"
    done
}
