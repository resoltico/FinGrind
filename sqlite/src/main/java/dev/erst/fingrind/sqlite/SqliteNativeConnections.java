package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns SQLite native connection open, close, and rekey behavior for the FFM bridge. */
final class SqliteNativeConnections {
  private SqliteNativeConnections() {}

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(
        bookPath,
        bookPassphrase,
        SqliteNativeOpenMode.READ_WRITE_CREATE,
        SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode) {
    return open(bookPath, bookPassphrase, openMode, SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(openMode, "openMode");
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    int nativeOpenFlags = prepareBookPathForNativeOpen(normalizedBookPath, openMode);
    try (SqliteNativeConnectionOpening opening =
        SqliteNativeConnectionOpening.start(
            normalizedBookPath, openMode.publishesActivityMarker())) {
      SqliteMaintenanceLeaseAuthority.requireNoActiveLease(normalizedBookPath);
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment filename = arena.allocateFrom(normalizedBookPath.toString());
        int resultCode = openNativeDatabase(filename, databasePointer, nativeOpenFlags, sqliteApi);
        MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
        if (resultCode != SqliteNativeResultCode.code("OK")) {
          SqliteNativeException failure = SqliteNativeErrors.failure(resultCode, sqliteApi);
          suppressCloseFailure(databaseHandle, sqliteApi, failure);
          throw failure;
        }
        return opening.configure(databaseHandle, bookPassphrase, sqliteApi, arena);
      }
    }
  }

  /**
   * Establishes the parent only for logical CREATE modes; every other open validates an existing
   * secure parent before coordination state is touched.
   */
  private static void establishOpenParentDirectory(
      Path normalizedBookPath, SqliteNativeOpenMode openMode) {
    try {
      switch (Objects.requireNonNull(openMode, "openMode")) {
        case READ_WRITE_CREATE, READ_WRITE_CREATE_EXCLUSIVE ->
            SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedBookPath);
        case READ_ONLY, READ_WRITE_EXISTING, READ_WRITE_EXISTING_STAGE ->
            SqliteBookFileSecurity.requireExistingSecureParentDirectory(normalizedBookPath);
      }
    } catch (IOException exception) {
      throw new SqliteStorageFailureException(
          "Failed to establish the secure FinGrind SQLite book parent directory.", exception);
    }
  }

  /**
   * Establishes or validates the live-book path before SQLite receives its pathname.
   *
   * <p>New books are claimed through an exact POSIX {@code CREATE_NEW + 0600} operation, then
   * SQLite opens that now-existing path without its own create flag. Existing books are only
   * validated: changing a pathname's permissions after a same-owner replacement cannot prove that
   * the mutation still targets the object previously inspected.
   */
  static int prepareBookPathForNativeOpen(Path normalizedBookPath, SqliteNativeOpenMode openMode) {
    establishOpenParentDirectory(normalizedBookPath, openMode);
    return switch (openMode) {
      case READ_ONLY -> {
        requireSecureExistingBookFile(normalizedBookPath, false);
        yield openMode.flags();
      }
      case READ_WRITE_EXISTING, READ_WRITE_EXISTING_STAGE -> {
        requireSecureExistingBookFile(normalizedBookPath, true);
        yield openMode.flags();
      }
      case READ_WRITE_CREATE -> {
        createOrValidateBookFile(normalizedBookPath);
        yield SqliteNativeOpenMode.READ_WRITE_EXISTING.flags();
      }
      case READ_WRITE_CREATE_EXCLUSIVE -> {
        createExclusiveBookFile(normalizedBookPath);
        yield SqliteNativeOpenMode.READ_WRITE_EXISTING.flags();
      }
    };
  }

  private static void createOrValidateBookFile(Path normalizedBookPath) {
    try {
      SqliteBookFileSecurity.createNewOwnerOnlyBookFile(normalizedBookPath);
    } catch (FileAlreadyExistsException collision) {
      requireSecureExistingBookFile(normalizedBookPath, true);
    } catch (IOException exception) {
      throw bookCreationFailure(normalizedBookPath, exception);
    }
  }

  private static void createExclusiveBookFile(Path normalizedBookPath) {
    try {
      SqliteBookFileSecurity.createNewOwnerOnlyBookFile(normalizedBookPath);
    } catch (FileAlreadyExistsException collision) {
      throw new SqliteNewBookDestinationOccupiedException(normalizedBookPath, collision);
    } catch (IOException exception) {
      throw bookCreationFailure(normalizedBookPath, exception);
    }
  }

  private static void requireSecureExistingBookFile(
      Path normalizedBookPath, boolean requiresWrite) {
    try {
      SqliteBookFileSecurity.requireSecureExistingBookFile(normalizedBookPath, requiresWrite);
    } catch (IOException exception) {
      throw new SqliteStorageFailureException(
          "Failed to inspect the FinGrind SQLite book file security.", exception);
    }
  }

  private static SqliteStorageFailureException bookCreationFailure(
      Path normalizedBookPath, IOException exception) {
    return new SqliteStorageFailureException(
        "Failed to atomically create the private FinGrind SQLite book file at "
            + SqliteMachinePaths.absoluteValue(normalizedBookPath)
            + ".",
        exception);
  }

  static void close(
      MemorySegment databaseHandle,
      @Nullable SqliteNativeActivityRegistration activityRegistration,
      SqliteNativeApi sqliteApi) {
    close(databaseHandle, activityRegistration, true, sqliteApi);
  }

  private static void close(
      MemorySegment databaseHandle,
      @Nullable SqliteNativeActivityRegistration activityRegistration,
      boolean recordsConnectionClosure,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    SqliteNativeInvocation.runSqlite(
        "Failed to close the SQLite native library bridge.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3CloseV2())
                  .invoke(databaseHandle);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw SqliteNativeErrors.failure(resultCode, sqliteApi);
          }
          if (recordsConnectionClosure) {
            SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
          }
        });
  }

  private static int openNativeDatabase(
      MemorySegment filename,
      MemorySegment databasePointer,
      int nativeOpenFlags,
      SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to open the SQLite native library bridge.",
        () ->
            SqliteNativeCallAdapter.adapt(
                    SqliteNativeCalls.OpenV2Call.class, sqliteApi.sqlite3OpenV2())
                .invoke(filename, databasePointer, nativeOpenFlags, MemorySegment.NULL));
  }

  static void suppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure) {
    if (databaseHandle.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      close(databaseHandle, null, false, sqliteApi);
    } catch (RuntimeException exception) {
      primaryFailure.addSuppressed(exception);
    }
  }
}
