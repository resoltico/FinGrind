package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;

/** Local maintenance access tuple for one protected book path and local passphrase source. */
public record ProtectedBookAccess(
    Path bookFilePath, ProtectedBookPassphraseSource passphraseSource) {
  public ProtectedBookAccess {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(passphraseSource, "passphraseSource");
  }

  /** Projects one published book access tuple into the local maintenance access shape. */
  public static ProtectedBookAccess fromPublished(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return new ProtectedBookAccess(
        bookAccess.bookFilePath(),
        ProtectedBookPassphraseSource.fromPublished(bookAccess.passphraseSource()));
  }

  /** Projects this local maintenance access back into the published contract shape. */
  public BookAccess toPublished() {
    return new BookAccess(bookFilePath, passphraseSource.toPublished(), java.util.List.of());
  }
}
