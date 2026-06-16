package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Shared base wrapper that binds one narrow session view to one SQLite store owner. */
class SqliteDelegatingSession {
  final SqlitePostingFactStore store;

  SqliteDelegatingSession(SqlitePostingFactStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  Path storeBookPath() {
    return store.storeBookPath();
  }

  final void closeStore() {
    store.close();
  }
}
