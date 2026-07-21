package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;

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
      case RekeyBookResult.Rejected rejected -> exitCodeFor(rejected.rejection());
    };
  }

  static int exitCodeFor(BackupBookResult result) {
    return switch (result) {
      case BackupBookResult.BackedUp _ -> 0;
      case BackupBookResult.AcknowledgementPending _ -> 4;
      case BackupBookResult.Rejected rejected -> exitCodeFor(rejected.rejection());
    };
  }

  static int exitCodeFor(RestoreBookResult result) {
    return switch (result) {
      case RestoreBookResult.Restored _ -> 0;
      case RestoreBookResult.Rejected rejected -> exitCodeFor(rejected.rejection());
    };
  }

  static int exitCodeFor(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared _ -> 0;
      case DeclareAccountResult.Reactivated _ -> 0;
      case DeclareAccountResult.Renamed _ -> 0;
      case DeclareAccountResult.Unchanged _ -> 0;
      case DeclareAccountResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(AmendAccountResult result) {
    return switch (result) {
      case AmendAccountResult.Amended _ -> 0;
      case AmendAccountResult.Unchanged _ -> 0;
      case AmendAccountResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(RetireAccountResult result) {
    return switch (result) {
      case RetireAccountResult.Retired _ -> 0;
      case RetireAccountResult.Unchanged _ -> 0;
      case RetireAccountResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(DeclareTaxRegistrationResult result) {
    return switch (result) {
      case DeclareTaxRegistrationResult.Declared _ -> 0;
      case DeclareTaxRegistrationResult.Updated _ -> 0;
      case DeclareTaxRegistrationResult.Unchanged _ -> 0;
      case DeclareTaxRegistrationResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(InterimResultSweepResult result) {
    return switch (result) {
      case InterimResultSweepResult.Swept _ -> 0;
      case InterimResultSweepResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(FiscalYearCloseResult result) {
    return switch (result) {
      case FiscalYearCloseResult.Closed _ -> 0;
      case FiscalYearCloseResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(BookMaintenanceRejection rejection) {
    return CliMaintenanceExitCodes.exitCodeFor(rejection);
  }
}
