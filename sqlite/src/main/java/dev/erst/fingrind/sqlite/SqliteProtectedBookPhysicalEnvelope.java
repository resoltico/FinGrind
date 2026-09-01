package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies the complete physical envelope of an opened protected book.
 *
 * <p>A live book is exactly its SQLite pages. A longer file is admissible only when its complete
 * contents form a manifest-attested backup artifact whose snapshot is those exact pages. This
 * preserves the intentionally self-describing backup container while refusing arbitrary outer bytes
 * on every ordinary book-read surface.
 */
final class SqliteProtectedBookPhysicalEnvelope {
  private SqliteProtectedBookPhysicalEnvelope() {}

  static EnvelopeKind requireAuthenticatedEnvelope(SqliteNativeDatabase database, Path bookPath) {
    try {
      long pageSize = pragmaPositiveLong(database, "pragma page_size");
      long pageCount = pragmaNonNegativeLong(database, "pragma page_count");
      long expectedLength = Math.multiplyExact(pageSize, pageCount);
      Path checkedBookPath = Objects.requireNonNull(bookPath, "bookPath");
      long actualLength = Files.size(checkedBookPath);
      if (actualLength == expectedLength) {
        return EnvelopeKind.LIVE_BOOK;
      }
      if (actualLength < expectedLength) {
        throw envelopeFailure();
      }
      return requireManifestAttestedBackupArtifact(database, checkedBookPath, expectedLength);
    } catch (IOException | ArithmeticException | NumberFormatException exception) {
      throw new SqliteProtectedBookVerificationException(exception);
    }
  }

  private static EnvelopeKind requireManifestAttestedBackupArtifact(
      SqliteNativeDatabase database, Path bookPath, long expectedSnapshotLength)
      throws IOException {
    AtomicBoolean snapshotLengthMatches = new AtomicBoolean();
    try {
      AttestationBackupArtifact.verify(
          Files.readAllBytes(bookPath),
          snapshot -> {
            snapshotLengthMatches.set(snapshot.length == expectedSnapshotLength);
            return SqliteAttestationEvidenceStore.loadAll(database);
          });
    } catch (RuntimeException exception) {
      throw new SqliteProtectedBookVerificationException(exception);
    }
    if (!snapshotLengthMatches.get()) {
      throw envelopeFailure();
    }
    return EnvelopeKind.MANIFEST_ATTESTED_BACKUP_ARTIFACT;
  }

  private static SqliteProtectedBookVerificationException envelopeFailure() {
    return new SqliteProtectedBookVerificationException(
        new IllegalStateException("Protected-book physical envelope does not match SQLite pages."));
  }

  /** Physical-envelope category established after protected-book authentication. */
  enum EnvelopeKind {
    /** The file contains exactly one authenticated live SQLite page sequence. */
    LIVE_BOOK,
    /** The longer file is a complete manifest-attested backup artifact. */
    MANIFEST_ATTESTED_BACKUP_ARTIFACT
  }

  private static long pragmaPositiveLong(SqliteNativeDatabase database, String sql) {
    long value = Long.parseLong(SqliteStatementQueries.querySingleText(database, sql));
    if (value <= 0L) {
      throw new IllegalStateException(
          "SQLite reported a non-positive protected-book page dimension.");
    }
    return value;
  }

  private static long pragmaNonNegativeLong(SqliteNativeDatabase database, String sql) {
    long value = Long.parseLong(SqliteStatementQueries.querySingleText(database, sql));
    if (value < 0L) {
      throw new IllegalStateException("SQLite reported a negative protected-book page count.");
    }
    return value;
  }
}
