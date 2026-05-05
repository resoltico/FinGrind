FROM azul/zulu-openjdk-alpine:26.0.1-jdk AS builder

WORKDIR /build

RUN apk add --no-cache build-base python3

COPY gradle.properties gradle.properties
COPY cli/build/docker-context/ /build/docker-context/
COPY scripts/render-managed-sqlite-compiler-flags.py scripts/render-managed-sqlite-compiler-flags.py
COPY scripts/verify-docker-build-context.py scripts/verify-docker-build-context.py
COPY third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000/sqlite3mc_amalgamation.c sqlite3mc_amalgamation.c

# The canonical Docker assembly input is built by ./gradlew :cli:stageDockerBuildContext before
# docker build is invoked. That staged directory carries the internal application JAR, the
# canonical private-runtime module list, the rendered entrypoint, the managed-SQLite contract,
# and the generated build-context manifest so container assembly consumes one checked context
# instead of re-listing parallel file owners in the Dockerfile.
# Public CLI downloads are the self-contained bundle archives, not this assembly input.

RUN python3 - <<'PY'
from hashlib import sha3_256
from pathlib import Path
expected = None
for line in Path("gradle.properties").read_text(encoding="utf-8").splitlines():
    if line.startswith("fingrindManagedSqliteSourceSha3="):
        expected = line.split("=", 1)[1].strip()
        break
if not expected:
    raise SystemExit("missing fingrindManagedSqliteSourceSha3 in gradle.properties")
source = Path("sqlite3mc_amalgamation.c").read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n")
actual = sha3_256(source).hexdigest()
if actual != expected:
    raise SystemExit(
        f"vendored SQLite source hash mismatch: expected {expected} but found {actual}"
    )
PY

RUN python3 scripts/verify-docker-build-context.py --context-dir /build/docker-context

RUN cc -O2 -fPIC $(python3 scripts/render-managed-sqlite-compiler-flags.py /build/docker-context/managed-sqlite-contract.json) -shared \
    -Wl,-soname,libsqlite3.so.0 -o libsqlite3.so.0 sqlite3mc_amalgamation.c -ldl -lpthread

RUN jlink \
    --module-path "${JAVA_HOME}/jmods" \
    --add-modules "$(cat /build/docker-context/runtime-modules.txt)" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/fingrind/runtime

FROM alpine:3.23

WORKDIR /workdir

RUN apk add --no-cache libstdc++

ENV FINGRIND_SQLITE_LIBRARY=/opt/fingrind/lib/libsqlite3.so.0

COPY --from=builder /opt/fingrind/runtime /opt/fingrind/runtime
COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/libsqlite3.so.0
COPY --from=builder /build/docker-context/fingrind.jar /opt/fingrind/app/fingrind.jar
COPY --from=builder /build/docker-context/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh
COPY LICENSE LICENSE-APACHE-2.0 LICENSE-SIL-OFL-1.1 LICENSE-SQLITE3MULTIPLECIPHERS NOTICE PATENTS.md /opt/fingrind/doc/

RUN chmod +x /opt/fingrind/bin/docker-entrypoint.sh

ENTRYPOINT ["/opt/fingrind/bin/docker-entrypoint.sh"]
