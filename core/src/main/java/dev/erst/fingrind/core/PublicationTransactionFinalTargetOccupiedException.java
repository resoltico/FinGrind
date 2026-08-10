package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Signals one verified no-replace collision whose final object is not this transaction's stage. */
final class PublicationTransactionFinalTargetOccupiedException extends IOException {
  private static final long serialVersionUID = 1L;

  private final Path finalPath;

  PublicationTransactionFinalTargetOccupiedException(Path finalPath, Throwable cause) {
    super("Publication transaction final target is already occupied.", cause);
    this.finalPath = Objects.requireNonNull(finalPath, "finalPath").toAbsolutePath().normalize();
  }

  Path finalPath() {
    return finalPath;
  }
}
