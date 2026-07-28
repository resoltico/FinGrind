package dev.erst.fingrind.sqlite;

/** Native SQLite open policies that FinGrind maps from its command-level access intents. */
enum SqliteNativeOpenMode {
  // SQLite defines these sqlite3_open_v2 flags as stable C constants.
  READ_ONLY(0x00000001, false),
  READ_WRITE_EXISTING(0x00000002, false),
  // A protected-book stage is created atomically with owner-only permissions. Do not repair its
  // access control by pathname after SQLite closes: Java NIO cannot bind that mutation to the
  // originally created stage across a same-owner rename.
  READ_WRITE_EXISTING_STAGE(0x00000002, false),
  READ_WRITE_CREATE(0x00000002 | 0x00000004, true),
  READ_WRITE_CREATE_EXCLUSIVE(0x00000002 | 0x00000004 | 0x00000010, true);

  private final int flags;
  private final boolean createsParentDirectory;

  SqliteNativeOpenMode(int flags, boolean createsParentDirectory) {
    this.flags = flags;
    this.createsParentDirectory = createsParentDirectory;
  }

  int flags() {
    return flags;
  }

  boolean createsParentDirectory() {
    return createsParentDirectory;
  }

  boolean publishesActivityMarker() {
    return this != READ_ONLY;
  }
}
