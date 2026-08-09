# Contributor devcontainer for FinGrind.
#
# Why this is not the runtime image:
# - VS Code remote extensions are more reliable on a glibc-based contributor image than on the
#   published runtime image.
# - Contributors need a full JDK, shell tooling, release helpers, and Docker access rather than a
#   minimal runtime-only image.
# - We still keep the contributor Java vendor aligned with CI by installing Azul Zulu 26.
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04@sha256:d94c97dd9cacf183d0a6fd12a8e87b526e9e928307674ae9c94139139c0c6eae

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

USER root

COPY gradle/fingrind-build.properties /tmp/fingrind-build.properties
COPY scripts/powershell_runtime.py \
    scripts/powershell_provisioning_cli.py \
    scripts/powershell_provisioning_tree.py \
    scripts/powershell_runtime_archives.py \
    scripts/powershell_runtime_cache.py \
    scripts/powershell_runtime_download.py \
    scripts/powershell_runtime_installation.py \
    scripts/powershell_runtime_metadata.py \
    scripts/powershell_runtime_models.py \
    /tmp/
COPY scripts/provision-powershell-runtime.py /tmp/provision-powershell-runtime.py

RUN export DEBIAN_FRONTEND=noninteractive \
    && retry() { \
        local attempt=1 max_attempts=5; \
        until "$@"; do \
            local exit_code=$?; \
            if (( attempt >= max_attempts )); then \
                return "${exit_code}"; \
            fi; \
            sleep $(( attempt * 2 )); \
            attempt=$(( attempt + 1 )); \
        done; \
    } \
    && retry apt-get update \
    && retry apt-get install -y --no-install-recommends \
        curl \
        fontconfig \
        fonts-dejavu \
        gh \
        git \
        gnupg \
        jq \
        less \
        lsof \
        procps \
        python3 \
        python3-pip \
        shellcheck \
        unzip \
        zip \
    && retry curl -fsSL \
        --connect-timeout 15 \
        --retry 5 \
        --retry-all-errors \
        --retry-delay 2 \
        --output /tmp/azul-repo.key \
        https://repos.azul.com/azul-repo.key \
    && gpg --dearmor -o /usr/share/keyrings/azul.gpg /tmp/azul-repo.key \
    && chmod 644 /usr/share/keyrings/azul.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" \
        > /etc/apt/sources.list.d/zulu.list \
    && retry apt-get update \
    && zulu_version="$(awk -F= '$1 == "fingrindZuluVersion" { print $2; exit }' /tmp/fingrind-build.properties)" \
    && [[ -n "${zulu_version}" ]] \
    && retry apt-get install -y --no-install-recommends "zulu26-jdk=${zulu_version}-1" \
    && zulu_home="$(find /usr/lib/jvm -maxdepth 1 -mindepth 1 -type d -name 'zulu26*' | sort | head -1)" \
    && [[ -n "${zulu_home}" ]] \
    && ln -sfn "${zulu_home}" /usr/lib/jvm/zulu-26 \
    && test -x /usr/lib/jvm/zulu-26/bin/java \
    && test -x /usr/lib/jvm/zulu-26/bin/javac \
    && fc-cache -f >/dev/null \
    && java_version="$(java --version 2>&1)" \
    && [[ "${java_version}" == *"${zulu_version}"* ]] \
    && javac --version >/dev/null \
    && git --version >/dev/null \
    && shellcheck --version >/dev/null \
    && pwsh_path="$(python3 /tmp/provision-powershell-runtime.py \
        --metadata /tmp/fingrind-build.properties \
        --install-root /opt/fingrind/powershell)" \
    && required_pwsh_version="$(python3 /tmp/provision-powershell-runtime.py \
        --metadata /tmp/fingrind-build.properties \
        --print-version)" \
    && test ! -e /usr/local/bin/pwsh \
    && ln -s "${pwsh_path}" /usr/local/bin/pwsh \
    && actual_pwsh_version="$(pwsh -NoLogo -NoProfile -NonInteractive \
        -Command '$PSVersionTable.PSVersion.ToString()')" \
    && [[ "${actual_pwsh_version}" == "${required_pwsh_version}" ]] \
    && rm -f /tmp/azul-repo.key \
    && rm -f /tmp/fingrind-build.properties \
        /tmp/powershell_provisioning_cli.py \
        /tmp/powershell_provisioning_tree.py \
        /tmp/powershell_runtime.py \
        /tmp/powershell_runtime_archives.py \
        /tmp/powershell_runtime_cache.py \
        /tmp/powershell_runtime_download.py \
        /tmp/powershell_runtime_installation.py \
        /tmp/powershell_runtime_metadata.py \
        /tmp/powershell_runtime_models.py \
        /tmp/provision-powershell-runtime.py \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/zulu-26
ENV PATH=${JAVA_HOME}/bin:${PATH}

USER vscode
