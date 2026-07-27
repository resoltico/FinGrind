package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns one activity registration until it transfers with a successfully configured database. */
final class SqliteNativeConnectionOpening implements AutoCloseable {
  private @Nullable SqliteNativeActivityRegistration activityRegistration;

  private SqliteNativeConnectionOpening(SqliteNativeActivityRegistration activityRegistration) {
    this.activityRegistration =
        Objects.requireNonNull(activityRegistration, "activityRegistration");
  }

  static SqliteNativeConnectionOpening start(Path normalizedBookPath, boolean publishesActivity) {
    return new SqliteNativeConnectionOpening(
        SqliteNativeRuntimeActivity.recordOpeningConnection(normalizedBookPath, publishesActivity));
  }

  SqliteNativeDatabase configure(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    SqliteNativeActivityRegistration registration =
        Objects.requireNonNull(activityRegistration, "activityRegistration");
    SqliteNativeDatabase openedDatabase =
        SqliteNativeKeyConfiguration.configureOpenedDatabase(
            databaseHandle, bookPassphrase, registration, sqliteApi, arena);
    activityRegistration = null;
    return openedDatabase;
  }

  @Override
  public void close() {
    if (activityRegistration != null) {
      SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
      activityRegistration = null;
    }
  }
}
