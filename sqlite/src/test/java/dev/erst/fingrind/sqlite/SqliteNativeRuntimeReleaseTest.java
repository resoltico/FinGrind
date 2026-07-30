package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for releasing a verified native SQLite runtime snapshot at process shutdown.
 */
class SqliteNativeRuntimeReleaseTest {
  @Test
  void release_releasesTheInitializedDefaultRuntimeInItsOwningProcess()
      throws IOException, InterruptedException {
    assertChildRuntimeReleasesSnapshot();
  }

  @Test
  void release_releasesTheEnvironmentProbeRuntimeInItsOwningProcess()
      throws IOException, InterruptedException {
    assertChildRuntimeReleasesSnapshot("probe");
  }

  @Test
  void processEndFallback_registersOnlyOneRuntimeReleaseHook() {
    AtomicBoolean hookInstalled = new AtomicBoolean();
    AtomicInteger registeredHookCount = new AtomicInteger();

    assertTrue(
        SqliteNativeBootstrap.installRuntimeReleaseHookOnce(
            hookInstalled, registeredHookCount::incrementAndGet));
    assertFalse(
        SqliteNativeBootstrap.installRuntimeReleaseHookOnce(
            hookInstalled, registeredHookCount::incrementAndGet));
    assertEquals(1, registeredHookCount.get());
  }

  @Test
  void processEndFallback_releasesTheInitializedRuntimeWhenTheCallerDoesNotCloseIt()
      throws IOException, InterruptedException {
    Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir"));
    Set<Path> snapshotsBefore = managedSnapshots(temporaryDirectory);

    Process process =
        new ProcessBuilder(
                SqliteChildJvmSupport.childJavaCommand(
                    SqliteRuntimeReleaseProcessProbe.class, "shutdown-hook"))
            .redirectErrorStream(true)
            .start();
    String output;
    int exitCode;
    try (process;
        InputStream processOutput = process.getInputStream()) {
      output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8);
      exitCode = process.waitFor();
    }

    assertEquals(0, exitCode, () -> "Child runtime output:\n" + output);
    assertEquals(snapshotsBefore, managedSnapshots(temporaryDirectory));
  }

  private static void assertChildRuntimeReleasesSnapshot(String... arguments)
      throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(
                SqliteChildJvmSupport.childJavaCommand(
                    SqliteRuntimeReleaseProcessProbe.class, arguments))
            .redirectErrorStream(true)
            .start();
    String output;
    int exitCode;
    try (process;
        InputStream processOutput = process.getInputStream()) {
      output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8);
      exitCode = process.waitFor();
    }

    assertEquals(0, exitCode, () -> "Child runtime output:\n" + output);
  }

  private static Set<Path> managedSnapshots(Path temporaryDirectory) throws IOException {
    try (Stream<Path> paths = Files.list(temporaryDirectory)) {
      return paths
          .filter(path -> path.getFileName().toString().startsWith("fingrind-managed-sqlite-"))
          .collect(Collectors.toCollection(HashSet::new));
    }
  }

  @Test
  void release_skipsAnUninitializedRuntime() {
    List<String> calls = new ArrayList<>();

    SqliteNativeRuntimeRelease.release(
        (SqliteVerifiedLibrarySnapshot) null,
        () -> calls.add("shutdown"),
        () -> calls.add("arena"),
        (action, exception) -> calls.add("reported " + action));

    assertEquals(List.of(), calls);
  }

  @Test
  void release_releasesAVerifiedSnapshotAfterClosingTheRuntimeAndArena() {
    List<String> calls = new ArrayList<>();
    SqliteVerifiedLibrarySnapshot snapshot =
        new SqliteVerifiedLibrarySnapshot(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                "/runtime-release-source"),
            Path.of("runtime-release-snapshot"),
            Path.of("runtime-release-snapshot/library"),
            Path.of("runtime-release-snapshot/library.sha256"),
            "0".repeat(64));

    SqliteNativeRuntimeRelease.release(
        snapshot,
        () -> calls.add("shutdown"),
        () -> calls.add("arena"),
        (action, exception) -> calls.add("reported " + action));

    assertEquals(List.of("shutdown", "arena"), calls);
  }

  @Test
  void release_closesTheNativeRuntimeAndArenaBeforeDiscardingTheSnapshot() {
    List<String> calls = new ArrayList<>();

    SqliteNativeRuntimeRelease.release(
        () -> calls.add("shutdown"),
        () -> calls.add("arena"),
        () -> calls.add("snapshot"),
        (action, exception) -> calls.add("reported " + action));

    assertEquals(List.of("shutdown", "arena", "snapshot"), calls);
  }

  @Test
  void release_retainsTheSnapshotWhenNativeShutdownFails() {
    List<String> calls = new ArrayList<>();
    IllegalStateException failure = new IllegalStateException("native shutdown failure");

    SqliteNativeRuntimeRelease.release(
        () -> {
          calls.add("shutdown");
          throw failure;
        },
        () -> calls.add("arena"),
        () -> calls.add("snapshot"),
        (action, exception) -> calls.add("reported " + action + ": " + exception.getMessage()));

    assertEquals(
        List.of(
            "shutdown",
            "reported shutting down the process-scoped SQLite runtime: native shutdown failure"),
        calls);
  }

  @Test
  void release_retainsTheSnapshotWhenTheNativeArenaCannotClose() {
    List<String> calls = new ArrayList<>();
    IllegalStateException failure = new IllegalStateException("arena close failure");

    SqliteNativeRuntimeRelease.release(
        () -> calls.add("shutdown"),
        () -> {
          calls.add("arena");
          throw failure;
        },
        () -> calls.add("snapshot"),
        (action, exception) -> calls.add("reported " + action + ": " + exception.getMessage()));

    assertEquals(
        List.of(
            "shutdown",
            "arena",
            "reported closing the process-scoped SQLite library arena: arena close failure"),
        calls);
  }

  @Test
  void release_reportsSnapshotDisposalFailureAfterTheNativeRuntimeIsClosed() {
    List<String> calls = new ArrayList<>();
    IllegalStateException failure = new IllegalStateException("snapshot release failure");

    SqliteNativeRuntimeRelease.release(
        () -> calls.add("shutdown"),
        () -> calls.add("arena"),
        () -> {
          calls.add("snapshot");
          throw failure;
        },
        (action, exception) -> calls.add("reported " + action + ": " + exception.getMessage()));

    assertEquals(
        List.of(
            "shutdown",
            "arena",
            "snapshot",
            "reported releasing the verified managed SQLite runtime snapshot: snapshot release failure"),
        calls);
  }
}
