FROM azul/zulu-openjdk-alpine:26-jre AS sqlite-builder

RUN apk add --no-cache bash build-base curl openssl tar

WORKDIR /tooling

COPY gradle.properties gradle.properties
COPY scripts/sqlite-tooling.sh scripts/sqlite-tooling.sh
COPY scripts/ensure-sqlite.sh scripts/ensure-sqlite.sh

RUN SQLITE_BIN="$(./scripts/ensure-sqlite.sh)" && install -m 755 "${SQLITE_BIN}" /usr/local/bin/sqlite3

FROM azul/zulu-openjdk-alpine:26-jre

WORKDIR /app

COPY --from=sqlite-builder /usr/local/bin/sqlite3 /usr/local/bin/sqlite3

# The fat JAR is built by the GitHub Actions workflow (./gradlew :cli:shadowJar)
# before docker build is invoked. For local use, run that command first.
COPY cli/build/libs/fingrind.jar fingrind.jar

ENV FINGRIND_SQLITE3_BINARY=/usr/local/bin/sqlite3

ENTRYPOINT ["java", "-jar", "/app/fingrind.jar"]
