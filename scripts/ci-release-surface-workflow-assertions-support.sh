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

assert_manual_jdk_provider_diagnostic() {
    grep -Fq 'java_distribution:' "${workflow_file}" || die \
        "CI workflow no longer exposes its bounded manual JDK-provider diagnostic input"
    grep -Fq "distribution: \${{ github.event_name == 'workflow_dispatch' && inputs.java_distribution || 'zulu' }}" "${workflow_file}" || die \
        "CI workflow no longer keeps Zulu as the standard branch and pull-request JDK provider"
    if ! grep -A9 -F 'java_distribution:' "${workflow_file}" | grep -Fq '          - temurin'; then
        die "CI workflow no longer permits the Temurin diagnostic provider"
    fi
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
