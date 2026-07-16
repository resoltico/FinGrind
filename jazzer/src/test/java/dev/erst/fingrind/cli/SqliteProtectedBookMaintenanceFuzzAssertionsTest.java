package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Locks every fuzz-selected protected-book maintenance scenario into the regular test suite. */
class SqliteProtectedBookMaintenanceFuzzAssertionsTest {
  @TempDir Path tempDirectory;

  @Test
  void exercise_coversEveryProtectedBookMaintenanceScenario() throws Exception {
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(tempDirectory);
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {2}, tempDirectory.resolve("scenario-destination-collision")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {4}, tempDirectory.resolve("scenario-independent")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {0}, tempDirectory.resolve("scenario-legacy")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {1}, tempDirectory.resolve("scenario-secret-collision")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {3}, tempDirectory.resolve("scenario-rekey-backup-restore")));
  }

  @Test
  void defensiveAssertions_detectMismatchedMaintenanceOutcomesAndArtifacts() throws Exception {
    Path path = tempDirectory.resolve("artifact");
    Files.writeString(path, "actual");

    assertPrivateFailure(
        "requireAcceptedResult",
        new Class<?>[] {Object.class, Class.class, String.class},
        new BackupBookResult.Rejected(new BookMaintenanceRejection.SecretTargetOccupied(path)),
        BackupBookResult.BackedUp.class,
        "backup");
    assertPrivateFailure(
        "requireAcceptedResult",
        new Class<?>[] {Object.class, Class.class, String.class},
        new RestoreBookResult.Rejected(new BookMaintenanceRejection.BookDestinationOccupied(path)),
        RestoreBookResult.Restored.class,
        "restore");
    assertPrivateFailure(
        "requireSecretTargetOccupied",
        new Class<?>[] {BackupBookResult.class},
        new BackupBookResult.BackedUp(
            path, path.resolveSibling("backup"), path.resolveSibling("key")));
    assertPrivateFailure(
        "requireSecretTargetOccupied",
        new Class<?>[] {BackupBookResult.class},
        new BackupBookResult.Rejected(
            new BookMaintenanceRejection.BackupDestinationAlreadyExists(path)));
    assertPrivateFailure(
        "requireSecretTargetOccupied",
        new Class<?>[] {RekeyBookResult.class},
        new RekeyBookResult.Rekeyed(path));
    assertPrivateFailure(
        "requireSecretTargetOccupied",
        new Class<?>[] {RekeyBookResult.class},
        new RekeyBookResult.Rejected(new BookMaintenanceRejection.BookDestinationOccupied(path)));
    assertPrivateFailure(
        "requireDestinationOccupied",
        new Class<?>[] {RestoreBookResult.class},
        new RestoreBookResult.Restored(path, path.resolveSibling("key")));
    assertPrivateFailure(
        "requireDestinationOccupied",
        new Class<?>[] {RestoreBookResult.class},
        new RestoreBookResult.Rejected(new BookMaintenanceRejection.SecretTargetOccupied(path)));
    assertPrivateFailure(
        "requireUnchanged",
        new Class<?>[] {Path.class, byte[].class, String.class},
        path,
        "expected".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "expected drift rejection");
    assertPrivateFailure(
        "requireAbsent",
        new Class<?>[] {Path.class, String.class},
        path,
        "expected occupied rejection");
  }

  private static void assertPrivateFailure(
      String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
    Method method =
        SqliteProtectedBookMaintenanceFuzzAssertions.class.getDeclaredMethod(name, parameterTypes);
    method.setAccessible(true);
    InvocationTargetException exception =
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, arguments));
    assertThrows(
        IllegalStateException.class,
        () -> {
          throw java.util.Objects.requireNonNull(
              exception.getCause(), "expected assertion failure");
        });
  }
}
