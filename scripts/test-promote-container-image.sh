#!/usr/bin/env bash
# Exercise the public-container promotion state machine against an isolated fake Docker registry.

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
readonly promoter="${script_dir}/promote-container-image.sh"
readonly staging_ref='ghcr.io/example/fingrind-staging'
readonly public_ref='ghcr.io/example/fingrind'
readonly version='9.9.9'
readonly amd64_digest='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
readonly arm64_digest='sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
readonly candidate_digest='sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'
readonly mismatch_digest='sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
readonly stale_latest_digest='sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee'

[[ -x "${promoter}" ]] || die "missing executable public-container promotion state machine"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-promote-container-image.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

cat > "${fixture_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly state_file="${FAKE_DOCKER_STATE_FILE:?}"
readonly operation_log="${FAKE_DOCKER_OPERATION_LOG:?}"

state_get_manifest() {
    local reference=$1

    python3 - "${state_file}" "${reference}" <<'PY'
from __future__ import annotations

import json
from pathlib import Path
import sys

state = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
reference = sys.argv[2]
manifest = state["manifests"].get(reference)
if manifest is None and "@" in reference:
    digest = reference.rsplit("@", 1)[1]
    manifest = next(
        (
            candidate
            for candidate in state["manifests"].values()
            if candidate.get("digest") == digest and "error" not in candidate
        ),
        None,
    )
if manifest is None:
    print(f"ERROR: {reference}: not found", file=sys.stderr)
    raise SystemExit(1)
if "error" in manifest:
    print(f"ERROR: {manifest['error']}", file=sys.stderr)
    raise SystemExit(1)
print(json.dumps(manifest, separators=(",", ":")))
PY
}

state_create_manifest() {
    local target=$1
    local metadata_file=$2
    shift 2

    python3 - "${state_file}" "${target}" "${metadata_file}" "$@" <<'PY'
from __future__ import annotations

import json
from copy import deepcopy
from pathlib import Path
import sys

state_path = Path(sys.argv[1])
target = sys.argv[2]
metadata_path = Path(sys.argv[3])
sources = sys.argv[4:]
state = json.loads(state_path.read_text(encoding="utf-8"))


def resolve(source: str) -> dict[str, object]:
    direct = state["manifests"].get(source)
    if direct is not None:
        return direct
    if "@" in source:
        digest = source.rsplit("@", 1)[1]
        for candidate in state["manifests"].values():
            if candidate.get("digest") == digest and "error" not in candidate:
                return candidate
    raise SystemExit(f"missing source manifest: {source}")


if len(sources) == 2:
    source_manifests = [resolve(source) for source in sources]
    descriptor_union = [
        descriptor
        for source_manifest in source_manifests
        for descriptor in source_manifest["manifests"]
    ]
    if state.get("candidateDescriptorProfile") == "corrupt":
        descriptor_union = descriptor_union[:2]
    result_manifest = {
        "digest": state["candidateDigest"],
        "manifests": descriptor_union,
    }
elif len(sources) == 1 and "@" in sources[0]:
    result_manifest = resolve(sources[0])
else:
    raise SystemExit(f"unsupported imagetools create sources: {sources!r}")

persisted_manifest = result_manifest
if target in state.get("postCreateMismatchTargets", []):
    persisted_manifest = deepcopy(result_manifest)
    persisted_manifest["digest"] = state["mismatchDigest"]
state["manifests"][target] = persisted_manifest
state_path.write_text(json.dumps(state, sort_keys=True), encoding="utf-8")
metadata_path.write_text(
    json.dumps({"containerimage.descriptor": {"digest": result_manifest["digest"]}}),
    encoding="utf-8",
)
PY
}

[[ "${1:-}" == 'buildx' && "${2:-}" == 'imagetools' ]] || {
    printf 'unsupported fake Docker invocation: %s\n' "$*" >&2
    exit 1
}
shift 2

