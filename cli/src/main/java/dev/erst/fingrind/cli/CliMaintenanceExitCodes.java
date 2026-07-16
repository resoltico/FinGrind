package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;

/** Owns exit-code classification for the protected-book maintenance rejection family. */
final class CliMaintenanceExitCodes {
  private CliMaintenanceExitCodes() {}

  static int exitCodeFor(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.MaintenanceStateConflict _ -> 7;
      case BookMaintenanceRejection.MaintenanceArtifactInvalid _ -> 6;
      case BookMaintenanceRejection.MaintenanceRequestInvalid _ -> 2;
    };
  }
}
