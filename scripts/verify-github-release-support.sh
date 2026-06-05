#!/usr/bin/env bash
# Shared helpers for verifying the published GitHub release surface for a version tag.

verify_github_release_die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

verify_github_release_record_failure() {
    VERIFY_GITHUB_RELEASE_LAST_FAILURE_REASON=$1
    return 1
}

verify_github_release_resolve_repository_slug() {
    if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
        printf '%s\n' "${GITHUB_REPOSITORY}"
        return
    fi
    gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null
}

verify_github_release_resolve_asset_names() {
    FINGRIND_RELEASE_PLAN_JSON="${VERIFY_GITHUB_RELEASE_PLAN_JSON}" python3 - <<'PY'
import json
import os

for asset_name in json.loads(os.environ["FINGRIND_RELEASE_PLAN_JSON"])["releaseAssetNames"]:
    print(asset_name)
PY
}

verify_github_release_download_source_archive() {
    local repo_slug=$1
    local archive_kind=$2
    local output_path=$3

    gh api "/repos/${repo_slug}/${archive_kind}/${VERIFY_GITHUB_RELEASE_TAG_NAME}" \
        > "${output_path}" 2>/dev/null
}

verify_github_release_source_archives() {
    local repo_slug=$1
    local work_dir
    local zip_archive
    local tar_archive
    local archive_output

    work_dir="$(mktemp -d)"
    zip_archive="${work_dir}/source.zip"
    tar_archive="${work_dir}/source.tar.gz"

    verify_github_release_download_source_archive "${repo_slug}" zipball "${zip_archive}" || {
        rm -rf "${work_dir}"
        verify_github_release_record_failure \
            "failed to download the published zip source archive for ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
        return 1
    }
    verify_github_release_download_source_archive "${repo_slug}" tarball "${tar_archive}" || {
        rm -rf "${work_dir}"
        verify_github_release_record_failure \
            "failed to download the published tarball source archive for ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
        return 1
    }
    archive_output="$(
        python3 "${VERIFY_GITHUB_RELEASE_ARCHIVE_VERIFIER}" "${zip_archive}" "${tar_archive}" 2>&1
    )" || {
        rm -rf "${work_dir}"
        verify_github_release_record_failure "${archive_output}"
        return 1
    }
    rm -rf "${work_dir}"
}

verify_github_release_downloaded_assets() {
    local work_dir=$1
    local signer_workflow="${VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG}/${VERIFY_GITHUB_RELEASE_SIGNER_WORKFLOW_PATH}"
    local attestation_output
    local asset_name
    local asset_path
    local downloader_output
    local asset_names=()

    mapfile -t asset_names < <(verify_github_release_resolve_asset_names)
    downloader_output="$(
        "${VERIFY_GITHUB_RELEASE_ASSET_DOWNLOADER}" \
            --repo "${VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG}" \
            --tag "${VERIFY_GITHUB_RELEASE_TAG_NAME}" \
            --dir "${work_dir}" \
            --retries 1 \
            --delay-seconds 0 \
            "${asset_names[@]}" 2>&1
    )" || {
        verify_github_release_record_failure "${downloader_output}"
        return 1
    }

    for asset_name in "${asset_names[@]}"; do
        [[ -n "${asset_name}" ]] || continue
        asset_path="${work_dir}/${asset_name}"
        [[ -f "${asset_path}" ]] || {
            verify_github_release_record_failure \
                "published release asset ${asset_name} did not download to ${asset_path}"
            return 1
        }
        attestation_output="$(
            gh attestation verify "${asset_path}" \
                --repo "${VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG}" \
                --signer-workflow "${signer_workflow}" 2>&1
        )" || {
            verify_github_release_record_failure \
                "published attestation verification failed for ${asset_name}: ${attestation_output}"
            return 1
        }
    done
}

verify_github_release_archive_checksum_pairs() {
    local work_dir=$1

    RELEASE_ASSET_ROOT="${work_dir}" python3 - <<'PY' 2>&1 || return 1
from hashlib import sha256
from pathlib import Path
import os

asset_root = Path(os.environ["RELEASE_ASSET_ROOT"])
archives = sorted(
    path
    for path in asset_root.iterdir()
    if path.is_file() and not path.name.endswith(".sha256")
)
if not archives:
    raise SystemExit("published release verification did not download any archive assets")
for archive_path in archives:
    checksum_path = asset_root / (archive_path.name + ".sha256")
    if not checksum_path.is_file():
        raise SystemExit(f"missing published checksum asset for {archive_path.name}")
    checksum_line = next(
        (line.strip() for line in checksum_path.read_text(encoding="utf-8").splitlines() if line.strip()),
        "",
    )
    if not checksum_line:
        raise SystemExit(f"published checksum asset was empty for {archive_path.name}")
    parts = checksum_line.split()
    if len(parts) != 2:
        raise SystemExit(
            f"published checksum asset must contain exactly one digest-and-filename pair for {archive_path.name}"
        )
    declared_digest, declared_name = parts
    declared_name = declared_name.lstrip("*")
    if declared_name != archive_path.name:
        raise SystemExit(
            f"published checksum asset targeted {declared_name} instead of {archive_path.name}"
        )
    digest = sha256()
    with archive_path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    actual_digest = digest.hexdigest()
    if declared_digest != actual_digest:
        raise SystemExit(
            f"published checksum asset declared {declared_digest} for {archive_path.name} but the archive bytes hashed to {actual_digest}"
        )
PY
}

