package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.BookVerification;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import java.nio.file.Path;
import java.util.Objects;

/** Owns protected-book verification mapping for SQLite-backed maintenance flows. */
final class SqliteProtectedBookVerificationSupport {
  BookVerification verifyResolvedBook(
      Path normalizedBookPath, SqliteBookPassphrase bookPassphrase) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    try (SqliteBookPassphrase verificationPassphrase = bookPassphrase.copy()) {
      return SqliteReadSessions.openResolved(normalizedBookPath, verificationPassphrase)
          .fold(
              bookSession -> inspectOpenedBook(normalizedBookPath, bookSession, bookPassphrase),
              failure ->
                  verificationFailure(
                      normalizedBookPath,
                      bookPassphrase,
                      protectedBookVerificationFailure(failure)));
    } catch (RuntimeException | Error exception) {
      bookPassphrase.close();
      throw exception;
    }
  }

  BookVerification mapInspection(Path normalizedBookPath, BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    return switch (inspection) {
      case BookLifecycleInspection.Initialized _ ->
          throw new IllegalArgumentException(
              "Initialized inspection requires one resolved verified-book handle.");
      case BookLifecycleInspection.Missing _ ->
          new VerificationFailure(normalizedBookPath, ProtectedBookVerificationFailure.MISSING);
      case BookLifecycleInspection.Existing existing ->
          new VerificationFailure(normalizedBookPath, mapInspectionFailure(existing));
    };
  }

  ProtectedBookVerificationFailure mapInspectionFailure(BookLifecycleInspection.Existing existing) {
    BookLifecycleInspection.Existing checkedExisting = Objects.requireNonNull(existing, "existing");
    if (checkedExisting.status() == BookLifecycleInspection.Status.BLANK_SQLITE) {
      return ProtectedBookVerificationFailure.BLANK_SQLITE;
    }
    if (checkedExisting.status() == BookLifecycleInspection.Status.FOREIGN_SQLITE) {
      return ProtectedBookVerificationFailure.FOREIGN_SQLITE;
    }
    if (checkedExisting.status() == BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION) {
      throw new ContractFailureException(
          ContractErrors.unsupportedBookFormatVersionFailure(
              checkedExisting.detectedBookFormatVersion(),
              checkedExisting.supportedBookFormatVersion()));
    }
    return ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND;
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

  private BookVerification inspectOpenedBook(
      Path normalizedBookPath, SqliteReadSession bookSession, SqliteBookPassphrase bookPassphrase) {
    try (SqliteReadSession ignored = bookSession) {
      BookLifecycleInspection inspection = bookSession.inspectBook();
      return switch (inspection) {
        case BookLifecycleInspection.Initialized _ ->
            new SqliteVerifiedBook(normalizedBookPath, bookPassphrase);
        case BookLifecycleInspection.Missing _ ->
            verificationFailure(
                normalizedBookPath, bookPassphrase, ProtectedBookVerificationFailure.MISSING);
        case BookLifecycleInspection.Existing existing ->
            verificationFailure(normalizedBookPath, bookPassphrase, mapInspectionFailure(existing));
      };
    } catch (RuntimeException | Error exception) {
      bookPassphrase.close();
      throw exception;
    }
  }

  private static BookVerification verificationFailure(
      Path normalizedBookPath,
      SqliteBookPassphrase bookPassphrase,
      ProtectedBookVerificationFailure verificationFailure) {
    bookPassphrase.close();
    return new VerificationFailure(normalizedBookPath, verificationFailure);
  }
}
