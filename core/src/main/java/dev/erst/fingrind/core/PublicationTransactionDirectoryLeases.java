package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Holds every cooperating-process publication-directory lease for one transaction attempt.
 *
 * <p>Lease order is global physical-directory identity order. This is a coordination mechanism, not
 * a defense against hostile mutation by the same operating-system user.
 */
final class PublicationTransactionDirectoryLeases implements AutoCloseable {
  static final String CONTROL_FILE_NAME = ".fingrind-publication-lease-v1.lock";

  private final List<PublicationTransactionHeldDirectoryLease> heldDirectories;
  private boolean closed;

  private PublicationTransactionDirectoryLeases(
      List<PublicationTransactionHeldDirectoryLease> heldDirectories) {
    this.heldDirectories = List.copyOf(heldDirectories);
  }

  static PublicationTransactionDirectoryLeases acquire(Collection<Path> directories)
      throws IOException {
    return acquire(directories, PublicationTransactionDirectoryLeaseProductionOperations.INSTANCE);
  }

  static PublicationTransactionDirectoryLeases acquire(
      Collection<Path> directories, PublicationTransactionDirectoryLeaseOperations operations)
      throws IOException {
    return new PublicationTransactionDirectoryLeases(
        PublicationTransactionDirectoryLeaseAcquirer.acquire(
            directories, Objects.requireNonNull(operations, "operations")));
  }

  List<String> physicalDirectoryIdentities() {
    return heldDirectories.stream()
        .map(PublicationTransactionHeldDirectoryLease::physicalIdentity)
        .toList();
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException failure = null;
    for (int index = heldDirectories.size() - 1; index >= 0; index--) {
      try {
        heldDirectories.get(index).close();
      } catch (IOException closeFailure) {
        if (failure == null) {
          failure = closeFailure;
        } else {
          failure.addSuppressed(closeFailure);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
