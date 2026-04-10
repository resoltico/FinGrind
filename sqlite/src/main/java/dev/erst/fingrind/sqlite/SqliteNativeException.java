package dev.erst.fingrind.sqlite;

/** Checked failure raised by the in-process SQLite native adapter. */
final class SqliteNativeException extends Exception {
  private static final long serialVersionUID = 1L;

  private final int resultCode;

  SqliteNativeException(int resultCode, String message) {
    super(message);
    this.resultCode = resultCode;
  }

  int resultCode() {
    return resultCode;
  }

  String resultName() {
    return SqliteNativeLibrary.resultName(resultCode);
  }
}
