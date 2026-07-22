package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;

/** Verifies the typed outcomes expected by protected-book maintenance fuzz scenarios. */
final class SqliteProtectedBookMaintenanceOutcomeAssertions {
  private SqliteProtectedBookMaintenanceOutcomeAssertions() {}

  static void requireAcceptedResult(Object result, Class<?> expectedResultType, String operation) {
    if (!expectedResultType.isInstance(result)) {
      throw new IllegalStateException(
          "Expected the protected-book " + operation + " scenario to succeed: " + result);
    }
  }

  static void requireSecretTargetOccupied(BackupBookResult result) {
    if (!(result
        instanceof BackupBookResult.Rejected(BookMaintenanceRejection.SecretTargetOccupied _))) {
      throw new IllegalStateException(
          "Expected the backup key target to be rejected as occupied: " + result);
    }
  }

  static void requireSecretTargetOccupied(RekeyBookResult result) {
    if (!(result
        instanceof RekeyBookResult.Rejected(BookMaintenanceRejection.SecretTargetOccupied _))) {
      throw new IllegalStateException(
          "Expected the rekey key target to be rejected as occupied: " + result);
    }
  }

  static void requireDestinationOccupied(RestoreBookResult result) {
    if (!(result
        instanceof
        RestoreBookResult.Rejected(BookMaintenanceRejection.BookDestinationOccupied _))) {
      throw new IllegalStateException(
          "Expected the restore destination to be rejected as occupied: " + result);
    }
  }

  static void requireArtifactVerificationFailure(RestoreBookResult result) {
    if (!(result
        instanceof
        RestoreBookResult.Rejected(BookMaintenanceRejection.ArtifactVerificationFailed _))) {
      throw new IllegalStateException(
          "Expected an unattested backup pair to be rejected by artifact verification: " + result);
    }
  }
}
