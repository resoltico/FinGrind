package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Signals one verified no-replace collision whose final object is not this transaction's stage. */
public final class PublicationTransactionFinalTargetOccupiedException extends IOException {
  private static final long serialVersionUID = 1L;

  private final Path finalPath;

  /** Creates the collision fact for the occupied canonical final target and its I/O cause. */
  public PublicationTransactionFinalTargetOccupiedException(Path finalPath, Throwable cause) {
    super("Publication transaction final target is already occupied.", cause);
    this.finalPath = Objects.requireNonNull(finalPath, "finalPath").toAbsolutePath().normalize();
  }

  /** Returns the canonical final target that was already occupied. */
  public Path finalPath() {
    return finalPath;
  }
}
