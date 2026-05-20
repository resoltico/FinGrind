FROM azul/zulu-openjdk-alpine:26.0.1-jdk AS builder

WORKDIR /build

RUN apk add --no-cache build-base python3

COPY source-root/ /build/source-root/
COPY Dockerfile docker-build-context-manifest.json docker-entrypoint.sh fingrind.jar managed-sqlite-contract.json runtime-modules.txt /build/
COPY source-root/scripts/render-managed-sqlite-compiler-flags.py scripts/render-managed-sqlite-compiler-flags.py
COPY source-root/scripts/verify-docker-build-context.py scripts/verify-docker-build-context.py
COPY source-root/third_party/sqlite/sqlite3mc-amalgamation-2.3.4-sqlite-3530001/ sqlite-source/

# The canonical Docker assembly input is built by ./gradlew :cli:stageDockerBuildContext before
# docker build is invoked. That staged directory now carries the Dockerfile, the internal
# application JAR, the canonical private-runtime module list, the rendered entrypoint, the
# managed-SQLite contract, the generated build-context manifest, and the exact source/build
# snapshot used to verify freshness. Container assembly therefore consumes one checked context
# instead of reopening the repository root and accidentally walking sibling build byproducts.
# Public CLI downloads are the self-contained bundle archives, not this assembly input.

RUN python3 - <<'PY'
import json
from hashlib import sha3_256
from pathlib import Path
contract = json.loads(Path("managed-sqlite-contract.json").read_text(encoding="utf-8"))
expected_package = contract.get("requiredSourcePackageId")
expected_files = contract.get("vendoredReleaseFiles")
source_dir = Path("sqlite-source")
if source_dir.name != "sqlite-source":
    raise SystemExit("unexpected SQLite source directory layout")
if not isinstance(expected_package, str) or not expected_package:
    raise SystemExit("managed SQLite contract missing requiredSourcePackageId")
if not isinstance(expected_files, dict) or not expected_files:
    raise SystemExit("managed SQLite contract missing vendoredReleaseFiles")
actual_files = sorted(
    str(path.relative_to(source_dir)).replace("\\\\", "/")
    for path in source_dir.rglob("*")
    if path.is_file()
)
expected_paths = sorted(expected_files)
if actual_files != expected_paths:
    raise SystemExit(
        "vendored SQLite release manifest drift: "
        f"expected {expected_paths} but found {actual_files}"
    )
for relative_path, expected_digest in expected_files.items():
    source = (
        source_dir.joinpath(relative_path)
        .read_bytes()
        .replace(b"\r\n", b"\n")
        .replace(b"\r", b"\n")
    )
    actual = sha3_256(source).hexdigest()
    if actual != expected_digest:
        raise SystemExit(
            f"vendored SQLite source hash mismatch for {relative_path}: "
            f"expected {expected_digest} but found {actual}"
        )
PY

RUN python3 scripts/verify-docker-build-context.py --context-dir /build --source-root /build/source-root

RUN cc -O2 -fPIC $(python3 scripts/render-managed-sqlite-compiler-flags.py /build/managed-sqlite-contract.json) -shared \
    -Wl,-soname,libsqlite3.so.0 -o libsqlite3.so.0 sqlite-source/sqlite3mc_amalgamation.c -ldl -lpthread

RUN python3 - <<'PY'
import json
import platform
import shutil
import subprocess
from pathlib import Path


def first_success(candidates: list[tuple[str, ...]], *, accept_failure_output: bool = False) -> str:
    last_error = "no probe attempts executed"
    for candidate in candidates:
        completed = subprocess.run(candidate, check=False, capture_output=True, text=True)
        combined = "\n".join(
            part.strip() for part in (completed.stdout, completed.stderr) if part.strip()
        ).strip()
        if completed.returncode == 0 and combined:
            return combined
        if accept_failure_output and combined:
            return combined
        last_error = combined or f"exit={completed.returncode}"
    raise SystemExit(f"probe failed: {last_error}")


