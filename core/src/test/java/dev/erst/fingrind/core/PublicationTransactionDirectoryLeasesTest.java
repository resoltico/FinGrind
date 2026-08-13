package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Verifies globally ordered, durable, cooperating-process publication-directory leases. */
class PublicationTransactionDirectoryLeasesTest {
  @Test
  void acquiresInPhysicalIdentityOrderAndReleasesInReverseOrder() throws Exception {
    RecordingOperations operations = new RecordingOperations();
    try (PublicationTransactionDirectoryLeases leases =
        PublicationTransactionDirectoryLeases.acquire(
            List.of(directory("zeta"), directory("alpha"), directory("beta")), operations)) {
      assertEquals(
          List.of("physical:alpha", "physical:beta", "physical:zeta"),
          leases.physicalDirectoryIdentities());
      assertEquals(List.of("open:alpha", "open:beta", "open:zeta"), operations.eventsWith("open:"));
      assertEquals(
          List.of("force-directory:alpha", "force-directory:beta", "force-directory:zeta"),
          operations.eventsWith("force-directory:"));
    }

    assertEquals(
        List.of(
            "unlock:zeta",
            "artifact-close:zeta",
            "unlock:beta",
            "artifact-close:beta",
            "unlock:alpha",
            "artifact-close:alpha"),
        operations.releaseEvents());
  }

  @Test
  void deduplicatesAliasesByPhysicalIdentityBeforeOpeningAnyLeaseArtifact() throws Exception {
    RecordingOperations operations = new RecordingOperations();
    operations.identityByDirectory.put("alias-one", "physical:shared");
    operations.identityByDirectory.put("alias-two", "physical:shared");
    operations.identityByDirectory.put("alias-zulu", "physical:shared");

    try (PublicationTransactionDirectoryLeases leases =
        PublicationTransactionDirectoryLeases.acquire(
            List.of(directory("alias-two"), directory("alias-one"), directory("alias-zulu")),
            operations)) {
      assertEquals(List.of("physical:shared"), leases.physicalDirectoryIdentities());
      assertEquals(List.of("open:alias-one"), operations.eventsWith("open:"));
    }
  }

  @Test
  void closesTheFailedArtifactThenReleasesEarlierLeasesWhenDirectoryForceFails() {
    RecordingOperations operations = new RecordingOperations();
    IOException directoryFailure = new IOException("beta directory force failed");
    IOException artifactCloseFailure = new IOException("beta artifact close failed");
    operations.forceFailures.put("beta", directoryFailure);
    operations.artifactCloseFailures.put("beta", artifactCloseFailure);

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                PublicationTransactionDirectoryLeases.acquire(
                    List.of(directory("alpha"), directory("beta"), directory("zeta")), operations));

