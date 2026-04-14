FROM azul/zulu-openjdk-alpine:26-jre AS sqlite-builder

WORKDIR /build/sqlite

RUN apk add --no-cache build-base

COPY third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000/sqlite3mc_amalgamation.c sqlite3mc_amalgamation.c

RUN cc -O2 -fPIC -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION=1 \
    -DSQLITE_TEMP_STORE=3 -DSQLITE_SECURE_DELETE=1 -shared \
    -Wl,-soname,libsqlite3.so.0 -o libsqlite3.so.0 sqlite3mc_amalgamation.c -ldl -lpthread

FROM azul/zulu-openjdk-alpine:26-jre

WORKDIR /app

ENV FINGRIND_SQLITE_LIBRARY=/opt/fingrind/lib/libsqlite3.so.0

COPY --from=sqlite-builder /build/sqlite/libsqlite3.so.0 /opt/fingrind/lib/libsqlite3.so.0

# The fat JAR is built by the GitHub Actions workflow (./gradlew :cli:shadowJar)
# before docker build is invoked. For local use, run that command first.
COPY cli/build/libs/fingrind.jar fingrind.jar

ENTRYPOINT ["java", "-jar", "/app/fingrind.jar"]
