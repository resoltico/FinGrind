#!/usr/bin/env bash
# Exercise the Windows Gradle wrapper policy through real PowerShell without claiming that the
# host ran cmd.exe, MSVC, or a Windows Gradle process. Native Windows CI remains that proof.

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
readonly owner_path="${repo_root}/scripts/gradle-wrapper-owner.ps1"
readonly entrypoint_path="${repo_root}/scripts/gradlew.ps1"

[[ -f "${owner_path}" ]] || die "missing PowerShell Gradle wrapper owner at ${owner_path}"
[[ -f "${entrypoint_path}" ]] || die "missing PowerShell Gradle wrapper entrypoint at ${entrypoint_path}"

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'PowerShell Gradle wrapper owner regression: skipped (pwsh unavailable)\n'
    exit 0
fi

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-gradlew-powershell-owner.XXXXXX")"
cleanup() {
    rm -rf "${tmp_dir}" 2>/dev/null || true
}
trap cleanup EXIT

readonly owner_contract_script="${tmp_dir}/owner-contract.ps1"
cat >"${owner_contract_script}" <<'PWSH'
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. $env:OWNER_PATH

function Assert-FinGrindWrapperTest {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

$parsed = @(ConvertFrom-FinGrindWindowsCommandLine `
    -CommandLine '-Done=alpha "-Dspace=two words" "-Dquote=said \"hello\"" "" "C:\Program Files\Java\\"')
$expectedParsed = @(
    '-Done=alpha',
    '-Dspace=two words',
    '-Dquote=said "hello"',
    '',
    'C:\Program Files\Java\'
)
Assert-FinGrindWrapperTest `
    -Condition (($parsed -join [char]0) -eq ($expectedParsed -join [char]0)) `
    -Message "Windows JAVA_OPTS/GRADLE_OPTS tokenizer lost quoted, escaped, empty, or trailing-backslash arguments"

$planRoot = Join-Path $env:TEST_ROOT "plan"
$plan = [pscustomobject]@{
    ProjectCacheDir = Join-Path $planRoot "project-cache"
    BuildLogicDir = Join-Path $planRoot "build-logic"
    JacocoRoot = Join-Path $planRoot "jacoco"
    ShouldExternalizeProjectBuildRoot = $true
    ProjectBuildRoot = Join-Path $planRoot "project-build"
    InvocationLeaseFile = Join-Path $planRoot "leases/build.lease"
}
$originalJavaOpts = [System.Environment]::GetEnvironmentVariable("JAVA_OPTS")
$originalGradleOpts = [System.Environment]::GetEnvironmentVariable("GRADLE_OPTS")
try {
    $env:JAVA_OPTS = '-Djava.option="two words" "-Djava.quote=said \"hello\""'
    $env:GRADLE_OPTS = '-Dgradle.option="Rīga büro"'
    $gradleArguments = @(':cli:tasks', '--unicode=Rīga büro')
    $invocation = New-FinGrindGradleWrapperInvocation `
        -RepositoryRoot $env:REPO_ROOT `
        -GradleArguments $gradleArguments `
        -JavaExecutable '/synthetic/java' `
        -WrapperPlan $plan
    $expectedArguments = @(
        '-Xmx64m',
        '-Xms64m',
        '-Djava.option=two words',
        '-Djava.quote=said "hello"',
        '-Dgradle.option=Rīga büro',
        "-Dfingrind.gradle.build-logic-dir=$($plan.BuildLogicDir)",
        "-Dfingrind.gradle.jacoco-root=$($plan.JacocoRoot)",
        "-Dfingrind.gradle.project-build-root=$($plan.ProjectBuildRoot)",
        '-Dorg.gradle.appname=gradlew',
        '-jar',
        (Join-Path $env:REPO_ROOT 'gradle/wrapper/gradle-wrapper.jar'),
        "--project-cache-dir=$($plan.ProjectCacheDir)",
        ':cli:tasks',
        '--unicode=Rīga büro'
    )
    Assert-FinGrindWrapperTest `
        -Condition (($invocation.JavaArguments -join [char]0) -eq ($expectedArguments -join [char]0)) `
        -Message "wrapper owner changed the canonical Java and Gradle argument vector"
    Assert-FinGrindWrapperTest `
        -Condition ($invocation.WorkingDirectory -eq [System.IO.Directory]::GetCurrentDirectory()) `
        -Message "wrapper owner changed the caller working directory"
    Assert-FinGrindWrapperTest `
        -Condition ($invocation.InvocationLeaseSource -eq (Join-Path $env:REPO_ROOT "scripts/GradleInvocationLease.java")) `
        -Message "wrapper owner changed the canonical invocation lease source"
    Assert-FinGrindWrapperTest `
        -Condition ($invocation.InvocationLeaseFile -eq $plan.InvocationLeaseFile) `
        -Message "wrapper owner changed the canonical invocation lease file"
    foreach ($requiredDirectory in @(
            $plan.ProjectCacheDir,
            $plan.BuildLogicDir,
            $plan.JacocoRoot,
            $plan.ProjectBuildRoot,
            (Split-Path -Path $plan.InvocationLeaseFile -Parent)
        )) {
        Assert-FinGrindWrapperTest `
            -Condition (Test-Path -LiteralPath $requiredDirectory -PathType Container) `
            -Message "wrapper owner did not create required default directory $requiredDirectory"
    }

    $overrideRoot = Join-Path $env:TEST_ROOT "override"
    $overridePlan = [pscustomobject]@{
        ProjectCacheDir = Join-Path $overrideRoot "would-not-create-project-cache"
        BuildLogicDir = Join-Path $overrideRoot "would-not-create-build-logic"
        JacocoRoot = Join-Path $overrideRoot "would-not-create-jacoco"
        ShouldExternalizeProjectBuildRoot = $true
        ProjectBuildRoot = Join-Path $overrideRoot "would-not-create-project-build"
        InvocationLeaseFile = Join-Path $overrideRoot "leases/build.lease"
    }
    $selectedProjectCache = Join-Path $env:TEST_ROOT "selected-project-cache"
    $selectedBuildLogic = Join-Path $env:TEST_ROOT "selected-build-logic"
    $selectedJacoco = Join-Path $env:TEST_ROOT "selected-jacoco"
    $selectedProjectBuild = Join-Path $env:TEST_ROOT "selected-project-build"
    $overrideArguments = @(
        "--project-cache-dir=$selectedProjectCache",
        "-Dfingrind.gradle.build-logic-dir=$selectedBuildLogic",
        "-Dfingrind.gradle.jacoco-root=$selectedJacoco",
        "-Dfingrind.gradle.project-build-root=$selectedProjectBuild",
        ':cli:tasks'
    )
    $overrideInvocation = New-FinGrindGradleWrapperInvocation `
        -RepositoryRoot $env:REPO_ROOT `
        -GradleArguments $overrideArguments `
        -JavaExecutable '/synthetic/java' `
        -WrapperPlan $overridePlan
    foreach ($uncreatedDirectory in @(
            $overridePlan.ProjectCacheDir,
            $overridePlan.BuildLogicDir,
            $overridePlan.JacocoRoot,
            $overridePlan.ProjectBuildRoot
        )) {
        Assert-FinGrindWrapperTest `
            -Condition (-not (Test-Path -LiteralPath $uncreatedDirectory)) `
            -Message "wrapper owner created a caller-selected override default at $uncreatedDirectory"
    }
    foreach ($forbiddenDefault in @(
            "--project-cache-dir=$($overridePlan.ProjectCacheDir)",
            "-Dfingrind.gradle.build-logic-dir=$($overridePlan.BuildLogicDir)",
            "-Dfingrind.gradle.jacoco-root=$($overridePlan.JacocoRoot)",
            "-Dfingrind.gradle.project-build-root=$($overridePlan.ProjectBuildRoot)"
        )) {
        Assert-FinGrindWrapperTest `
            -Condition (-not ($overrideInvocation.JavaArguments -contains $forbiddenDefault)) `
            -Message "wrapper owner injected a default despite a caller-selected override: $forbiddenDefault"
    }
    Assert-FinGrindWrapperTest `
        -Condition (Test-Path -LiteralPath (Split-Path -Path $overridePlan.InvocationLeaseFile -Parent) -PathType Container) `
        -Message "wrapper owner did not create the independent invocation lease directory"
}
finally {
    [System.Environment]::SetEnvironmentVariable("JAVA_OPTS", $originalJavaOpts)
    [System.Environment]::SetEnvironmentVariable("GRADLE_OPTS", $originalGradleOpts)
}
PWSH

OWNER_PATH="${owner_path}" REPO_ROOT="${repo_root}" TEST_ROOT="${tmp_dir}" \
    pwsh -NoLogo -NoProfile -File "${owner_contract_script}"

readonly fake_bin="${tmp_dir}/fake-bin"
readonly fake_java="${fake_bin}/java"
readonly caller_directory="${tmp_dir}/caller directory"
readonly project_cache_directory="${tmp_dir}/project cache"
readonly build_logic_directory="${tmp_dir}/build logic"
readonly jacoco_directory="${tmp_dir}/jacoco root"
readonly project_build_directory="${tmp_dir}/project build"
readonly capture_path="${tmp_dir}/captured-invocation.json"
mkdir -p "${fake_bin}" "${caller_directory}"
cat >"${fake_java}" <<'PY'
#!/usr/bin/env python3
import json
import os
from pathlib import Path
import subprocess
import sys

if len(sys.argv) >= 5 and sys.argv[1].endswith("GradleInvocationLease.java") and sys.argv[3] == "--":
    lease_file = Path(sys.argv[2])
    if not lease_file.parent.is_dir():
        raise SystemExit("PowerShell Gradle owner did not create the invocation lease directory")
    raise SystemExit(subprocess.run(sys.argv[4:]).returncode)

Path(os.environ["FINGRIND_WRAPPER_CAPTURE_PATH"]).write_text(
    json.dumps(
        {
            "argv": sys.argv[1:],
            "cwd": os.getcwd(),
            "stdinIsATty": sys.stdin.isatty(),
            "stdin": (
                sys.stdin.read()
                if os.environ.get("FINGRIND_WRAPPER_READ_STDIN", "1") == "1"
                else None
            ),
        },
        ensure_ascii=False,
    ),
    encoding="utf-8",
)
print("fake-java-standard-output")
print("fake-java-standard-error", file=sys.stderr)
raise SystemExit(int(os.environ.get("FINGRIND_WRAPPER_EXIT_CODE", "0")))
PY
chmod +x "${fake_java}"

entry_stdout="${tmp_dir}/entry.stdout"
entry_stderr="${tmp_dir}/entry.stderr"
(
    cd "${caller_directory}"
    printf 'inherited stdin payload\n' |
        env \
            PATH="${fake_bin}:${PATH}" \
            JAVA_HOME='' \
            JAVA_OPTS='-Djava.option="two words" "-Djava.quote=said \"hello\""' \
            GRADLE_OPTS='-Dgradle.option="Rīga büro"' \
            FINGRIND_GRADLE_PROJECT_CACHE_DIR="${project_cache_directory}" \
            FINGRIND_GRADLE_BUILD_LOGIC_DIR="${build_logic_directory}" \
            FINGRIND_GRADLE_JACOCO_ROOT="${jacoco_directory}" \
            FINGRIND_GRADLE_PROJECT_BUILD_ROOT="${project_build_directory}" \
            FINGRIND_WRAPPER_CAPTURE_PATH="${capture_path}" \
            pwsh -NoLogo -NoProfile -File "${entrypoint_path}" \
                :cli:tasks \
                '--unicode=Rīga büro' \
                '-Dcaller.option=Rīga büro' \
                '--literal=quote"and\slash' \
                '' \
                '--trailing-backslash=Program Files\Gradle\' >"${entry_stdout}" 2>"${entry_stderr}"
)

[[ -f "${capture_path}" ]] || die "PowerShell Gradle entrypoint did not start the fake Java process"
grep -Fxq 'fake-java-standard-output' "${entry_stdout}" || die \
    "PowerShell Gradle owner did not preserve inherited standard output"
grep -Fxq 'fake-java-standard-error' "${entry_stderr}" || die \
    "PowerShell Gradle owner did not preserve inherited standard error"
python3 - <<'PY' \
    "${capture_path}" \
    "${repo_root}" \
    "${caller_directory}" \
    "${project_cache_directory}" \
    "${build_logic_directory}" \
    "${jacoco_directory}" \
    "${project_build_directory}"
import json
from pathlib import Path
import sys

capture_path = Path(sys.argv[1])
repo_root = Path(sys.argv[2])
caller_directory = str(Path(sys.argv[3]).resolve())
project_cache_directory = sys.argv[4]
build_logic_directory = sys.argv[5]
jacoco_directory = sys.argv[6]
project_build_directory = sys.argv[7]
payload = json.loads(capture_path.read_text(encoding="utf-8"))

expected_arguments = [
    "-Xmx64m",
    "-Xms64m",
    "-Djava.option=two words",
    '-Djava.quote=said "hello"',
    "-Dgradle.option=Rīga büro",
    f"-Dfingrind.gradle.build-logic-dir={build_logic_directory}",
    f"-Dfingrind.gradle.jacoco-root={jacoco_directory}",
    f"-Dfingrind.gradle.project-build-root={project_build_directory}",
    "-Dorg.gradle.appname=gradlew",
    "-jar",
    str(repo_root / "gradle/wrapper/gradle-wrapper.jar"),
    f"--project-cache-dir={project_cache_directory}",
    ":cli:tasks",
    "--unicode=Rīga büro",
    "-Dcaller.option=Rīga büro",
    '--literal=quote"and\\slash',
    "",
    "--trailing-backslash=Program Files\\Gradle\\",
]
if payload["argv"] != expected_arguments:
    raise SystemExit(
        "PowerShell Gradle entrypoint changed the native Java argv:\n"
        f"expected={expected_arguments!r}\nactual={payload['argv']!r}"
    )
if payload["cwd"] != caller_directory:
    raise SystemExit(
        f"PowerShell Gradle entrypoint changed the caller cwd: {payload['cwd']!r} != {caller_directory!r}"
    )
if payload["stdin"] != "inherited stdin payload\n":
    raise SystemExit(f"PowerShell Gradle entrypoint lost inherited stdin: {payload['stdin']!r}")
if payload["stdinIsATty"] is not False:
    raise SystemExit(
        "PowerShell Gradle entrypoint did not hand redirected standard input to Java as a pipe"
    )
PY

set +e
env \
    PATH="${fake_bin}:${PATH}" \
    JAVA_HOME='' \
    FINGRIND_GRADLE_PROJECT_CACHE_DIR="${project_cache_directory}" \
    FINGRIND_GRADLE_BUILD_LOGIC_DIR="${build_logic_directory}" \
    FINGRIND_GRADLE_JACOCO_ROOT="${jacoco_directory}" \
    FINGRIND_GRADLE_PROJECT_BUILD_ROOT="${project_build_directory}" \
    FINGRIND_WRAPPER_CAPTURE_PATH="${capture_path}" \
    FINGRIND_WRAPPER_READ_STDIN=0 \
    FINGRIND_WRAPPER_EXIT_CODE=23 \
    pwsh -NoLogo -NoProfile -File "${entrypoint_path}" :cli:tasks </dev/null >/dev/null 2>/dev/null
exit_status=$?
set -e
[[ ${exit_status} -eq 23 ]] || die \
    "PowerShell Gradle entrypoint did not relay the Java process exit code: ${exit_status}"

if [[ -t 0 ]]; then
    readonly interactive_capture_path="${tmp_dir}/interactive-invocation.json"
    (
        cd "${caller_directory}"
        env \
            PATH="${fake_bin}:${PATH}" \
            JAVA_HOME='' \
            FINGRIND_GRADLE_PROJECT_CACHE_DIR="${project_cache_directory}" \
            FINGRIND_GRADLE_BUILD_LOGIC_DIR="${build_logic_directory}" \
            FINGRIND_GRADLE_JACOCO_ROOT="${jacoco_directory}" \
            FINGRIND_GRADLE_PROJECT_BUILD_ROOT="${project_build_directory}" \
            FINGRIND_WRAPPER_CAPTURE_PATH="${interactive_capture_path}" \
            FINGRIND_WRAPPER_READ_STDIN=0 \
            pwsh -NoLogo -NoProfile -File "${entrypoint_path}" :cli:tasks >/dev/null 2>/dev/null
    )
    python3 - <<'PY' "${interactive_capture_path}"
import json
from pathlib import Path
import sys

payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload["stdinIsATty"] is not True:
    raise SystemExit(
        "PowerShell Gradle entrypoint did not preserve an interactive standard-input terminal"
    )
if payload["stdin"] is not None:
    raise SystemExit(
        "interactive fake Java unexpectedly consumed standard input during wrapper regression"
    )
PY
else
    printf 'PowerShell Gradle wrapper owner regression: interactive standard-input inheritance skipped (no terminal)\n'
fi

printf 'PowerShell Gradle wrapper owner regression: success\n'
