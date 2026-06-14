package dev.erst.fingrind.sqlite;

/** Shared thread and read-operations access for one SQLite posting-fact store read surface. */
interface SqlitePostingFactStoreReadOperationsView {
  /** Returns the thread-ownership guard for this store. */
  SqliteThreadOwner storeThreadOwner();

  /** Returns the read operations owner for this store. */
  SqliteStoreReadOperations storeReadOperations();
}
