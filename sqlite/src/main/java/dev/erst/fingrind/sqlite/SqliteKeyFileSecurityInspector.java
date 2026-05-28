package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;

/** Reads one native filesystem security descriptor for a key file or its parent directory. */
@FunctionalInterface
interface SqliteKeyFileSecurityInspector {
  /** Returns the security model and permissions visible for the supplied path. */
  SqliteKeyFileSecurity inspect(Path path) throws IOException;
}
