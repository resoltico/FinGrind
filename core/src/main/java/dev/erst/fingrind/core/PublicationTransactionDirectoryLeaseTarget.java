package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One canonical physical-directory lease target selected before any lease-control artifact opens.
 */
record PublicationTransactionDirectoryLeaseTarget(String physicalIdentity, Path directory) {
  PublicationTransactionDirectoryLeaseTarget {
    Objects.requireNonNull(physicalIdentity, "physicalIdentity");
    if (physicalIdentity.isBlank()) {
      throw new IllegalArgumentException("physicalIdentity must be nonblank.");
    }
    directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
  }
}
