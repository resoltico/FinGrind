FROM azul/zulu-openjdk-alpine:26-jre AS sqlite-builder

WORKDIR /build/sqlite

RUN apk add --no-cache build-base

COPY third_party/sqlite/sqlite-amalgamation-3530000/sqlite3.c sqlite3.c
COPY third_party/sqlite/sqlite-amalgamation-3530000/sqlite3.h sqlite3.h
COPY third_party/sqlite/sqlite-amalgamation-3530000/sqlite3ext.h sqlite3ext.h

RUN cc -O2 -fPIC -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION=1 -shared \
    -Wl,-soname,libsqlite3.so.0 -o libsqlite3.so.0 sqlite3.c -ldl -lpthread

FROM azul/zulu-openjdk-alpine:26-jre

WORKDIR /app

ENV FINGRIND_SQLITE_LIBRARY=/opt/fingrind/lib/libsqlite3.so.0

COPY --from=sqlite-builder /build/sqlite/libsqlite3.so.0 /opt/fingrind/lib/libsqlite3.so.0

# The fat JAR is built by the GitHub Actions workflow (./gradlew :cli:shadowJar)
# before docker build is invoked. For local use, run that command first.
COPY cli/build/libs/fingrind.jar fingrind.jar

ENTRYPOINT ["java", "-jar", "/app/fingrind.jar"]
