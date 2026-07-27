FROM azul/zulu-openjdk-alpine:26.0.1-jdk@sha256:d5514973a10f0dbdf3c18199465713176316a60ee032d19adacd4812588b611b AS builder

WORKDIR /build

RUN apk add --no-cache python3 binutils

COPY source-root/ /build/source-root/
COPY Dockerfile docker-build-context-manifest.json docker-entrypoint.sh fingrind.jar native-sqlite-format-boundary-probe.jar runtime-modules.txt /build/
COPY libsqlite3.so.0 libsqlite3.so.0.sha256 toolchain-fingerprint.json build-contract.json /build/
COPY source-root/scripts/verify-docker-build-context.py scripts/verify-docker-build-context.py

# The canonical Docker assembly input is built by ./gradlew :cli:stageDockerBuildContext before
# docker build is invoked. That staged directory now carries the Dockerfile, the internal
# application JAR, the canonical private-runtime module list, the rendered entrypoint, the
# managed-SQLite library plus its provenance files, the generated build-context manifest, and the
# exact source/build snapshot used to verify freshness. Container assembly therefore consumes one
# checked context instead of reopening the repository root and accidentally walking sibling build
# byproducts or rebuilding native SQLite through a second pipeline. Public CLI downloads are the
# self-contained bundle archives, not this assembly input.

RUN python3 scripts/verify-docker-build-context.py --context-dir /build --source-root /build/source-root

RUN python3 - <<'PY'
from hashlib import sha256
from pathlib import Path

library_path = Path("libsqlite3.so.0")
checksum_path = Path("libsqlite3.so.0.sha256")
toolchain_path = Path("toolchain-fingerprint.json")
build_contract_path = Path("build-contract.json")

for path in (library_path, checksum_path, toolchain_path, build_contract_path):
    if not path.is_file():
        raise SystemExit(f"missing staged managed SQLite artifact {path}")
    if path.stat().st_size == 0:
        raise SystemExit(f"staged managed SQLite artifact was empty: {path}")

checksum_line = next(
    (line.strip() for line in checksum_path.read_text(encoding="utf-8").splitlines() if line.strip()),
    "",
)
if not checksum_line:
    raise SystemExit("managed SQLite checksum file was empty")
parts = checksum_line.split()
if len(parts) != 2:
    raise SystemExit("managed SQLite checksum file must contain exactly one digest-and-filename pair")
declared_digest, declared_name = parts
declared_name = declared_name.lstrip("*")
if declared_name != library_path.name:
    raise SystemExit(
        f"managed SQLite checksum file targeted {declared_name} instead of {library_path.name}"
    )
actual_digest = sha256(library_path.read_bytes()).hexdigest()
if declared_digest != actual_digest:
    raise SystemExit(
        f"managed SQLite checksum file declared {declared_digest} but the library bytes hashed to {actual_digest}"
    )
PY

RUN jlink \
    --module-path "${JAVA_HOME}/jmods" \
    --add-modules "$(cat /build/runtime-modules.txt)" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/fingrind/runtime

FROM alpine:3.24@sha256:a2d49ea686c2adfe3c992e47dc3b5e7fa6e6b5055609400dc2acaeb241c829f4

WORKDIR /workdir

RUN apk add --no-cache libstdc++

COPY --from=builder /opt/fingrind/runtime /opt/fingrind/runtime
COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/native/libsqlite3.so.0
COPY --from=builder /build/libsqlite3.so.0.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.sha256
COPY --from=builder /build/toolchain-fingerprint.json /opt/fingrind/lib/native/toolchain-fingerprint.json
COPY --from=builder /build/build-contract.json /opt/fingrind/lib/native/build-contract.json
COPY --from=builder /build/fingrind.jar /opt/fingrind/lib/app/fingrind.jar
COPY --from=builder /build/native-sqlite-format-boundary-probe.jar /opt/fingrind/lib/release-smoke/native-sqlite-format-boundary-probe.jar
COPY --from=builder /build/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh
COPY source-root/LICENSE source-root/LICENSE-APACHE-2.0 source-root/LICENSE-SIL-OFL-1.1 source-root/LICENSE-SQLITE3MULTIPLECIPHERS source-root/NOTICE source-root/PATENTS.md /opt/fingrind/doc/

RUN chmod +x /opt/fingrind/bin/docker-entrypoint.sh

ENTRYPOINT ["/opt/fingrind/bin/docker-entrypoint.sh"]
