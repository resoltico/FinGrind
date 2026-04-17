FROM azul/zulu-openjdk-alpine:26.0.0-jdk AS builder

WORKDIR /build

RUN apk add --no-cache build-base python3

COPY gradle.properties gradle.properties
COPY third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000/sqlite3mc_amalgamation.c sqlite3mc_amalgamation.c

# The internal application JAR is built by the GitHub Actions workflow
# (./gradlew :cli:shadowJar) before docker build is invoked. That task also stages the
# compile-only analysis support jars under cli/build/docker/jdeps so jdeps can resolve
# package-level nullness annotations while computing the private runtime image.
# Public CLI downloads are the self-contained bundle archives, not this assembly input.
COPY cli/build/libs/fingrind.jar fingrind.jar
COPY cli/build/docker/jdeps/ jdeps/

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

RUN cc -O2 -fPIC -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION=1 \
    -DSQLITE_TEMP_STORE=3 -DSQLITE_SECURE_DELETE=1 -shared \
    -Wl,-soname,libsqlite3.so.0 -o libsqlite3.so.0 sqlite3mc_amalgamation.c -ldl -lpthread

RUN jdeps_classpath="$(find /build/jdeps -type f -name '*.jar' -print | paste -sd: -)" && \
    jdeps --multi-release 26 \
    --class-path "${jdeps_classpath}" \
    --print-module-deps /build/fingrind.jar > /build/runtime-modules.txt

RUN jlink \
    --module-path "${JAVA_HOME}/jmods" \
    --add-modules "$(cat /build/runtime-modules.txt)" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/fingrind/runtime

FROM alpine:3.22

WORKDIR /workdir

RUN apk add --no-cache libstdc++

ENV FINGRIND_SQLITE_LIBRARY=/opt/fingrind/lib/libsqlite3.so.0

COPY --from=builder /opt/fingrind/runtime /opt/fingrind/runtime
COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/libsqlite3.so.0
COPY --from=builder /build/fingrind.jar /opt/fingrind/app/fingrind.jar

ENTRYPOINT ["/opt/fingrind/runtime/bin/java", "--enable-native-access=ALL-UNNAMED", "-Dfingrind.runtime.distribution=container-image", "-jar", "/opt/fingrind/app/fingrind.jar"]
