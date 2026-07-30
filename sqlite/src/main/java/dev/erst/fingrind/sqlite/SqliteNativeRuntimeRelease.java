package dev.erst.fingrind.sqlite;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Releases the process-scoped native runtime before discarding its verified library snapshot. */
final class SqliteNativeRuntimeRelease {
  private SqliteNativeRuntimeRelease() {}

  static void release(
      @Nullable SqliteVerifiedLibrarySnapshot verifiedSnapshot,
      SqliteRuntimeCloseSequence.CloseAction nativeShutdown,
      SqliteRuntimeCloseSequence.CloseAction libraryArenaClose,
      SqliteBestEffort.Reporter reporter) {
    if (verifiedSnapshot == null) {
      return;
    }
    release(
        nativeShutdown,
        libraryArenaClose,
        verifiedSnapshot::releaseAfterNativeRuntimeClose,
        reporter);
  }

  static void release(
      SqliteRuntimeCloseSequence.CloseAction nativeShutdown,
      SqliteRuntimeCloseSequence.CloseAction libraryArenaClose,
      SqliteRuntimeCloseSequence.CloseAction snapshotRelease,
      SqliteBestEffort.Reporter reporter) {
    SqliteRuntimeCloseSequence.CloseAction checkedNativeShutdown =
        Objects.requireNonNull(nativeShutdown, "nativeShutdown");
    SqliteRuntimeCloseSequence.CloseAction checkedLibraryArenaClose =
        Objects.requireNonNull(libraryArenaClose, "libraryArenaClose");
    SqliteRuntimeCloseSequence.CloseAction checkedSnapshotRelease =
        Objects.requireNonNull(snapshotRelease, "snapshotRelease");
    SqliteBestEffort.Reporter checkedReporter = Objects.requireNonNull(reporter, "reporter");
    if (!release(
        checkedNativeShutdown,
        "shutting down the process-scoped SQLite runtime",
        checkedReporter)) {
      return;
    }
    if (!release(
        checkedLibraryArenaClose,
        "closing the process-scoped SQLite library arena",
        checkedReporter)) {
      return;
    }
    release(
        checkedSnapshotRelease,
        "releasing the verified managed SQLite runtime snapshot",
        checkedReporter);
  }

  private static boolean release(
      SqliteRuntimeCloseSequence.CloseAction closeAction,
      String action,
      SqliteBestEffort.Reporter reporter) {
    try {
      closeAction.close();
      return true;
    } catch (RuntimeException exception) {
      SqliteBestEffort.reportCleanupFailure(action, exception, reporter);
      return false;
    }
  }
}
