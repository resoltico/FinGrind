package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;

/** One directory lease whose lock and retained exact artifact must close together. */
record PublicationTransactionHeldDirectoryLease(
    String physicalIdentity,
    PublicationTransactionDirectoryLeaseOperations.LeaseControlArtifact artifact,
    PrivateOutputFile.HeldLock lock)
    implements AutoCloseable {
  PublicationTransactionHeldDirectoryLease {
    Objects.requireNonNull(physicalIdentity, "physicalIdentity");
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(lock, "lock");
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    try {
      lock.close();
    } catch (IOException closeFailure) {
      failure = closeFailure;
    }
    try {
      artifact.close();
    } catch (IOException closeFailure) {
      if (failure == null) {
        failure = closeFailure;
      } else {
        failure.addSuppressed(closeFailure);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
