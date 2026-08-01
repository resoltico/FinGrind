#!/usr/bin/env bash
#
# Prove the root check monitor uses portable file-size probing across BSD and GNU
# userlands so release verification cannot pass on macOS while failing on Linux.

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
readonly helper_path="${repo_root}/scripts/check-monitor-common.sh"

[[ -f "${helper_path}" ]] || die "missing check monitor helper"

# shellcheck source=/dev/null
source "${helper_path}"

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

readonly payload_path="${tmp_dir}/payload.log"
printf '0123456789\n' > "${payload_path}"
readonly expected_size='11'

run_fake_stat_variant() {
    local variant=$1
    local bin_dir="${tmp_dir}/${variant}/bin"
    mkdir -p "${bin_dir}"

    cat > "${bin_dir}/stat" <<EOF
#!/usr/bin/env bash
set -euo pipefail
variant='${variant}'
case "\${variant}" in
  bsd)
    [[ "\${1:-}" == '-f' && "\${2:-}" == '%z' && -n "\${3:-}" ]] || exit 64
    wc -c < "\${3}" | tr -d '[:space:]'
    ;;
  gnu)
    [[ "\${1:-}" == '-c' && "\${2:-}" == '%s' && -n "\${3:-}" ]] || exit 64
    wc -c < "\${3}" | tr -d '[:space:]'
    ;;
  *)
    exit 64
    ;;
esac
EOF
    chmod +x "${bin_dir}/stat"

    local reported_size
    reported_size="$(PATH="${bin_dir}:/usr/bin:/bin" bash -c \
        "source '${helper_path}'; file_size_bytes '${payload_path}'")"
    [[ "${reported_size}" == "${expected_size}" ]] || die \
        "check monitor helper reported ${reported_size} bytes for ${variant} stat; expected ${expected_size}"
}

run_fake_stat_variant bsd
run_fake_stat_variant gnu

missing_size="$(bash -c "source '${helper_path}'; file_size_bytes '${tmp_dir}/missing.log'")"
[[ "${missing_size}" == '0' ]] || die \
    "check monitor helper should report 0 for missing files, got ${missing_size}"

readonly compiler_warning_log="${tmp_dir}/compiler-warning.log"
cat >"${compiler_warning_log}" <<'EOF'
> Task :report-pdf:compileTestJava
Note: Some input files use or override a deprecated API.
> Task :cli:compileJava
src/main/java/example/LegacyApi.java:12: warning: [deprecation] oldApi() in Example has been deprecated
> Task :cli:test
warning: expected test fixture output
EOF
compiler_warning_tasks="$(java_compiler_warning_tasks "${compiler_warning_log}")"
expected_compiler_warning_tasks=$':cli:compileJava\n:report-pdf:compileTestJava'
[[ "${compiler_warning_tasks}" == "${expected_compiler_warning_tasks}" ]] || die \
    "check monitor helper did not retain only compiler-owned warning tasks: ${compiler_warning_tasks}"

printf 'check-monitor-common regression: success\n'
