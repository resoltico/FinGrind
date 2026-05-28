package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.BookVerification;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import java.nio.file.Path;
import java.util.Objects;

/** Owns protected-book verification mapping for SQLite-backed maintenance flows. */
final class SqliteProtectedBookVerificationSupport {
  MaintenanceDecision<BookVerification> verifyResolvedBook(
      Path normalizedBookPath, SqliteBookPassphrase bookPassphrase) {
    try (SqliteBookPassphrase ignored = bookPassphrase) {
      return SqliteReadSessions.openResolved(normalizedBookPath, bookPassphrase)
          .fold(
              bookSession -> inspectOpenedBook(normalizedBookPath, bookSession),
              failure ->
                  MaintenanceDecision.accepted(
                      new VerificationFailure(
                          normalizedBookPath, protectedBookVerificationFailure(failure))));
    }
  }

  BookVerification mapInspection(Path normalizedBookPath, BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    return switch (inspection) {
      case BookLifecycleInspection.Initialized _ ->
          new ProtectedBookMaintenanceStore.VerifiedBook(normalizedBookPath);
      case BookLifecycleInspection.Missing _ ->
          new VerificationFailure(normalizedBookPath, ProtectedBookVerificationFailure.MISSING);
      case BookLifecycleInspection.Existing existing ->
          new VerificationFailure(normalizedBookPath, mapInspectionFailure(existing.status()));
    };
  }

  ProtectedBookVerificationFailure mapInspectionFailure(BookLifecycleInspection.Status status) {
    return switch (Objects.requireNonNull(status, "status")) {
      case MISSING -> ProtectedBookVerificationFailure.MISSING;
      case BLANK_SQLITE -> ProtectedBookVerificationFailure.BLANK_SQLITE;
      case FOREIGN_SQLITE -> ProtectedBookVerificationFailure.FOREIGN_SQLITE;
      case UNSUPPORTED_FORMAT_VERSION ->
          ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION;
      case INCOMPLETE_FINGRIND -> ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND;
      case INITIALIZED ->
          throw new IllegalArgumentException("INITIALIZED is not one rejection inspection status.");
    };
  }

  static ProtectedBookVerificationFailure protectedBookVerificationFailure(
      dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    if (!ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED
        .code()
        .equals(Objects.requireNonNull(failure, "failure").code())) {
      throw new IllegalStateException(
          "SQLite read-session verification rejected with one non-verification contract failure: "
              + failure.code());
    }
    return ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED;
  }

  private MaintenanceDecision<BookVerification> inspectOpenedBook(
      Path normalizedBookPath, SqliteReadSession bookSession) {
    try (SqliteReadSession ignored = bookSession) {
      BookLifecycleInspection inspection = bookSession.inspectBook();
      return MaintenanceDecision.accepted(mapInspection(normalizedBookPath, inspection));
    }
  }
}
