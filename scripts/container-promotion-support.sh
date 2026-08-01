#!/usr/bin/env bash
# Shared state-machine helpers for immutable public multi-architecture container publication.

container_promotion_die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

container_promotion_validate_digest() {
    local digest=$1

    [[ "${digest}" =~ ^sha256:[0-9a-f]{64}$ ]] || container_promotion_die \
        "container manifest digest must be one lowercase sha256 digest, got ${digest}"
}

container_promotion_validate_image_reference() {
    local reference=$1

    [[ "${reference}" =~ ^[^[:space:]@]+$ ]] || container_promotion_die \
        "container image reference must be nonempty and must not contain whitespace or @: ${reference}"
}

container_promotion_validate_version() {
    local version=$1

    [[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || container_promotion_die \
        "container release version must be one stable X.Y.Z version, got ${version}"
}

container_promotion_read_retry_count() {
    local retries="${FINGRIND_CONTAINER_PROMOTION_VERIFY_RETRIES:-6}"

    [[ "${retries}" =~ ^[1-9][0-9]*$ ]] || container_promotion_die \
        'FINGRIND_CONTAINER_PROMOTION_VERIFY_RETRIES must be one positive integer'
    printf '%s\n' "${retries}"
}

container_promotion_read_retry_delay_seconds() {
    local delay_seconds="${FINGRIND_CONTAINER_PROMOTION_VERIFY_DELAY_SECONDS:-2}"

    [[ "${delay_seconds}" =~ ^[0-9]+$ ]] || container_promotion_die \
        'FINGRIND_CONTAINER_PROMOTION_VERIFY_DELAY_SECONDS must be one nonnegative integer'
    printf '%s\n' "${delay_seconds}"
}

container_promotion_manifest_json() {
    local reference=$1
    local inspection_output

    if inspection_output="$(docker buildx imagetools inspect --format '{{json .Manifest}}' "${reference}" 2>&1)"; then
        if ! jq -e '
            type == "object" and
            (.digest | type == "string" and test("^sha256:[0-9a-f]{64}$"))
        ' <<< "${inspection_output}" >/dev/null; then
            container_promotion_die \
                "container manifest inspection for ${reference} did not return one structured descriptor digest"
        fi
        printf '%s\n' "${inspection_output}"
        return 0
    fi

    # This is intentionally the only absence classification. Authentication, transport, schema,
    # and all other inspection failures remain indeterminate and stop publication before a write.
    if [[ "${inspection_output}" == "ERROR: ${reference}: not found" ]]; then
        return 10
    fi
    container_promotion_die \
        "could not inspect container manifest ${reference}; refusing to classify it as absent: ${inspection_output}"
}

container_promotion_manifest_digest() {
    local manifest_json=$1
    local digest

    digest="$(jq -er '.digest' <<< "${manifest_json}")" || container_promotion_die \
        'container manifest JSON did not contain one descriptor digest'
    container_promotion_validate_digest "${digest}"
    printf '%s\n' "${digest}"
}

container_promotion_require_staging_platform_index() {
    local manifest_json=$1
    local architecture=$2
    local reference=$3

    if ! jq -e --arg architecture "${architecture}" '
        (.manifests | type == "array") and
        all(
            .manifests[];
            (.platform.os == "linux" and .platform.architecture == $architecture) or
            (.platform.os == "unknown" and .platform.architecture == "unknown")
        ) and
        ([.manifests[] | select(.platform.os == "linux")] | length == 1) and
        ([
            .manifests[]
            | select(.platform.os == "linux" and .platform.architecture == $architecture)
        ] | length == 1)
    ' <<< "${manifest_json}" >/dev/null; then
        container_promotion_die \
            "staging manifest ${reference} must contain exactly one linux/${architecture} runtime descriptor and only unknown-platform attachment descriptors"
    fi
}

container_promotion_require_public_multiarch_index() {
    local manifest_json=$1
    local reference=$2

    if ! jq -e '
        (.manifests | type == "array") and
        all(
            .manifests[];
            (.platform.os == "linux" and (.platform.architecture == "amd64" or .platform.architecture == "arm64")) or
            (.platform.os == "unknown" and .platform.architecture == "unknown")
        ) and
        ([.manifests[] | select(.platform.os == "linux" and .platform.architecture == "amd64")] | length == 1) and
        ([.manifests[] | select(.platform.os == "linux" and .platform.architecture == "arm64")] | length == 1)
    ' <<< "${manifest_json}" >/dev/null; then
        container_promotion_die \
            "container manifest ${reference} must contain exactly one linux/amd64 and one linux/arm64 runtime descriptor, plus only unknown-platform attachment descriptors"
    fi
}

container_promotion_require_candidate_descriptor_union() {
    local x86_manifest_json=$1
    local arm_manifest_json=$2
    local candidate_manifest_json=$3
    local candidate_reference=$4

    if ! jq -e -n \
        --argjson x86 "${x86_manifest_json}" \
        --argjson arm "${arm_manifest_json}" \
        --argjson candidate "${candidate_manifest_json}" '
        def canonical:
            if type == "object" then
                to_entries
                | sort_by(.key)
                | map({key, value: (.value | canonical)})
                | from_entries
            elif type == "array" then map(canonical)
            else .
            end;
        def descriptor_multiset: map(canonical) | sort_by(tojson);
        ($x86.manifests | type == "array" and length > 0) and
        ($arm.manifests | type == "array" and length > 0) and
        ($candidate.manifests | type == "array" and length > 0) and
        (([$x86.manifests[], $arm.manifests[]] | descriptor_multiset) ==
         ([$candidate.manifests[]] | descriptor_multiset))
    ' >/dev/null; then
        container_promotion_die \
            "staging candidate ${candidate_reference} does not preserve the complete descriptor multiset of both staged platform indexes"
    fi
}

container_promotion_wait_for_manifest_json() {
    local reference=$1
    local expected_digest=$2
    local retries
    local delay_seconds
    local attempt=1
    local manifest_json=''
    local observed_digest=''
    local inspection_status=0

    retries="$(container_promotion_read_retry_count)"
    delay_seconds="$(container_promotion_read_retry_delay_seconds)"
    while (( attempt <= retries )); do
        if manifest_json="$(container_promotion_manifest_json "${reference}")"; then
            observed_digest="$(container_promotion_manifest_digest "${manifest_json}")"
            if [[ "${observed_digest}" == "${expected_digest}" ]]; then
                printf '%s\n' "${manifest_json}"
                return 0
            fi
        else
            inspection_status=$?
            if [[ ${inspection_status} -ne 10 ]]; then
                return "${inspection_status}"
            fi
            observed_digest='absent'
        fi

        if (( attempt == retries )); then
            break
        fi
        if [[ "${delay_seconds}" != '0' ]]; then
            sleep "${delay_seconds}"
        fi
        attempt=$((attempt + 1))
    done

    container_promotion_die \
        "container manifest ${reference} did not converge to ${expected_digest}; observed ${observed_digest}"
}

container_promotion_create_manifest() {
    local target_reference=$1
    shift
    local metadata_file
    local created_digest

    metadata_file="$(mktemp "${TMPDIR:-/tmp}/fingrind-container-promotion-metadata.XXXXXX")" || \
        container_promotion_die 'could not allocate container-promotion metadata file'
    if ! docker buildx imagetools create \
        --tag "${target_reference}" \
        --metadata-file "${metadata_file}" \
        "$@" >/dev/null; then
        rm -f -- "${metadata_file}"
        container_promotion_die "could not create container manifest ${target_reference}"
    fi
    created_digest="$(jq -er '."containerimage.descriptor".digest' "${metadata_file}")" || {
        rm -f -- "${metadata_file}"
        container_promotion_die \
            "container manifest creation for ${target_reference} did not emit one descriptor digest"
    }
    rm -f -- "${metadata_file}"
    container_promotion_validate_digest "${created_digest}"
    printf '%s\n' "${created_digest}"
}

container_promotion_main() {
    local staging_reference=$1
    local public_reference=$2
    local version=$3
    local mark_latest=$4
    local x86_reference
    local arm_reference
    local candidate_reference
    local exact_reference
    local latest_reference
    local exact_manifest_json=''
    local candidate_manifest_json=''
    local x86_manifest_json=''
    local arm_manifest_json=''
    local candidate_digest=''
    local exact_digest=''
    local accepted_exact_digest=''
    local latest_manifest_json=''
    local latest_digest=''
    local inspection_status=0
    local created_digest=''

    container_promotion_validate_image_reference "${staging_reference}"
    container_promotion_validate_image_reference "${public_reference}"
    container_promotion_validate_version "${version}"
    [[ "${mark_latest}" == 'true' || "${mark_latest}" == 'false' ]] || \
        container_promotion_die 'container latest policy must be true or false'

    x86_reference="${staging_reference}:${version}-linux-x86_64"
    arm_reference="${staging_reference}:${version}-linux-aarch64"
    candidate_reference="${staging_reference}:${version}-candidate"
    exact_reference="${public_reference}:${version}"
    latest_reference="${public_reference}:latest"

    # Resolve the immutable exact public state first. The retained candidate is only needed to
    # prove a retry converges; never rebuild it after exact publication exists.
    if exact_manifest_json="$(container_promotion_manifest_json "${exact_reference}")"; then
        exact_digest="$(container_promotion_manifest_digest "${exact_manifest_json}")"
        if candidate_manifest_json="$(container_promotion_manifest_json "${candidate_reference}")"; then
            candidate_digest="$(container_promotion_manifest_digest "${candidate_manifest_json}")"
            container_promotion_require_public_multiarch_index \
                "${candidate_manifest_json}" "${candidate_reference}"
        else
            inspection_status=$?
            [[ ${inspection_status} -eq 10 ]] || return "${inspection_status}"
            container_promotion_die \
                "public immutable container ${exact_reference} exists but retained staging candidate ${candidate_reference} is absent; refusing to infer retry convergence"
        fi
        if [[ "${exact_digest}" != "${candidate_digest}" ]]; then
            container_promotion_die \
                "public immutable container ${exact_reference} resolves to ${exact_digest}, but retained staging candidate ${candidate_reference} resolves to ${candidate_digest}; refusing to overwrite the public exact tag"
        fi
        container_promotion_require_public_multiarch_index \
            "${exact_manifest_json}" "${exact_reference}"
        accepted_exact_digest="${exact_digest}"
        printf 'accepted immutable public container %s at %s without rewriting it\n' \
            "${exact_reference}" "${accepted_exact_digest}"
    else
        inspection_status=$?
        [[ ${inspection_status} -eq 10 ]] || return "${inspection_status}"

        if candidate_manifest_json="$(container_promotion_manifest_json "${candidate_reference}")"; then
            candidate_digest="$(container_promotion_manifest_digest "${candidate_manifest_json}")"
            container_promotion_require_public_multiarch_index \
                "${candidate_manifest_json}" "${candidate_reference}"
            printf 'using retained staging candidate %s at %s\n' \
                "${candidate_reference}" "${candidate_digest}"
        else
            inspection_status=$?
            [[ ${inspection_status} -eq 10 ]] || return "${inspection_status}"

            x86_manifest_json="$(container_promotion_manifest_json "${x86_reference}")" || \
                return $?
            arm_manifest_json="$(container_promotion_manifest_json "${arm_reference}")" || \
                return $?
            container_promotion_require_staging_platform_index \
                "${x86_manifest_json}" 'amd64' "${x86_reference}"
            container_promotion_require_staging_platform_index \
                "${arm_manifest_json}" 'arm64' "${arm_reference}"

            candidate_digest="$(container_promotion_create_manifest \
                "${candidate_reference}" \
                "${staging_reference}@$(container_promotion_manifest_digest "${x86_manifest_json}")" \
                "${staging_reference}@$(container_promotion_manifest_digest "${arm_manifest_json}")")"
            candidate_manifest_json="$(container_promotion_wait_for_manifest_json \
                "${candidate_reference}" "${candidate_digest}")"
            container_promotion_require_candidate_descriptor_union \
                "${x86_manifest_json}" \
                "${arm_manifest_json}" \
                "${candidate_manifest_json}" \
                "${candidate_reference}"
            container_promotion_require_public_multiarch_index \
                "${candidate_manifest_json}" "${candidate_reference}"
            printf 'created durable staging candidate %s at %s\n' \
                "${candidate_reference}" "${candidate_digest}"
        fi

        created_digest="$(container_promotion_create_manifest \
            "${exact_reference}" "${staging_reference}@${candidate_digest}")"
        [[ "${created_digest}" == "${candidate_digest}" ]] || container_promotion_die \
            "public exact container creation for ${exact_reference} produced ${created_digest}, not retained candidate ${candidate_digest}"
        exact_manifest_json="$(container_promotion_wait_for_manifest_json \
            "${exact_reference}" "${candidate_digest}")"
        accepted_exact_digest="$(container_promotion_manifest_digest "${exact_manifest_json}")"
        container_promotion_require_public_multiarch_index \
            "${exact_manifest_json}" "${exact_reference}"
        printf 'created immutable public container %s at %s\n' \
            "${exact_reference}" "${accepted_exact_digest}"
    fi

    [[ "${mark_latest}" == 'true' ]] || return 0

    if latest_manifest_json="$(container_promotion_manifest_json "${latest_reference}")"; then
        latest_digest="$(container_promotion_manifest_digest "${latest_manifest_json}")"
        if [[ "${latest_digest}" == "${accepted_exact_digest}" ]]; then
            container_promotion_require_public_multiarch_index \
                "${latest_manifest_json}" "${latest_reference}"
            printf 'latest public container %s already resolves to accepted exact digest %s\n' \
                "${latest_reference}" "${accepted_exact_digest}"
            return 0
        fi
    else
        inspection_status=$?
        [[ ${inspection_status} -eq 10 ]] || return "${inspection_status}"
    fi

    created_digest="$(container_promotion_create_manifest \
        "${latest_reference}" "${public_reference}@${accepted_exact_digest}")"
    [[ "${created_digest}" == "${accepted_exact_digest}" ]] || container_promotion_die \
        "latest public container creation for ${latest_reference} produced ${created_digest}, not accepted exact digest ${accepted_exact_digest}"
    latest_manifest_json="$(container_promotion_wait_for_manifest_json \
        "${latest_reference}" "${accepted_exact_digest}")"
    latest_digest="$(container_promotion_manifest_digest "${latest_manifest_json}")"
    container_promotion_require_public_multiarch_index \
        "${latest_manifest_json}" "${latest_reference}"
    printf 'updated latest public container %s to accepted exact digest %s\n' \
        "${latest_reference}" "${latest_digest}"
}
