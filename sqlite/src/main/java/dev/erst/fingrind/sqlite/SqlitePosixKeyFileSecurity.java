package dev.erst.fingrind.sqlite;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** POSIX mode bits for one regular key file or key-file parent directory. */
record SqlitePosixKeyFileSecurity(Set<PosixFilePermission> permissions)
    implements SqliteKeyFileSecurity {
  SqlitePosixKeyFileSecurity {
    permissions = Set.copyOf(permissions);
  }
}
