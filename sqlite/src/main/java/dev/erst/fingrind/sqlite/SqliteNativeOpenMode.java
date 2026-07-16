package dev.erst.fingrind.sqlite;

/** Native SQLite open policies that FinGrind maps from its command-level access intents. */
enum SqliteNativeOpenMode {
  // SQLite defines these sqlite3_open_v2 flags as stable C constants.
  READ_ONLY(0x00000001, false),
  READ_WRITE_EXISTING(0x00000002, true),
  // A protected-book stage inherits its secure parent directory. Harden it only after SQLite
  // closes the handle, because Windows can reject an ACL mutation while that handle is open.
  READ_WRITE_EXISTING_STAGE(0x00000002, false),
  READ_WRITE_CREATE(0x00000002 | 0x00000004, true),
  READ_WRITE_CREATE_EXCLUSIVE(0x00000002 | 0x00000004 | 0x00000010, true);

  private final int flags;
  private final boolean hardensBookArtifactsOnOpen;

  SqliteNativeOpenMode(int flags, boolean hardensBookArtifactsOnOpen) {
    this.flags = flags;
    this.hardensBookArtifactsOnOpen = hardensBookArtifactsOnOpen;
  }

  int flags() {
    return flags;
  }

  boolean publishesActivityMarker() {
    return this != READ_ONLY;
  }

  boolean hardensBookArtifactsOnOpen() {
    return hardensBookArtifactsOnOpen;
  }
}
