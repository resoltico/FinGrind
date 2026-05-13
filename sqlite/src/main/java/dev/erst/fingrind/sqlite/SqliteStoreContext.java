package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Immutable dependency bundle for one SQLite-backed book session. */
class SqliteStoreContext {
  private final Path bookPath;
  private final SqliteStoreAccessMode accessMode;
  private final SqlitePostingReader postingReader;
  private final SqliteReportReader reportReader;
  private final SqliteBookStateReader bookStateReader;
  private final Supplier<SqliteNativeApi> sqliteApiSupplier;

  SqliteStoreContext(
      Path bookPath,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath().normalize();
    this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
    this.sqliteApiSupplier = Objects.requireNonNull(sqliteApiSupplier, "sqliteApiSupplier");
    this.postingReader = new SqlitePostingReader();
    this.reportReader = new SqliteReportReader(postingReader);
    this.bookStateReader = SqliteBookContract.BOOK_STATE_READER;
  }

  Path bookPath() {
    return bookPath;
  }

  SqliteStoreAccessMode accessMode() {
    return accessMode;
  }

  SqlitePostingReader postingReader() {
    return postingReader;
  }

  SqliteReportReader reportReader() {
    return reportReader;
  }

  SqliteBookStateReader bookStateReader() {
    return bookStateReader;
  }

  int bookFormatVersion() {
    return SqliteBookContract.FORMAT_VERSION;
  }

  String notInitializedBookMessage() {
    return SqliteBookContract.NOT_INITIALIZED_BOOK_MESSAGE;
  }

  SqliteNativeApi sqliteApi() {
    return sqliteApiSupplier.get();
  }

  SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
    return SqliteConnectionConfigurer.configureOpenedDatabase(
        SqliteNativeConnections.open(
            bookPath(), bookPassphrase, accessMode().nativeOpenMode(), sqliteApi()),
        accessMode());
  }

  SqliteNativeDatabase openConfiguredDatabaseWithoutRollbackArtifactWarning(
      SqliteBookPassphrase bookPassphrase) {
    return SqliteConnectionConfigurer.configureOpenedDatabase(
        SqliteNativeConnections.openWithoutRollbackArtifactWarning(
            bookPath(), bookPassphrase, accessMode().nativeOpenMode(), sqliteApi()),
        accessMode());
  }

  static void closeOwnedDatabase(SqliteNativeDatabase database) {
    database.close();
  }
}