case "${1:-}" in
    inspect)
        shift
        [[ "${1:-}" == '--format' && "${2:-}" == '{{json .Manifest}}' ]] || {
            printf 'unsupported imagetools inspect invocation: %s\n' "$*" >&2
            exit 1
        }
        reference="${3:-}"
        [[ -n "${reference}" ]] || exit 1
        printf 'inspect %s\n' "${reference}" >> "${operation_log}"
        state_get_manifest "${reference}"
        ;;
    create)
        shift
        target=''
        metadata_file=''
        sources=()
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --tag|-t)
                    target="${2:-}"
                    shift 2
                    ;;
                --metadata-file)
                    metadata_file="${2:-}"
                    shift 2
                    ;;
                --)
                    shift
                    sources+=("$@")
                    break
                    ;;
                -*)
                    printf 'unsupported imagetools create option: %s\n' "$1" >&2
                    exit 1
                    ;;
                *)
                    sources+=("$1")
                    shift
                    ;;
            esac
        done
        [[ -n "${target}" && -n "${metadata_file}" && ${#sources[@]} -gt 0 ]] || {
            printf 'incomplete imagetools create invocation\n' >&2
            exit 1
        }
        printf 'create %s <- %s\n' "${target}" "${sources[*]}" >> "${operation_log}"
        state_create_manifest "${target}" "${metadata_file}" "${sources[@]}"
        ;;
    *)
        printf 'unsupported fake Docker imagetools command: %s\n' "$1" >&2
        exit 1
        ;;
esac
EOF
chmod +x "${fixture_root}/bin/docker"

write_state() {
    local destination=$1
    local exact_state=$2
    local latest_state=$3
    local candidate_state=$4
    local source_profile=${5:-stable}
    local candidate_descriptor_profile=${6:-complete}
    local post_create_mismatch_target=${7:-}

    jq -n \
        --arg staging_ref "${staging_ref}" \
        --arg public_ref "${public_ref}" \
        --arg version "${version}" \
        --arg amd64_digest "${amd64_digest}" \
        --arg arm64_digest "${arm64_digest}" \
        --arg candidate_digest "${candidate_digest}" \
        --arg mismatch_digest "${mismatch_digest}" \
        --arg exact_state "${exact_state}" \
        --arg latest_state "${latest_state}" \
        --arg candidate_state "${candidate_state}" \
        --arg source_profile "${source_profile}" \
        --arg candidate_descriptor_profile "${candidate_descriptor_profile}" \
        --arg post_create_mismatch_target "${post_create_mismatch_target}" \
        '
        def descriptor($digest; $os; $architecture; $annotations):
          {
            mediaType: "application/vnd.oci.image.manifest.v1+json",
            digest: $digest,
            size: 1,
            annotations: $annotations,
            platform: {os: $os, architecture: $architecture}
          };
        def complete_candidate:
          {
            digest: $candidate_digest,
            manifests: [
              descriptor("sha256:1111111111111111111111111111111111111111111111111111111111111111"; "linux"; "amd64"; {}),
              descriptor("sha256:2222222222222222222222222222222222222222222222222222222222222222"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"}),
              descriptor("sha256:3333333333333333333333333333333333333333333333333333333333333333"; "linux"; "arm64"; {}),
              descriptor("sha256:4444444444444444444444444444444444444444444444444444444444444444"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
            ]
          };
        def candidate_index:
          if $candidate_descriptor_profile == "corrupt" then
            complete_candidate | .manifests = .manifests[0:2]
          else complete_candidate
          end;
        def corrupt_candidate_index:
          complete_candidate | .manifests = .manifests[0:2];
        def selected_source_indexes:
          if $source_profile == "divergent" then
            {
              x86: {
                digest: "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                manifests: [
                  descriptor("sha256:5555555555555555555555555555555555555555555555555555555555555555"; "linux"; "amd64"; {}),
                  descriptor("sha256:6666666666666666666666666666666666666666666666666666666666666666"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
                ]
              },
              arm: {
                digest: "sha256:9999999999999999999999999999999999999999999999999999999999999999",
                manifests: [
                  descriptor("sha256:7777777777777777777777777777777777777777777777777777777777777777"; "linux"; "arm64"; {}),
                  descriptor("sha256:8888888888888888888888888888888888888888888888888888888888888888"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
                ]
              }
            }
          elif $source_profile == "invalid-platform" then
            {
              x86: {
                digest: $amd64_digest,
                manifests: [
                  descriptor("sha256:1111111111111111111111111111111111111111111111111111111111111111"; "linux"; "amd64"; {}),
                  descriptor("sha256:2222222222222222222222222222222222222222222222222222222222222222"; "linux"; "amd64"; {})
                ]
              },
              arm: {
                digest: $arm64_digest,
                manifests: [
                  descriptor("sha256:3333333333333333333333333333333333333333333333333333333333333333"; "linux"; "arm64"; {}),
                  descriptor("sha256:4444444444444444444444444444444444444444444444444444444444444444"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
                ]
              }
            }
          elif $source_profile == "wrong-extra-platform" then
            {
              x86: {
                digest: $amd64_digest,
                manifests: [
                  descriptor("sha256:1111111111111111111111111111111111111111111111111111111111111111"; "linux"; "amd64"; {}),
                  descriptor("sha256:2222222222222222222222222222222222222222222222222222222222222222"; "linux"; "arm64"; {})
                ]
              },
              arm: {
                digest: $arm64_digest,
                manifests: [
                  descriptor("sha256:3333333333333333333333333333333333333333333333333333333333333333"; "linux"; "arm64"; {}),
                  descriptor("sha256:4444444444444444444444444444444444444444444444444444444444444444"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
                ]
              }
            }
          else
            {
              x86: {
                digest: $amd64_digest,
                manifests: [
                  descriptor("sha256:1111111111111111111111111111111111111111111111111111111111111111"; "linux"; "amd64"; {}),
                  descriptor("sha256:2222222222222222222222222222222222222222222222222222222222222222"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
                ]
              },
              arm: {
                digest: $arm64_digest,
                manifests: [
                  descriptor("sha256:3333333333333333333333333333333333333333333333333333333333333333"; "linux"; "arm64"; {}),
                  descriptor("sha256:4444444444444444444444444444444444444444444444444444444444444444"; "unknown"; "unknown"; {"vnd.docker.reference.type": "attestation-manifest"})
                ]
              }
            }
          end;
        def indexed($digest):
          if $digest == $candidate_digest then candidate_index
          else {digest: $digest, manifests: []}
          end;
        {
          candidateDigest: $candidate_digest,
          mismatchDigest: $mismatch_digest,
          candidateDescriptorProfile: $candidate_descriptor_profile,
          postCreateMismatchTargets: (
            if $post_create_mismatch_target == "exact" then
              [$public_ref + ":" + $version]
            elif $post_create_mismatch_target == "latest" then
              [$public_ref + ":latest"]
            else []
            end
          ),
          manifests: {
            ($staging_ref + ":" + $version + "-linux-x86_64"): selected_source_indexes.x86,
            ($staging_ref + ":" + $version + "-linux-aarch64"): selected_source_indexes.arm
          }
        }
        | if $exact_state == "" then .
          elif $exact_state == "error" then
            .manifests[$public_ref + ":" + $version] = {error: "registry transport failed"}
          elif $exact_state == "corrupt" then
            .manifests[$public_ref + ":" + $version] = corrupt_candidate_index
          else .manifests[$public_ref + ":" + $version] = indexed($exact_state)
          end
        | if $latest_state == "" then .
          elif $latest_state == "corrupt" then
            .manifests[$public_ref + ":latest"] = corrupt_candidate_index
          else .manifests[$public_ref + ":latest"] = indexed($latest_state)
          end
        | if $candidate_state == "" then .
          elif $candidate_state == "error" then
            .manifests[$staging_ref + ":" + $version + "-candidate"] = {error: "registry transport failed"}
          elif $candidate_state == "malformed" then
            .manifests[$staging_ref + ":" + $version + "-candidate"] = {digest: "not-a-digest", manifests: []}
          else .manifests[$staging_ref + ":" + $version + "-candidate"] = indexed($candidate_state)
          end
        | if $source_profile == "missing-x86" then
            del(.manifests[$staging_ref + ":" + $version + "-linux-x86_64"])
          else .
          end
        ' > "${destination}"
}

run_promotion() {
    local state_file=$1
    local operation_log=$2
    local mark_latest=$3

    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_DOCKER_STATE_FILE="${state_file}" \
        FAKE_DOCKER_OPERATION_LOG="${operation_log}" \
        FINGRIND_CONTAINER_PROMOTION_VERIFY_RETRIES=1 \
        FINGRIND_CONTAINER_PROMOTION_VERIFY_DELAY_SECONDS=0 \
        bash "${promoter}" "${staging_ref}" "${public_ref}" "${version}" "${mark_latest}"
}

assert_digest() {
    local state_file=$1
    local reference=$2
    local expected_digest=$3

    jq -e --arg reference "${reference}" --arg expected_digest "${expected_digest}" \
        '.manifests[$reference].digest == $expected_digest' "${state_file}" >/dev/null || die \
        "expected ${reference} to resolve to ${expected_digest}"
}

assert_no_create() {
    local operation_log=$1
    local reference=$2

    if grep -Fq "create ${reference} <-" "${operation_log}"; then
        die "promotion rewrote immutable public tag ${reference}"
    fi
}

assert_no_inspect() {
    local operation_log=$1
    local reference=$2

    if grep -Fq "inspect ${reference}" "${operation_log}"; then
        die "promotion unexpectedly reread mutable staging state ${reference} after accepting a retained candidate"
    fi
}

assert_create_precedes() {
    local operation_log=$1
    local first_reference=$2
    local second_reference=$3
    local first_line
    local second_line

    first_line="$(grep -n -F "create ${first_reference} <-" "${operation_log}" | head -n 1 | cut -d: -f1)"
    second_line="$(grep -n -F "create ${second_reference} <-" "${operation_log}" | head -n 1 | cut -d: -f1)"
    [[ -n "${first_line}" && -n "${second_line}" && ${first_line} -lt ${second_line} ]] || die \
        "expected durable candidate ${first_reference} to be created before public exact ${second_reference}"
}

absent_dir="${fixture_root}/absent"
mkdir -p "${absent_dir}"
write_state "${absent_dir}/state.json" '' '' ''
: > "${absent_dir}/operations.log"
run_promotion "${absent_dir}/state.json" "${absent_dir}/operations.log" false
assert_digest "${absent_dir}/state.json" "${staging_ref}:${version}-candidate" "${candidate_digest}"
assert_digest "${absent_dir}/state.json" "${public_ref}:${version}" "${candidate_digest}"
assert_no_create "${absent_dir}/operations.log" "${public_ref}:latest"
assert_create_precedes \
    "${absent_dir}/operations.log" \
    "${staging_ref}:${version}-candidate" \
    "${public_ref}:${version}"
grep -Fq "create ${public_ref}:${version} <- ${staging_ref}@${candidate_digest}" \
    "${absent_dir}/operations.log" || die \
    "absent public tag was not promoted from the immutable staging candidate digest"

retained_candidate_dir="${fixture_root}/retained-candidate"
mkdir -p "${retained_candidate_dir}"
write_state "${retained_candidate_dir}/state.json" '' '' "${candidate_digest}" divergent
: > "${retained_candidate_dir}/operations.log"
run_promotion "${retained_candidate_dir}/state.json" "${retained_candidate_dir}/operations.log" false
assert_digest "${retained_candidate_dir}/state.json" "${public_ref}:${version}" "${candidate_digest}"
assert_no_create "${retained_candidate_dir}/operations.log" "${staging_ref}:${version}-candidate"
assert_no_inspect "${retained_candidate_dir}/operations.log" "${staging_ref}:${version}-linux-x86_64"
assert_no_inspect "${retained_candidate_dir}/operations.log" "${staging_ref}:${version}-linux-aarch64"

matching_dir="${fixture_root}/matching"
mkdir -p "${matching_dir}"
write_state "${matching_dir}/state.json" "${candidate_digest}" "${candidate_digest}" "${candidate_digest}" divergent
: > "${matching_dir}/operations.log"
run_promotion "${matching_dir}/state.json" "${matching_dir}/operations.log" true
assert_no_create "${matching_dir}/operations.log" "${staging_ref}:${version}-candidate"
assert_no_create "${matching_dir}/operations.log" "${public_ref}:${version}"
assert_no_create "${matching_dir}/operations.log" "${public_ref}:latest"
assert_no_inspect "${matching_dir}/operations.log" "${staging_ref}:${version}-linux-x86_64"
assert_no_inspect "${matching_dir}/operations.log" "${staging_ref}:${version}-linux-aarch64"

mismatch_dir="${fixture_root}/mismatch"
mkdir -p "${mismatch_dir}"
write_state "${mismatch_dir}/state.json" "${mismatch_digest}" "${stale_latest_digest}" "${candidate_digest}"
: > "${mismatch_dir}/operations.log"
if run_promotion "${mismatch_dir}/state.json" "${mismatch_dir}/operations.log" true; then
    die "promotion accepted a staging candidate that disagreed with an immutable public exact tag"
fi
assert_digest "${mismatch_dir}/state.json" "${public_ref}:${version}" "${mismatch_digest}"
assert_digest "${mismatch_dir}/state.json" "${public_ref}:latest" "${stale_latest_digest}"
assert_no_create "${mismatch_dir}/operations.log" "${public_ref}:${version}"
assert_no_create "${mismatch_dir}/operations.log" "${public_ref}:latest"

candidate_missing_dir="${fixture_root}/candidate-missing"
mkdir -p "${candidate_missing_dir}"
write_state "${candidate_missing_dir}/state.json" "${candidate_digest}" "${stale_latest_digest}" ''
: > "${candidate_missing_dir}/operations.log"
if run_promotion "${candidate_missing_dir}/state.json" "${candidate_missing_dir}/operations.log" true; then
    die "promotion accepted an existing public exact tag with no retained candidate provenance"
fi
assert_digest "${candidate_missing_dir}/state.json" "${public_ref}:${version}" "${candidate_digest}"
assert_digest "${candidate_missing_dir}/state.json" "${public_ref}:latest" "${stale_latest_digest}"
assert_no_create "${candidate_missing_dir}/operations.log" "${staging_ref}:${version}-candidate"
assert_no_create "${candidate_missing_dir}/operations.log" "${public_ref}:latest"

latest_dir="${fixture_root}/latest"
mkdir -p "${latest_dir}"
write_state "${latest_dir}/state.json" "${candidate_digest}" "${stale_latest_digest}" "${candidate_digest}"
: > "${latest_dir}/operations.log"
run_promotion "${latest_dir}/state.json" "${latest_dir}/operations.log" true
assert_no_create "${latest_dir}/operations.log" "${public_ref}:${version}"
assert_digest "${latest_dir}/state.json" "${public_ref}:latest" "${candidate_digest}"
grep -Fq "create ${public_ref}:latest <- ${public_ref}@${candidate_digest}" \
    "${latest_dir}/operations.log" || die \
    "latest did not consume the accepted immutable public exact digest"

inspection_error_dir="${fixture_root}/inspection-error"
mkdir -p "${inspection_error_dir}"
write_state "${inspection_error_dir}/state.json" error "${stale_latest_digest}" "${candidate_digest}"
: > "${inspection_error_dir}/operations.log"
if run_promotion "${inspection_error_dir}/state.json" "${inspection_error_dir}/operations.log" true; then
    die "promotion treated an indeterminate manifest inspection as an absent public tag"
fi
assert_no_create "${inspection_error_dir}/operations.log" "${public_ref}:${version}"
assert_no_create "${inspection_error_dir}/operations.log" "${public_ref}:latest"

invalid_platform_dir="${fixture_root}/invalid-platform"
mkdir -p "${invalid_platform_dir}"
write_state "${invalid_platform_dir}/state.json" '' '' '' invalid-platform
: > "${invalid_platform_dir}/operations.log"
if run_promotion "${invalid_platform_dir}/state.json" "${invalid_platform_dir}/operations.log" false; then
    die "promotion accepted a staging source index with duplicate linux/amd64 descriptors"
fi
assert_no_create "${invalid_platform_dir}/operations.log" "${staging_ref}:${version}-candidate"
assert_no_create "${invalid_platform_dir}/operations.log" "${public_ref}:${version}"

wrong_extra_platform_dir="${fixture_root}/wrong-extra-platform"
mkdir -p "${wrong_extra_platform_dir}"
write_state "${wrong_extra_platform_dir}/state.json" '' '' '' wrong-extra-platform
: > "${wrong_extra_platform_dir}/operations.log"
if run_promotion "${wrong_extra_platform_dir}/state.json" "${wrong_extra_platform_dir}/operations.log" false; then
    die "promotion accepted a linux/arm64 runtime descriptor in the x86-only staging index"
fi
assert_no_create "${wrong_extra_platform_dir}/operations.log" "${staging_ref}:${version}-candidate"
assert_no_create "${wrong_extra_platform_dir}/operations.log" "${public_ref}:${version}"

corrupt_candidate_dir="${fixture_root}/corrupt-candidate"
mkdir -p "${corrupt_candidate_dir}"
write_state "${corrupt_candidate_dir}/state.json" '' '' '' stable corrupt
: > "${corrupt_candidate_dir}/operations.log"
if run_promotion "${corrupt_candidate_dir}/state.json" "${corrupt_candidate_dir}/operations.log" false; then
    die "promotion accepted a candidate index that dropped a staged provenance descriptor"
fi
assert_no_create "${corrupt_candidate_dir}/operations.log" "${public_ref}:${version}"

retained_incomplete_candidate_dir="${fixture_root}/retained-incomplete-candidate"
mkdir -p "${retained_incomplete_candidate_dir}"
write_state \
    "${retained_incomplete_candidate_dir}/state.json" \
    '' \
    '' \
    "${candidate_digest}" \
    stable \
    corrupt
: > "${retained_incomplete_candidate_dir}/operations.log"
if run_promotion \
    "${retained_incomplete_candidate_dir}/state.json" \
    "${retained_incomplete_candidate_dir}/operations.log" \
    false; then
    die "promotion accepted an incomplete retained candidate before creating the immutable public exact tag"
fi
assert_no_create "${retained_incomplete_candidate_dir}/operations.log" "${public_ref}:${version}"

malformed_candidate_dir="${fixture_root}/malformed-candidate"
mkdir -p "${malformed_candidate_dir}"
write_state "${malformed_candidate_dir}/state.json" '' '' malformed
: > "${malformed_candidate_dir}/operations.log"
if run_promotion "${malformed_candidate_dir}/state.json" "${malformed_candidate_dir}/operations.log" false; then
    die "promotion accepted a retained candidate with a malformed descriptor digest"
fi
assert_no_create "${malformed_candidate_dir}/operations.log" "${public_ref}:${version}"

incomplete_exact_dir="${fixture_root}/incomplete-exact"
mkdir -p "${incomplete_exact_dir}"
write_state \
    "${incomplete_exact_dir}/state.json" \
    corrupt \
    "${stale_latest_digest}" \
    "${candidate_digest}"
: > "${incomplete_exact_dir}/operations.log"
if run_promotion "${incomplete_exact_dir}/state.json" "${incomplete_exact_dir}/operations.log" true; then
    die "promotion accepted a public exact tag that lacks its required arm64 runtime descriptor"
fi
assert_no_create "${incomplete_exact_dir}/operations.log" "${public_ref}:latest"

incomplete_latest_dir="${fixture_root}/incomplete-latest"
mkdir -p "${incomplete_latest_dir}"
write_state \
    "${incomplete_latest_dir}/state.json" \
    "${candidate_digest}" \
    corrupt \
    "${candidate_digest}"
: > "${incomplete_latest_dir}/operations.log"
if run_promotion "${incomplete_latest_dir}/state.json" "${incomplete_latest_dir}/operations.log" true; then
    die "promotion accepted a latest tag that lacks its required arm64 runtime descriptor"
fi
assert_no_create "${incomplete_latest_dir}/operations.log" "${public_ref}:latest"

missing_source_dir="${fixture_root}/missing-source"
mkdir -p "${missing_source_dir}"
write_state "${missing_source_dir}/state.json" '' '' '' missing-x86
: > "${missing_source_dir}/operations.log"
if run_promotion "${missing_source_dir}/state.json" "${missing_source_dir}/operations.log" false; then
    die "promotion accepted a missing staged x86 source index"
fi
assert_no_create "${missing_source_dir}/operations.log" "${staging_ref}:${version}-candidate"
assert_no_create "${missing_source_dir}/operations.log" "${public_ref}:${version}"

post_create_exact_mismatch_dir="${fixture_root}/post-create-exact-mismatch"
mkdir -p "${post_create_exact_mismatch_dir}"
write_state \
    "${post_create_exact_mismatch_dir}/state.json" \
    '' \
    '' \
    '' \
    stable \
    complete \
    exact
: > "${post_create_exact_mismatch_dir}/operations.log"
if run_promotion \
    "${post_create_exact_mismatch_dir}/state.json" \
    "${post_create_exact_mismatch_dir}/operations.log" \
    true; then
    die "promotion accepted an exact public tag whose post-create registry digest diverged from the candidate"
fi
assert_digest \
    "${post_create_exact_mismatch_dir}/state.json" \
    "${public_ref}:${version}" \
    "${mismatch_digest}"
assert_no_create "${post_create_exact_mismatch_dir}/operations.log" "${public_ref}:latest"

post_create_latest_mismatch_dir="${fixture_root}/post-create-latest-mismatch"
mkdir -p "${post_create_latest_mismatch_dir}"
write_state \
    "${post_create_latest_mismatch_dir}/state.json" \
    "${candidate_digest}" \
    '' \
    "${candidate_digest}" \
    stable \
    complete \
    latest
: > "${post_create_latest_mismatch_dir}/operations.log"
if run_promotion \
    "${post_create_latest_mismatch_dir}/state.json" \
    "${post_create_latest_mismatch_dir}/operations.log" \
    true; then
    die "promotion accepted a latest public tag whose post-create registry digest diverged from the accepted exact tag"
fi
assert_digest \
    "${post_create_latest_mismatch_dir}/state.json" \
    "${public_ref}:latest" \
    "${mismatch_digest}"

printf 'public container promotion regression: success\n'