verify_github_release_once() {
    local release_tag
    local is_draft
    local is_prerelease
    local has_asset
    local asset_name
    local security_policy_output
    local work_dir
    local archive_checksum_output

    release_tag="$(
        gh release view "${VERIFY_GITHUB_RELEASE_TAG_NAME}" --json tagName --jq '.tagName' 2>/dev/null
    )" || {
        verify_github_release_record_failure \
            "could not read published release metadata for ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
        return 1
    }
    [[ "${release_tag}" == "${VERIFY_GITHUB_RELEASE_TAG_NAME}" ]] || {
        verify_github_release_record_failure \
            "release metadata resolved tag ${release_tag} instead of ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
        return 1
    }

    is_draft="$(
        gh release view "${VERIFY_GITHUB_RELEASE_TAG_NAME}" --json isDraft --jq '.isDraft' 2>/dev/null
    )" || {
        verify_github_release_record_failure \
            "could not read draft state for release ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
        return 1
    }
    [[ "${is_draft}" == "true" || "${is_draft}" == "false" ]] || {
        verify_github_release_record_failure \
            "release ${VERIFY_GITHUB_RELEASE_TAG_NAME} draft state must resolve to true or false"
        return 1
    }
    if [[ "${VERIFY_GITHUB_RELEASE_ALLOW_DRAFT}" == "false" && "${is_draft}" != "false" ]]; then
        verify_github_release_record_failure \
            "release ${VERIFY_GITHUB_RELEASE_TAG_NAME} remains a draft"
        return 1
    fi

    is_prerelease="$(
        gh release view "${VERIFY_GITHUB_RELEASE_TAG_NAME}" --json isPrerelease --jq '.isPrerelease' 2>/dev/null
    )" || {
        verify_github_release_record_failure \
            "could not read prerelease state for release ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
        return 1
    }
    [[ "${is_prerelease}" == "false" ]] || {
        verify_github_release_record_failure \
            "release ${VERIFY_GITHUB_RELEASE_TAG_NAME} is marked as a prerelease"
        return 1
    }

    while IFS= read -r asset_name || [[ -n "${asset_name}" ]]; do
        [[ -n "${asset_name}" ]] || continue
        has_asset="$(gh release view "${VERIFY_GITHUB_RELEASE_TAG_NAME}" --json assets --jq \
            ".assets | map(.name) | index(\"${asset_name}\") != null" 2>/dev/null)" || {
            verify_github_release_record_failure \
                "could not inspect release assets for ${VERIFY_GITHUB_RELEASE_TAG_NAME}"
            return 1
        }
        [[ "${has_asset}" == "true" ]] || {
            verify_github_release_record_failure \
                "release ${VERIFY_GITHUB_RELEASE_TAG_NAME} is missing published asset ${asset_name}"
            return 1
        }
    done < <(verify_github_release_resolve_asset_names)

    security_policy_output="$(
        "${VERIFY_GITHUB_RELEASE_SECURITY_POLICY_VERIFIER}" "${VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG}" 2>&1
    )" || {
        verify_github_release_record_failure "${security_policy_output}"
        return 1
    }

    work_dir="$(mktemp -d)"
    verify_github_release_downloaded_assets "${work_dir}" || {
        rm -rf "${work_dir}"
        return 1
    }
    archive_checksum_output="$(
        verify_github_release_archive_checksum_pairs "${work_dir}" 2>&1
    )" || {
        rm -rf "${work_dir}"
        verify_github_release_record_failure "${archive_checksum_output}"
        return 1
    }
    rm -rf "${work_dir}"
    verify_github_release_source_archives "${VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG}" || return 1
}

