package dev.erst.fingrind.sqlite;

/** Structured failure raised by the in-process SQLite native adapter. */
final class SqliteNativeException extends RuntimeException {
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
    return SqliteNativeErrors.resultName(resultCode);
  }
}
