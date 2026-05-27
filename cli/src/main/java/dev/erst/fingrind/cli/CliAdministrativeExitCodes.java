package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;

/** Exit-code mapping for administrative and maintenance command results. */
final class CliAdministrativeExitCodes {
  private CliAdministrativeExitCodes() {}

  static int exitCodeFor(OpenBookResult result) {
    return switch (result) {
      case OpenBookResult.Opened _ -> 0;
      case OpenBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(RekeyBookResult result) {
    return switch (result) {
      case RekeyBookResult.Rekeyed _ -> 0;
      case RekeyBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(BackupBookResult result) {
    return switch (result) {
      case BackupBookResult.BackedUp _ -> 0;
      case BackupBookResult.Rejected rejected -> exitCodeFor(rejected.rejection());
    };
  }

  static int exitCodeFor(RestoreBookResult result) {
    return switch (result) {
      case RestoreBookResult.Restored _ -> 0;
      case RestoreBookResult.Rejected rejected -> exitCodeFor(rejected.rejection());
    };
  }

  static int exitCodeFor(RekeyRollbackResult result) {
    return switch (result) {
      case RekeyRollbackResult.Inspected _ -> 0;
      case RekeyRollbackResult.Restored _ -> 0;
      case RekeyRollbackResult.Deleted _ -> 0;
      case RekeyRollbackResult.Rejected rejected -> exitCodeFor(rejected.rejection());
    };
  }

  static int exitCodeFor(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared _ -> 0;
      case DeclareAccountResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(PeriodResultTransferResult result) {
    return switch (result) {
      case PeriodResultTransferResult.Transferred _ -> 0;
      case PeriodResultTransferResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ -> 7;
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ -> 7;
      case BookMaintenanceRejection.ArtifactBusy _ -> 7;
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ -> 7;
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists _ -> 7;
      case BookMaintenanceRejection.ArtifactVerificationFailed _ -> 6;
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook _ -> 2;
      case BookMaintenanceRejection.NoRollbackArtifactsFound _ -> 2;
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired _ -> 2;
      case BookMaintenanceRejection.RollbackArtifactNotFound _ -> 2;
      case BookMaintenanceRejection.RollbackArtifactNotForBook _ -> 2;
    };
  }
}
