package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Owns a decoded backup snapshot while attestation verification decides whether its resources may
 * transfer to the durable verification result.
 */
final class SqliteVerifiedBackupSnapshot implements AutoCloseable {
  private final SqliteOwnedStagedArtifact stage;
  private @Nullable SqliteVerifiedBook book;
  private boolean transferred;

  SqliteVerifiedBackupSnapshot(SqliteOwnedStagedArtifact stage) {
    this.stage = Objects.requireNonNull(stage, "stage");
  }

  Path stagedPath() {
    return stage.stagedPath();
  }

  void attachBook(SqliteVerifiedBook book) {
    this.book = Objects.requireNonNull(book, "book");
  }

  SqliteVerifiedBook book() {
    return Objects.requireNonNull(book, "book");
  }

  SqliteVerifiedBackupArtifact transfer(AttestationBackupArtifactVerification verification) {
    SqliteVerifiedBackupArtifact artifact =
        new SqliteVerifiedBackupArtifact(
            stage, book(), Objects.requireNonNull(verification, "verification"));
    transferred = true;
    return artifact;
  }

  @Override
  public void close() {
    if (!transferred) {
      try {
        if (book != null) {
          book.close();
        }
      } finally {
        stage.discard();
      }
    }
  }
}
