package dev.erst.fingrind.sqlite;

/** Native SQLite open policies that FinGrind maps from its command-level access intents. */
enum SqliteNativeOpenMode {
  // SQLite defines these sqlite3_open_v2 flags as stable C constants.
  READ_ONLY(0x00000001),
  READ_WRITE_EXISTING(0x00000002),
  READ_WRITE_CREATE(0x00000002 | 0x00000004),
  READ_WRITE_CREATE_EXCLUSIVE(0x00000002 | 0x00000004 | 0x00000010);

  private final int flags;

  SqliteNativeOpenMode(int flags) {
    this.flags = flags;
  }

  int flags() {
    return flags;
  }

  boolean publishesActivityMarker() {
    return this != READ_ONLY;
  }

  boolean hardensBookArtifactsOnOpen() {
    return this != READ_ONLY;
  }
}
