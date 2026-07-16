package dev.erst.fingrind.sqlite;

/** Public-facing filesystem repair guidance for protected-book artifact paths. */
final class SqliteBookFileSecuritySupport {
  private SqliteBookFileSecuritySupport() {}

  static String invalidBookFilePathMessage() {
    return "The FinGrind protected-book path does not satisfy the filesystem contract.";
  }

  static String invalidBookFilePathHint() {
    return "Choose a regular non-symlink protected-book path beneath a private owner-only parent directory. If the parent directory already exists, tighten it first; otherwise target a missing private directory so FinGrind can create it securely, then rerun the command.";
  }
}
