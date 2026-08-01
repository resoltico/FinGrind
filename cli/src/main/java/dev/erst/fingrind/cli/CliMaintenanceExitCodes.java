package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;

/** Owns exit-code classification for the protected-book maintenance rejection family. */
final class CliMaintenanceExitCodes {
  private CliMaintenanceExitCodes() {}

  static int exitCodeFor(BookMaintenanceRejection rejection) {
    return BookMaintenanceRejection.exitCode(rejection);
  }
}