    assertSame(directoryFailure, failure);
    assertEquals(List.of(artifactCloseFailure), List.of(failure.getSuppressed()));
    assertEquals(
        List.of(
            "open:alpha",
            "force-file:alpha",
            "force-directory:alpha",
            "lock:alpha",
            "open:beta",
            "force-file:beta",
            "force-directory:beta",
            "artifact-close:beta",
            "unlock:alpha",
            "artifact-close:alpha"),
        operations.nonIdentityEvents());
  }

  @Test
  void refusesAnIdentityThatChangesAfterTheDirectoryEntryIsDurablyForced() {
    RecordingOperations operations = new RecordingOperations();
    operations.changedIdentityAfterFirstRead.put("alpha", "physical:substituted");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                PublicationTransactionDirectoryLeases.acquire(
                    List.of(directory("alpha")), operations));

    assertTrue(String.valueOf(failure.getMessage()).contains("changed physical identity"));
    assertEquals(
        List.of("open:alpha", "force-file:alpha", "force-directory:alpha", "artifact-close:alpha"),
        operations.nonIdentityEvents());
  }

  @Test
  void refusesAnUnavailableOrOverlappingLeaseWithoutLeakingItsControlArtifact() {
    RecordingOperations unavailable = new RecordingOperations();
    unavailable.unavailableLocks.add("alpha");

    IOException unavailableFailure =
        assertThrows(
            IOException.class,
            () ->
                PublicationTransactionDirectoryLeases.acquire(
                    List.of(directory("alpha")), unavailable));

    assertTrue(String.valueOf(unavailableFailure.getMessage()).contains("another process"));
    assertEquals(
        List.of(
            "open:alpha",
            "force-file:alpha",
            "force-directory:alpha",
            "lock:alpha",
            "artifact-close:alpha"),
        unavailable.nonIdentityEvents());

    RecordingOperations overlapping = new RecordingOperations();
    overlapping.overlappingLocks.add("alpha");
    IOException overlappingFailure =
        assertThrows(
            IOException.class,
            () ->
                PublicationTransactionDirectoryLeases.acquire(
                    List.of(directory("alpha")), overlapping));

    assertTrue(overlappingFailure.getCause() instanceof OverlappingFileLockException);
    assertEquals(
        List.of(
            "open:alpha",
            "force-file:alpha",
            "force-directory:alpha",
            "lock:alpha",
            "artifact-close:alpha"),
        overlapping.nonIdentityEvents());
  }

  @Test
  void releasesEveryLeaseWhenLaterReleaseFails() throws Exception {
    RecordingOperations operations = new RecordingOperations();
    IOException betaUnlockFailure = new IOException("beta unlock failed");
    IOException betaArtifactCloseFailure = new IOException("beta artifact close failed");
    IOException alphaArtifactCloseFailure = new IOException("alpha artifact close failed");
    operations.lockCloseFailures.put("beta", betaUnlockFailure);
    operations.artifactCloseFailures.put("beta", betaArtifactCloseFailure);
    operations.artifactCloseFailures.put("alpha", alphaArtifactCloseFailure);
    PublicationTransactionDirectoryLeases leases =
        PublicationTransactionDirectoryLeases.acquire(
            List.of(directory("alpha"), directory("beta")), operations);

    IOException failure =
        assertThrows(
            IOException.class,
            () -> {
              try (leases) {
                // The configured release failure must leave this resource scope.
              }
            });

    assertSame(betaUnlockFailure, failure);
    assertEquals(
        List.of(betaArtifactCloseFailure, alphaArtifactCloseFailure),
        List.of(failure.getSuppressed()));
    assertEquals(
        List.of("unlock:beta", "artifact-close:beta", "unlock:alpha", "artifact-close:alpha"),
        operations.releaseEvents());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void productionOperationsRetainOneExactPrivateControlArtifactAndRejectLocalContention(
      @TempDir Path temporaryDirectory) throws Exception {
    Set<PosixFilePermission> ownerOnly =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    Files.setPosixFilePermissions(temporaryDirectory, ownerOnly);
    Path directory = temporaryDirectory.resolve("publication");
    Files.createDirectory(
        directory, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(ownerOnly));

    try (PublicationTransactionDirectoryLeases first =
        PublicationTransactionDirectoryLeases.acquire(List.of(directory))) {
      Path control = directory.resolve(PublicationTransactionDirectoryLeases.CONTROL_FILE_NAME);
      PrivateOutputFile.requireExistingOwnerOnly(control, PrivateOutputFile.Access.READ_WRITE);
      assertEquals(
          List.of(PrivateOutputDirectory.physicalObjectIdentity(directory)),
          first.physicalDirectoryIdentities());
      assertThrows(
          IOException.class,
          () -> PublicationTransactionDirectoryLeases.acquire(List.of(directory)));
    }

    try (PublicationTransactionDirectoryLeases ignored =
        PublicationTransactionDirectoryLeases.acquire(List.of(directory))) {
      assertTrue(
          Files.exists(directory.resolve(PublicationTransactionDirectoryLeases.CONTROL_FILE_NAME)));
    }
  }

  @Test
  void rejectsEmptyTargetsAndBlankPhysicalIdentitiesAndMakesCloseIdempotent() throws Exception {
    RecordingOperations operations = new RecordingOperations();

    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionDirectoryLeases.acquire(List.of(), operations));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionDirectoryLeaseTarget("", directory("alpha")));

    PublicationTransactionDirectoryLeases leases =
        PublicationTransactionDirectoryLeases.acquire(List.of(directory("alpha")), operations);
    try (leases) {
      leases.close();
    }
  }

  private static Path directory(String name) {
    return Path.of("publication-test", name);
  }

  /** Records one deterministic lease-acquisition attempt without using the filesystem. */
  private static final class RecordingOperations
      implements PublicationTransactionDirectoryLeaseOperations {
    private final Map<String, String> identityByDirectory = new ConcurrentHashMap<>();
    private final Map<String, String> changedIdentityAfterFirstRead = new ConcurrentHashMap<>();
    private final Map<String, IOException> forceFailures = new ConcurrentHashMap<>();
    private final Map<String, IOException> lockCloseFailures = new ConcurrentHashMap<>();
    private final Map<String, IOException> artifactCloseFailures = new ConcurrentHashMap<>();
    private final List<String> unavailableLocks = new ArrayList<>();
    private final List<String> overlappingLocks = new ArrayList<>();
    private final Map<String, Integer> identityReads = new ConcurrentHashMap<>();
    private final List<String> events = new ArrayList<>();

    @Override
    public String physicalDirectoryIdentity(Path directory) {
      String name = name(directory);
      events.add("identity:" + name);
      int reads = identityReads.merge(name, 1, Integer::sum);
      if (reads > 1 && changedIdentityAfterFirstRead.containsKey(name)) {
        return changedIdentityAfterFirstRead.get(name);
      }
      return identityByDirectory.getOrDefault(name, "physical:" + name);
    }

    @Override
    public LeaseControlArtifact openLeaseControlArtifact(Path path) {
      String name = name(java.util.Objects.requireNonNull(path.getParent(), "lease path parent"));
      events.add("open:" + name);
      return new RecordingArtifact(
          name,
          events,
          unavailableLocks,
          overlappingLocks,
          lockCloseFailures,
          artifactCloseFailures);
    }

    @Override
    public void forceDirectory(Path directory) throws IOException {
      String name = name(directory);
      events.add("force-directory:" + name);
      IOException failure = forceFailures.get(name);
      if (failure != null) {
        throw failure;
      }
    }

    List<String> eventsWith(String prefix) {
      return events.stream().filter(event -> event.startsWith(prefix)).toList();
    }

    List<String> nonIdentityEvents() {
      return events.stream().filter(event -> !event.startsWith("identity:")).toList();
    }

    List<String> releaseEvents() {
      return events.stream()
          .filter(event -> event.startsWith("unlock:") || event.startsWith("artifact-close:"))
          .toList();
    }
  }

  /** Retained fake exact artifact whose lock and handle lifecycle is fully observable. */
  private static final class RecordingArtifact
      implements PublicationTransactionDirectoryLeaseOperations.LeaseControlArtifact {
    private final String directoryName;
    private final List<String> events;
    private final Collection<String> unavailableLocks;
    private final Collection<String> overlappingLocks;
    private final Map<String, IOException> lockCloseFailures;
    private final Map<String, IOException> artifactCloseFailures;

    RecordingArtifact(
        String directoryName,
        List<String> events,
        Collection<String> unavailableLocks,
        Collection<String> overlappingLocks,
        Map<String, IOException> lockCloseFailures,
        Map<String, IOException> artifactCloseFailures) {
      this.directoryName = directoryName;
      this.events = events;
      this.unavailableLocks = unavailableLocks;
      this.overlappingLocks = overlappingLocks;
      this.lockCloseFailures = lockCloseFailures;
      this.artifactCloseFailures = artifactCloseFailures;
    }

    @Override
    public void force() {
      events.add("force-file:" + directoryName);
    }

    @Override
    public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock() {
      events.add("lock:" + directoryName);
      if (overlappingLocks.contains(directoryName)) {
        throw new OverlappingFileLockException();
      }
      if (unavailableLocks.contains(directoryName)) {
        return null;
      }
      return () -> {
        events.add("unlock:" + directoryName);
        IOException failure = lockCloseFailures.get(directoryName);
        if (failure != null) {
          throw failure;
        }
      };
    }

    @Override
    public void close() throws IOException {
      events.add("artifact-close:" + directoryName);
      IOException failure = artifactCloseFailures.get(directoryName);
      if (failure != null) {
        throw failure;
      }
    }
  }

  private static String name(Path path) {
    return path.getFileName().toString();
  }
}
