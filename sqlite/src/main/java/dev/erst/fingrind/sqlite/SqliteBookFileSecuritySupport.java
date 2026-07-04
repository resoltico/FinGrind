package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.util.Objects;

/** Public-facing filesystem repair guidance for protected-book artifact paths. */
final class SqliteBookFileSecuritySupport {
  private SqliteBookFileSecuritySupport() {}

  static String invalidBookFilePathMessage(Path normalizedBookPath, Exception exception) {
    String detail =
        Objects.requireNonNullElse(
            exception.getMessage(), "Protected-book path validation failed.");
    return "The FinGrind protected-book path does not satisfy the filesystem contract: "
        + PublicPathHint.fromPath(normalizedBookPath).value()
        + ". "
        + detail;
  }

  static String invalidBookFilePathHint() {
    return "Choose a regular non-symlink protected-book path beneath a private owner-only parent directory. If the parent directory already exists, tighten it first; otherwise target a missing private directory so FinGrind can create it securely, then rerun the command.";
  }
}