def normalize_target_triple(raw_output: str) -> str:
    lines = [line.strip() for line in raw_output.splitlines() if line.strip()]
    for line in lines:
        lowered = line.lower()
        if "linux" in lowered and "-" in lowered and " " not in lowered:
            return line
    for line in lines:
        lowered = line.lower()
        if "aarch64" in lowered or "arm64" in lowered:
            return "aarch64-unknown-linux-gnu"
        if "x86_64" in lowered or "amd64" in lowered or "x64" in lowered:
            return "x86_64-unknown-linux-gnu"
    raise SystemExit(f"could not normalize target triple from probe output: {raw_output}")


def normalize_sysroot(raw_output: str) -> str:
    lines = [line.strip() for line in raw_output.splitlines() if line.strip()]
    for line in lines:
        if not line.startswith("Configured with:") and not line.startswith("Thread model:"):
            return line
    raise SystemExit(f"could not normalize sysroot/search-dir probe output: {raw_output}")


compiler_command = "cc"
compiler_executable = shutil.which(compiler_command) or compiler_command
toolchain = {
    "compilerCommand": compiler_command,
    "compilerExecutable": compiler_executable,
    "compilerVersion": first_success(
        [(compiler_executable, "--version"), (compiler_executable, "-v")], accept_failure_output=True
    ),
    "targetTriple": normalize_target_triple(
        first_success(
            [
                (compiler_executable, "-dumpmachine"),
                (compiler_executable, "-print-target-triple"),
                (compiler_executable, "--print-target-triple"),
                (compiler_executable, "--version"),
                (compiler_executable, "-v"),
            ],
            accept_failure_output=True,
        )
    ),
    "linkerVersion": first_success([("ld", "-v"), ("ld", "-V")], accept_failure_output=True),
    "sdkOrSysroot": normalize_sysroot(
        first_success(
            [
                (compiler_executable, "--print-sysroot"),
                (compiler_executable, "-print-sysroot"),
                (compiler_executable, "-print-search-dirs"),
                (compiler_executable, "-v"),
            ],
            accept_failure_output=True,
        )
    ),
    "operatingSystemId": "linux",
    "hostArchitecture": platform.machine(),
}
Path("toolchain-fingerprint.json").write_text(json.dumps(toolchain, indent=2) + "\n", encoding="utf-8")

managed_contract = json.loads(Path("managed-sqlite-contract.json").read_text(encoding="utf-8"))
build_contract = {
    "sqliteVersion": managed_contract["requiredMinimumSqliteVersion"],
    "operatingSystemId": "linux",
    "requiredCompileOptions": managed_contract["requiredCompileOptions"],
    "forbiddenCompileOptions": managed_contract["forbiddenCompileOptions"],
    "requiresSecureMemorySupport": managed_contract["requiresSecureMemorySupport"],
}
Path("build-contract.json").write_text(json.dumps(build_contract, indent=2) + "\n", encoding="utf-8")
PY

RUN sha256sum libsqlite3.so.0 > libsqlite3.so.0.sha256
RUN sha256sum libsqlite3.so.0 > libsqlite3.so.0.trusted.sha256

RUN jlink \
    --module-path "${JAVA_HOME}/jmods" \
    --add-modules "$(cat /build/runtime-modules.txt)" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/fingrind/runtime

FROM alpine:3.23

WORKDIR /workdir

RUN apk add --no-cache libstdc++

COPY --from=builder /opt/fingrind/runtime /opt/fingrind/runtime
COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/native/libsqlite3.so.0
COPY --from=builder /build/libsqlite3.so.0.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.sha256
COPY --from=builder /build/libsqlite3.so.0.trusted.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.trusted.sha256
COPY --from=builder /build/toolchain-fingerprint.json /opt/fingrind/lib/native/toolchain-fingerprint.json
COPY --from=builder /build/build-contract.json /opt/fingrind/lib/native/build-contract.json
COPY --from=builder /build/fingrind.jar /opt/fingrind/lib/app/fingrind.jar
COPY --from=builder /build/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh
COPY source-root/LICENSE source-root/LICENSE-APACHE-2.0 source-root/LICENSE-SIL-OFL-1.1 source-root/LICENSE-SQLITE3MULTIPLECIPHERS source-root/NOTICE source-root/PATENTS.md /opt/fingrind/doc/

RUN chmod +x /opt/fingrind/bin/docker-entrypoint.sh

ENTRYPOINT ["/opt/fingrind/bin/docker-entrypoint.sh"]
