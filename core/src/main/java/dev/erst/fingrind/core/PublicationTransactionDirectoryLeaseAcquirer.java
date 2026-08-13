package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Acquires a complete globally ordered set of cooperating-process directory leases. */
final class PublicationTransactionDirectoryLeaseAcquirer {
  private PublicationTransactionDirectoryLeaseAcquirer() {}

  static List<PublicationTransactionHeldDirectoryLease> acquire(
      Collection<Path> directories, PublicationTransactionDirectoryLeaseOperations operations)
      throws IOException {
    List<PublicationTransactionHeldDirectoryLease> held = new ArrayList<>();
    try {
      for (PublicationTransactionDirectoryLeaseTarget target : targets(directories, operations)) {
        held.add(acquireOne(target, operations));
      }
      return held;
    } catch (IOException | RuntimeException | Error failure) {
      closeAfterFailure(held, failure);
      throw failure;
    }
  }

  private static List<PublicationTransactionDirectoryLeaseTarget> targets(
      Collection<Path> directories, PublicationTransactionDirectoryLeaseOperations operations)
      throws IOException {
    Collection<Path> checkedDirectories = Objects.requireNonNull(directories, "directories");
    if (checkedDirectories.isEmpty()) {
      throw new IllegalArgumentException("directories must not be empty.");
    }
    List<String> physicalIdentities = new ArrayList<>();
    List<Path> directoriesByPhysicalIdentity = new ArrayList<>();
    for (Path directory : checkedDirectories) {
      Path normalizedDirectory =
          Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
      String identity = operations.physicalDirectoryIdentity(normalizedDirectory);
      int existingIndex = physicalIdentities.indexOf(identity);
      if (existingIndex < 0) {
        physicalIdentities.add(identity);
        directoriesByPhysicalIdentity.add(normalizedDirectory);
      } else if (normalizedDirectory
              .toString()
              .compareTo(directoriesByPhysicalIdentity.get(existingIndex).toString())
          < 0) {
        directoriesByPhysicalIdentity.set(existingIndex, normalizedDirectory);
      }
    }
    return physicalIdentities.stream()
        .sorted(Comparator.naturalOrder())
        .map(
            identity ->
                new PublicationTransactionDirectoryLeaseTarget(
                    identity,
                    directoriesByPhysicalIdentity.get(physicalIdentities.indexOf(identity))))
        .toList();
  }

  private static PublicationTransactionHeldDirectoryLease acquireOne(
      PublicationTransactionDirectoryLeaseTarget target,
      PublicationTransactionDirectoryLeaseOperations operations)
      throws IOException {
    PublicationTransactionDirectoryLeaseOperations.LeaseControlArtifact artifact =
        operations.openLeaseControlArtifact(
            target.directory().resolve(PublicationTransactionDirectoryLeases.CONTROL_FILE_NAME));
    try {
      artifact.force();
      operations.forceDirectory(target.directory());
      requireUnchangedPhysicalIdentity(target, operations);
      return new PublicationTransactionHeldDirectoryLease(
          target.physicalIdentity(), artifact, requireExclusiveLock(artifact));
    } catch (IOException | RuntimeException | Error failure) {
      closePreservingFailure(artifact, failure);
      throw failure;
    }
  }

  private static void requireUnchangedPhysicalIdentity(
      PublicationTransactionDirectoryLeaseTarget target,
      PublicationTransactionDirectoryLeaseOperations operations)
      throws IOException {
    if (!target
        .physicalIdentity()
        .equals(operations.physicalDirectoryIdentity(target.directory()))) {
      throw new IOException(
          "Publication directory changed physical identity during lease acquisition.");
    }
  }

  private static PrivateOutputFile.HeldLock requireExclusiveLock(
      PublicationTransactionDirectoryLeaseOperations.LeaseControlArtifact artifact)
      throws IOException {
    try {
      PrivateOutputFile.@Nullable HeldLock lock = artifact.tryExclusiveLock();
      if (lock != null) {
        return lock;
      }
    } catch (OverlappingFileLockException exception) {
      throw new IOException(
          "Publication directory lease is already held by this process.", exception);
    }
    throw new IOException("Publication directory lease is already held by another process.");
  }

  private static void closeAfterFailure(
      List<PublicationTransactionHeldDirectoryLease> held, Throwable failure) {
    for (int index = held.size() - 1; index >= 0; index--) {
      closePreservingFailure(held.get(index), failure);
    }
  }

  private static void closePreservingFailure(AutoCloseable resource, Throwable failure) {
    try {
      resource.close();
    } catch (Exception | Error closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }
}
