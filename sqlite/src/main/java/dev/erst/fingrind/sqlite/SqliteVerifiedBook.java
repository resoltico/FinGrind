package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.Objects;

/** Verified SQLite protected-book handle that retains one resolved secret for one workflow. */
final class SqliteVerifiedBook implements ProtectedBookMaintenanceStore.VerifiedBook {
  private final Path artifactPath;
  private final SqliteBookPassphrase bookPassphrase;

  SqliteVerifiedBook(Path artifactPath, SqliteBookPassphrase bookPassphrase) {
    this.artifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
    this.bookPassphrase = Objects.requireNonNull(bookPassphrase, "bookPassphrase");
  }

  @Override
  public Path artifactPath() {
    return artifactPath;
  }

  SqliteBookPassphrase passphraseCopy() {
    return bookPassphrase.copy();
  }

  @Override
  public void close() {
    bookPassphrase.close();
  }
}
