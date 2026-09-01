package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Verifies and safely closes one just-opened database before session ownership is published. */
final class SqliteOpenedDatabaseVerification {
  private SqliteOpenedDatabaseVerification() {}

  /**
   * Verifies the authenticated envelope and rejects mutable access to immutable backup artifacts.
   */
  static void requireAdmissible(
      SqliteNativeDatabase openedDatabase, Path bookPath, SqliteStoreAccessMode accessMode) {
    try {
      requireEnvelopeAccess(openedDatabase, bookPath, accessMode);
    } catch (RuntimeException exception) {
      closeAfterVerificationFailure(openedDatabase, exception);
      throw exception;
    }
  }

  private static void requireEnvelopeAccess(
      SqliteNativeDatabase openedDatabase, Path bookPath, SqliteStoreAccessMode accessMode) {
    SqliteProtectedBookPhysicalEnvelope.EnvelopeKind envelopeKind =
        SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(openedDatabase, bookPath);
    if (envelopeKind
            == SqliteProtectedBookPhysicalEnvelope.EnvelopeKind.MANIFEST_ATTESTED_BACKUP_ARTIFACT
        && Objects.requireNonNull(accessMode, "accessMode").writesJournalMode()) {
      throw new SqliteProtectedBookVerificationException(
          new IllegalStateException(
              "A manifest-attested backup artifact is immutable and cannot be opened for mutation."));
    }
  }

  private static void closeAfterVerificationFailure(
      SqliteNativeDatabase openedDatabase, RuntimeException primaryFailure) {
    try {
      Objects.requireNonNull(openedDatabase, "openedDatabase").close();
    } catch (RuntimeException closeFailure) {
      Objects.requireNonNull(primaryFailure, "primaryFailure").addSuppressed(closeFailure);
    }
  }
}
