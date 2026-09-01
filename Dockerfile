FROM alpine:3.24@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b AS builder

ARG TARGETARCH

WORKDIR /build

COPY source-root/gradle/fingrind-build.properties /tmp/fingrind-build.properties

RUN apk add --no-cache binutils curl python3 \
    && zulu_version="$(awk -F= '$1 == "fingrindZuluVersion" { print $2; exit }' /tmp/fingrind-build.properties)" \
    && test -n "${zulu_version}" \
    && case "${TARGETARCH}" in \
        amd64) \
            zulu_archive="zulu26.32.203-ca-jdk${zulu_version}-linux_musl_x64.tar.gz"; \
            zulu_sha256="aadcca0249b6e07b06747d475ce5a0d3ab1aaaadd5acb4ae3eed0c9f942dac2e"; \
            ;; \
        arm64) \
            zulu_archive="zulu26.32.203-ca-jdk${zulu_version}-linux_musl_aarch64.tar.gz"; \
            zulu_sha256="153f5166055270744c2fe70716d68c0a5f49c643552ae0c8e3b49708a5f3accd"; \
            ;; \
        *) \
            echo "unsupported Docker target architecture: ${TARGETARCH}" >&2; \
            exit 1; \
            ;; \
    esac \
    && curl --fail --location --retry 5 --retry-all-errors --output "/tmp/${zulu_archive}" \
        "https://cdn.azul.com/zulu/bin/${zulu_archive}" \
    && echo "${zulu_sha256}  /tmp/${zulu_archive}" | sha256sum -c -s - \
    && mkdir -p /opt/zulu \
    && tar -xzf "/tmp/${zulu_archive}" --strip-components=1 -C /opt/zulu \
    && test -x /opt/zulu/bin/java \
    && test -x /opt/zulu/bin/jlink \
    && zulu_runtime_version="$(/opt/zulu/bin/java --version 2>&1)" \
    && printf '%s\n' "${zulu_runtime_version}" | grep -F "${zulu_version}" >/dev/null \
    && printf '%s  %s\n' "${zulu_sha256}" "${zulu_archive}" > /opt/zulu/FINGRIND-INPUT-JDK-BINARY-ARCHIVE.sha256 \
    && rm -f "/tmp/${zulu_archive}" /tmp/fingrind-build.properties

ENV JAVA_HOME=/opt/zulu

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

RUN "${JAVA_HOME}/bin/jlink" \
    --module-path "${JAVA_HOME}/jmods" \
    --add-modules "$(cat /build/runtime-modules.txt)" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/fingrind/runtime \
    && mkdir -p /opt/fingrind/runtime/provenance \
    && cp /opt/zulu/release /opt/fingrind/runtime/provenance/source-jdk-release \
    && cp /opt/zulu/FINGRIND-INPUT-JDK-BINARY-ARCHIVE.sha256 /opt/fingrind/runtime/provenance/input-jdk-binary-archive.sha256 \
    && cp /build/runtime-modules.txt /opt/fingrind/runtime/provenance/requested-modules.txt \
    && { \
        cd /opt/fingrind/runtime/legal; \
        find . -type l | while IFS= read -r legal_link; do \
            legal_target="$(readlink -f "${legal_link}")"; \
            case "${legal_target}" in /opt/fingrind/runtime/legal/*) ;; *) exit 1 ;; esac; \
        done; \
        find -L . -type f ! -name INDEX.sha256 | sort | while IFS= read -r legal_file; do \
            legal_digest="$(sha256sum "${legal_file}" | cut -d ' ' -f 1)"; \
            printf '%s  %s\n' "${legal_digest}" "${legal_file#./}"; \
        done; \
    } > /opt/fingrind/runtime/legal/INDEX.sha256

FROM alpine:3.24@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b

WORKDIR /workdir

ARG FINGRIND_IMAGE_VERSION=development
ARG FINGRIND_IMAGE_REVISION=main

COPY source-root/gradle/alpine-container-packages.lock.tsv /tmp/alpine-container-packages.lock.tsv

LABEL org.opencontainers.image.source="https://github.com/resoltico/FinGrind" \
    org.opencontainers.image.documentation="https://github.com/resoltico/FinGrind/blob/${FINGRIND_IMAGE_REVISION}/NOTICE" \
    org.opencontainers.image.version="${FINGRIND_IMAGE_VERSION}" \
    org.opencontainers.image.revision="${FINGRIND_IMAGE_REVISION}" \
    org.opencontainers.image.title="FinGrind"

# Record the exact final operating-system payload. The pinned base-image digest fixes the
# preinstalled set, and the inventory retains each Alpine packaging commit so recipients can map
# object code back to its source recipe. No extra C++ runtime is installed: the linked Zulu image
# and managed SQLite library depend on musl, not libstdc++ or libgcc.
RUN mkdir -p /opt/fingrind/doc \
    && { \
        printf 'package\tversion\tsource-package\taports-commit\tlicense\tupstream\n'; \
        awk 'BEGIN { RS=""; FS="\n" } { \
            package=version=origin=commit=license=upstream=""; \
            for (field=1; field<=NF; field++) { \
                if ($field ~ /^P:/) package=substr($field, 3); \
                else if ($field ~ /^V:/) version=substr($field, 3); \
                else if ($field ~ /^o:/) origin=substr($field, 3); \
                else if ($field ~ /^c:/) commit=substr($field, 3); \
                else if ($field ~ /^L:/) license=substr($field, 3); \
                else if ($field ~ /^U:/) upstream=substr($field, 3); \
            } \
            printf "%s\t%s\t%s\t%s\t%s\t%s\n", package, version, origin, commit, license, upstream; \
        }' /lib/apk/db/installed | sort; \
    } > /opt/fingrind/doc/ALPINE-PACKAGES.tsv \
    && cmp /tmp/alpine-container-packages.lock.tsv /opt/fingrind/doc/ALPINE-PACKAGES.tsv \
    && mv /tmp/alpine-container-packages.lock.tsv /opt/fingrind/doc/ALPINE-PACKAGES.lock.tsv

COPY --from=builder /opt/fingrind/runtime /opt/fingrind/runtime
COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/native/libsqlite3.so.0
COPY --from=builder /build/libsqlite3.so.0.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.sha256
COPY --from=builder /build/toolchain-fingerprint.json /opt/fingrind/lib/native/toolchain-fingerprint.json
COPY --from=builder /build/build-contract.json /opt/fingrind/lib/native/build-contract.json
COPY --from=builder /build/fingrind.jar /opt/fingrind/lib/app/fingrind.jar
COPY --from=builder /build/native-sqlite-format-boundary-probe.jar /opt/fingrind/lib/release-smoke/native-sqlite-format-boundary-probe.jar
COPY --from=builder /build/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh
COPY source-root/LICENSE source-root/LICENSE-ALPINE-CONTAINER-COMPONENTS source-root/LICENSE-APACHE-2.0 source-root/LICENSE-CC0-1.0 source-root/LICENSE-GPL-2.0 source-root/LICENSE-MPL-2.0 source-root/LICENSE-SIL-OFL-1.1 source-root/LICENSE-SQLITE3MULTIPLECIPHERS source-root/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY source-root/NOTICE source-root/NOTICE-ZULU-26.32.203 source-root/PATENTS.md source-root/SOURCE_OFFER.md /opt/fingrind/doc/

RUN chmod +x /opt/fingrind/bin/docker-entrypoint.sh

ENTRYPOINT ["/opt/fingrind/bin/docker-entrypoint.sh"]