verify_github_release_parse_args() {
    local tag_name=''
    if [[ $# -gt 0 && "$1" == v* ]]; then
        tag_name="$1"
        shift
    fi
    if [[ -z "${tag_name}" ]]; then
        tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
    fi

    readonly VERIFY_GITHUB_RELEASE_TAG_NAME="${tag_name}"
    readonly VERIFY_GITHUB_RELEASE_RETRY_COUNT="${FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES:-3}"
    readonly VERIFY_GITHUB_RELEASE_RETRY_DELAY_SECONDS="${FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS:-5}"
    readonly VERIFY_GITHUB_RELEASE_ALLOW_DRAFT="${FINGRIND_VERIFY_GITHUB_RELEASE_ALLOW_DRAFT:-false}"
    VERIFY_GITHUB_RELEASE_LAST_FAILURE_REASON=''

    [[ -n "${VERIFY_GITHUB_RELEASE_TAG_NAME}" ]] || verify_github_release_die "tag name is required"
    [[ "${VERIFY_GITHUB_RELEASE_ALLOW_DRAFT}" == "true" || "${VERIFY_GITHUB_RELEASE_ALLOW_DRAFT}" == "false" ]] || \
        verify_github_release_die "FINGRIND_VERIFY_GITHUB_RELEASE_ALLOW_DRAFT must be true or false"
}

verify_github_release_init_contract() {
    readonly VERIFY_GITHUB_RELEASE_ARCHIVE_VERIFIER="${VERIFY_GITHUB_RELEASE_SCRIPT_DIR}/verify-source-archive.py"
    readonly VERIFY_GITHUB_RELEASE_ASSET_DOWNLOADER="${VERIFY_GITHUB_RELEASE_SCRIPT_DIR}/download-github-release-assets.sh"
    readonly VERIFY_GITHUB_RELEASE_SECURITY_POLICY_VERIFIER="${VERIFY_GITHUB_RELEASE_SCRIPT_DIR}/verify-security-policy-surface.sh"
    readonly VERIFY_GITHUB_RELEASE_SIGNER_WORKFLOW_PATH='.github/workflows/release.yml'
    readonly VERIFY_GITHUB_RELEASE_PLAN_READER="${VERIFY_GITHUB_RELEASE_SCRIPT_DIR}/read-release-publication-plan.py"

    [[ -f "${VERIFY_GITHUB_RELEASE_ARCHIVE_VERIFIER}" ]] || verify_github_release_die \
        "missing source archive verifier at ${VERIFY_GITHUB_RELEASE_ARCHIVE_VERIFIER}"
    [[ -x "${VERIFY_GITHUB_RELEASE_ASSET_DOWNLOADER}" ]] || verify_github_release_die \
        "missing executable release asset downloader at ${VERIFY_GITHUB_RELEASE_ASSET_DOWNLOADER}"
    [[ -x "${VERIFY_GITHUB_RELEASE_SECURITY_POLICY_VERIFIER}" ]] || verify_github_release_die \
        "missing executable security-policy verifier at ${VERIFY_GITHUB_RELEASE_SECURITY_POLICY_VERIFIER}"
    [[ -f "${VERIFY_GITHUB_RELEASE_PLAN_READER}" ]] || verify_github_release_die \
        "missing release-publication plan reader at ${VERIFY_GITHUB_RELEASE_PLAN_READER}"

    readonly VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG="$(verify_github_release_resolve_repository_slug)"
    [[ -n "${VERIFY_GITHUB_RELEASE_REPOSITORY_SLUG}" ]] || verify_github_release_die \
        "could not resolve the GitHub repository slug"
    readonly VERIFY_GITHUB_RELEASE_VERSION="${VERIFY_GITHUB_RELEASE_TAG_NAME#v}"
    readonly VERIFY_GITHUB_RELEASE_PLAN_JSON="$(
        python3 "${VERIFY_GITHUB_RELEASE_PLAN_READER}" --version "${VERIFY_GITHUB_RELEASE_VERSION}"
    )"
}

verify_github_release_main() {
    verify_github_release_parse_args "$@"
    verify_github_release_init_contract

    local attempt=1
    until verify_github_release_once; do
        if (( attempt >= VERIFY_GITHUB_RELEASE_RETRY_COUNT )); then
            if [[ -n "${VERIFY_GITHUB_RELEASE_LAST_FAILURE_REASON}" ]]; then
                verify_github_release_die \
                    "release ${VERIFY_GITHUB_RELEASE_TAG_NAME} verification failed: ${VERIFY_GITHUB_RELEASE_LAST_FAILURE_REASON}"
            fi
            verify_github_release_die \
                "release ${VERIFY_GITHUB_RELEASE_TAG_NAME} verification failed for an unknown reason"
        fi
        if [[ -n "${VERIFY_GITHUB_RELEASE_LAST_FAILURE_REASON}" ]]; then
            printf 'release verification pending: %s\n' "${VERIFY_GITHUB_RELEASE_LAST_FAILURE_REASON}" >&2
        fi
        attempt=$((attempt + 1))
        sleep "${VERIFY_GITHUB_RELEASE_RETRY_DELAY_SECONDS}"
    done

    local release_url
    release_url="$(gh release view "${VERIFY_GITHUB_RELEASE_TAG_NAME}" --json url --jq '.url')"
    printf 'Verified GitHub release handoff: %s\n' "${release_url}"
}
