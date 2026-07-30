#!/usr/bin/env bash
# Keep Windows filesystem rules in the one host-independent build-logic policy owner.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_script_dir() {
    local source_path="${BASH_SOURCE[0]}"
    while [[ -h "${source_path}" ]]; do
        local source_dir
        source_dir="$(cd -P -- "$(dirname -- "${source_path}")" && pwd)"
        source_path="$(readlink "${source_path}")"
        if [[ "${source_path}" != /* ]]; then
            source_path="${source_dir}/${source_path}"
        fi
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly build_logic_directory="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic"
readonly policy_source="${build_logic_directory}/WindowsPortableArchivePathPolicy.kt"
readonly staging_layout_source="${build_logic_directory}/BundleStagingLayout.kt"
readonly bundle_target_source="${build_logic_directory}/DistributionBundleContractTypes.kt"
readonly archive_task_source="${build_logic_directory}/CliBundleArchiveTasks.kt"
readonly archive_member_validation_task_source="${build_logic_directory}/ValidateBundleArchiveMembersTask.kt"
readonly distribution_plugin_source="${build_logic_directory}/FinGrindCliDistributionPlugin.kt"
readonly timestamp_normalization_source="${build_logic_directory}/NormalizeBundleFileTimestampsTask.kt"

[[ -f "${policy_source}" ]] || die "missing Windows-portable archive-path policy at ${policy_source}"
[[ -f "${staging_layout_source}" ]] || die "missing bundle staging layout at ${staging_layout_source}"
[[ -f "${bundle_target_source}" ]] || die "missing bundle target contract at ${bundle_target_source}"
[[ -f "${archive_task_source}" ]] || die "missing CLI archive task registration at ${archive_task_source}"
[[ -f "${archive_member_validation_task_source}" ]] || die \
    "missing staged archive-member validation task at ${archive_member_validation_task_source}"
[[ -f "${distribution_plugin_source}" ]] || die \
    "missing CLI distribution plugin at ${distribution_plugin_source}"
[[ -f "${timestamp_normalization_source}" ]] || die \
    "missing bundle timestamp normalizer at ${timestamp_normalization_source}"

policy_owner_files=()
while IFS= read -r policy_owner_file; do
    policy_owner_files+=("${policy_owner_file}")
done < <(
    rg -l --glob '*.kt' \
        'WINDOWS_FORBIDDEN_CHARACTERS|windowsReservedDeviceBaseNames|isWindowsForbiddenCodePoint|archiveMembersCollide|internal enum class PortableArchiveMemberKind' \
        "${build_logic_directory}" || true
)
if [[ ${#policy_owner_files[@]} -ne 1 || "${policy_owner_files[0]}" != "${policy_source}" ]]; then
    die 'Windows portable archive-path component rules must have exactly one build-logic owner'
fi

grep -Fq 'WindowsPortableArchivePathPolicy.requireComponent' "${staging_layout_source}" || die \
    'bundle staging no longer admits derived bundle-root and archive components through the Windows policy'
grep -Fq 'WindowsPortableArchivePathPolicy.requireNoCaseInsensitiveArchivePathCollisions' \
    "${staging_layout_source}" || die \
    'bundle staging no longer rejects Windows case-insensitive archive-path collisions through the policy'
grep -Fq 'WindowsPortableArchivePathPolicy.requireComponent' "${bundle_target_source}" || die \
    'bundle target construction no longer admits classifiers through the Windows policy'
grep -Fq 'WindowsPortableArchivePathPolicy.requireRelativeArchivePath' "${bundle_target_source}" || die \
    'bundle target construction no longer admits launcher paths through the Windows policy'
grep -Fq 'WindowsPortableArchivePathPolicy.requireFileName' "${bundle_target_source}" || die \
    'bundle target construction no longer admits native library leaves through the Windows policy'
grep -Fq 'WindowsPortableArchivePathPolicy.requirePortableArchiveMembers' \
    "${archive_member_validation_task_source}" || die \
    'staged archive-member validation no longer delegates portable member admission to the Windows policy'
grep -Fq 'NOFOLLOW_LINKS' "${archive_member_validation_task_source}" || die \
    'staged archive-member validation no longer classifies links without following them'
grep -Fq '@get:Internal' "${archive_member_validation_task_source}" || die \
    'Gradle may snapshot the staged archive tree before the no-follow member admission runs'
grep -Fq 'PortableArchiveMemberKind.SYMBOLIC_LINK' "${archive_member_validation_task_source}" || die \
    'staged archive-member validation no longer distinguishes symbolic links from ordinary files'
grep -Fq 'bundleArchiveMemberValidationTask' "${archive_task_source}" || die \
    'CLI archive tasks no longer accept the staged archive-member validation dependency'
grep -Fq 'dependsOn(bundleArchiveMemberValidationTask)' "${archive_task_source}" || die \
    'CLI archive tasks no longer depend directly on staged archive-member validation'
grep -Fq 'ValidateBundleArchiveMembersTask' "${distribution_plugin_source}" || die \
    'CLI distribution no longer registers staged archive-member validation'
grep -Fq 'bundleArchiveMemberValidationTask = validateCliBundleArchiveMembers' \
    "${distribution_plugin_source}" || die \
    'CLI archive registration no longer receives its direct staged archive-member validation dependency'
grep -Fq 'NOFOLLOW_LINKS' "${timestamp_normalization_source}" || die \
    'bundle timestamp normalization no longer avoids following staged links'

printf 'Windows-portable archive-path policy regression: success\n'
