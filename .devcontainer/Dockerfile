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
    && retry apt-get install -y --no-install-recommends zulu26-jdk \
    && zulu_home="$(find /usr/lib/jvm -maxdepth 1 -mindepth 1 -type d -name 'zulu26*' | sort | head -1)" \
    && [[ -n "${zulu_home}" ]] \
    && ln -sfn "${zulu_home}" /usr/lib/jvm/zulu-26 \
    && test -x /usr/lib/jvm/zulu-26/bin/java \
    && test -x /usr/lib/jvm/zulu-26/bin/javac \
    && fc-cache -f >/dev/null \
    && java --version >/dev/null \
    && javac --version >/dev/null \
    && git --version >/dev/null \
    && shellcheck --version >/dev/null \
    && rm -f /tmp/azul-repo.key \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/zulu-26
ENV PATH=${JAVA_HOME}/bin:${PATH}

USER vscode
