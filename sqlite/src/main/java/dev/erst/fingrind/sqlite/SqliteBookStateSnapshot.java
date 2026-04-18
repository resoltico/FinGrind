package dev.erst.fingrind.sqlite;

/** One cached snapshot of the selected SQLite book header and interpreted lifecycle state. */
record SqliteBookStateSnapshot(int applicationId, int userVersion, SqliteBookState state) {}
