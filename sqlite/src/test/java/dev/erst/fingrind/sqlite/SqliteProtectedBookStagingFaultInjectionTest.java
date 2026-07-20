package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Exercises every protected-book staging boundary against real encrypted book artifacts. */
class SqliteProtectedBookStagingFaultInjectionTest
    extends SqliteProtectedBookMaintenanceStoreCoverageTestSupport {

  @ParameterizedTest(name = "backup: {0}")
  @MethodSource("backupStagingCheckpoints")
  void stageBackupPair_failureAtEveryBoundaryPreservesSourceAndLeavesNoArtifacts(
      String checkpointName, SqliteProtectedBookStagingSupport.StagingCheckpoint checkpoint)
      throws Exception {
    SourceBook source = initializedSourceBook("backup-" + checkpointName);
    byte[] sourceBookBefore = Files.readAllBytes(source.bookPath());
    byte[] sourceKeyBefore = Files.readAllBytes(source.keyPath());
    Path sourceDirectory = requiredParent(source.bookPath());
    List<String> sourceDirectoryBefore = childNames(sourceDirectory);
    Path backupBookPath =
        tempDirectory.resolve("backup-" + checkpointName).resolve("backup.sqlite");
    Path backupKeyPath = tempDirectory.resolve("backup-" + checkpointName).resolve("backup.key");

    assertStagingFailsAt(
        checkpoint,
        () ->
            SqliteProtectedBookStagingSupport.stageResolvedBackupPair(
                source.bookPath(),
                backupBookPath,
                backupKeyPath,
                SqliteBookKeyFile.load(source.keyPath()),
                VERIFICATION_SUPPORT,
                failAt(checkpoint),
                SqliteBookKeyFileGenerator::generate));

    assertArrayEquals(sourceBookBefore, Files.readAllBytes(source.bookPath()));
    assertArrayEquals(sourceKeyBefore, Files.readAllBytes(source.keyPath()));
    assertFalse(Files.exists(backupBookPath));
    assertFalse(Files.exists(backupKeyPath));
    assertNoOwnedStages(backupBookPath, backupKeyPath);
    assertEquals(sourceDirectoryBefore, childNames(sourceDirectory));
  }

  @ParameterizedTest(name = "restore: {0}")
  @MethodSource("restoreStagingCheckpoints")
  void stageRestoredBookPair_failureAtEveryBoundaryPreservesSourceAndLiveDestination(
      String checkpointName, SqliteProtectedBookStagingSupport.StagingCheckpoint checkpoint)
      throws Exception {
    SourceBook source = initializedSourceBook("restore-" + checkpointName);
    byte[] sourceBookBefore = Files.readAllBytes(source.bookPath());
    byte[] sourceKeyBefore = Files.readAllBytes(source.keyPath());
    Path restoredBookPath =
        writeArtifact("restore-" + checkpointName + "/restored.sqlite", "existing-live-book");
    byte[] restoredBookBefore = Files.readAllBytes(restoredBookPath);
    Path restoredKeyPath =
        tempDirectory.resolve("restore-" + checkpointName).resolve("restored.key");
    Path sourceDirectory = requiredParent(source.bookPath());
    List<String> sourceDirectoryBefore = childNames(sourceDirectory);

    assertStagingFailsAt(
        checkpoint,
        () ->
            SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                source.bookPath(),
                restoredBookPath,
                restoredKeyPath,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                SqliteBookKeyFile.load(source.keyPath()),
                VERIFICATION_SUPPORT,
                failAt(checkpoint),
                SqliteBookKeyFileGenerator::generate));

    assertArrayEquals(sourceBookBefore, Files.readAllBytes(source.bookPath()));
    assertArrayEquals(sourceKeyBefore, Files.readAllBytes(source.keyPath()));
    assertArrayEquals(restoredBookBefore, Files.readAllBytes(restoredBookPath));
    assertFalse(Files.exists(restoredKeyPath));
    assertNoOwnedStages(restoredBookPath, restoredKeyPath);
    assertEquals(sourceDirectoryBefore, childNames(sourceDirectory));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("distinctSecretStagingOperations")
  void stagedMaintenancePair_retriesWhenItsGeneratedSecretMatchesTheSource(
      String operation, DistinctSecretStagingOperation stagingOperation) throws Exception {
    SourceBook source = initializedSourceBook("distinct-" + operation);
    AtomicInteger generationCalls = new AtomicInteger();
    Path generatedKeyPath = tempDirectory.resolve("distinct-" + operation).resolve("generated.key");

    stagingOperation.stageAndCommit(
        source, generatedKeyPath, duplicateThenDistinctKey(source.keyPath(), generationCalls));

    assertEquals(2, generationCalls.get());
    try (SqliteBookPassphrase sourcePassphrase = SqliteBookKeyFile.load(source.keyPath());
        SqliteBookPassphrase generatedPassphrase = SqliteBookKeyFile.load(generatedKeyPath)) {
      assertFalse(generatedPassphrase.hasSameSecretAs(sourcePassphrase));
    }
  }

  @Test
  void distinctStagedSecret_failsClosedWhenEveryGeneratedSecretMatchesTheSource() throws Exception {
    SourceBook source = initializedSourceBook("distinct-exhausted");
    Path stagedKeyPath = tempDirectory.resolve("distinct-exhausted").resolve("staged.key");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              try (SqliteBookPassphrase sourcePassphrase =
                  SqliteBookKeyFile.load(source.keyPath())) {
                SqliteDistinctStagedSecret.generate(
                    stagedKeyPath,
                    sourcePassphrase,
                    SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_SECRET_GENERATION,
                    checkpoint -> {},
                    duplicateSourceKey(source.keyPath()));
              }
            });

    assertTrue(String.valueOf(exception.getMessage()).contains("Unable to generate a distinct"));
    assertFalse(Files.exists(stagedKeyPath));
  }

  private static Stream<Arguments> backupStagingCheckpoints() {
    return Stream.of(
        Arguments.of("export", SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_EXPORT),
        Arguments.of(
            "secret-generation",
            SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_SECRET_GENERATION),
        Arguments.of("rekey", SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_REKEY));
  }

  private static Stream<Arguments> restoreStagingCheckpoints() {
    return Stream.of(
        Arguments.of("copy", SqliteProtectedBookStagingSupport.StagingCheckpoint.RESTORE_COPY),
        Arguments.of(
            "secret-generation",
            SqliteProtectedBookStagingSupport.StagingCheckpoint.RESTORE_SECRET_GENERATION),
        Arguments.of("rekey", SqliteProtectedBookStagingSupport.StagingCheckpoint.RESTORE_REKEY));
  }

  private static Stream<Arguments> distinctSecretStagingOperations() {
    return Stream.of(
        Arguments.of(
            "backup",
            (DistinctSecretStagingOperation)
                (source, generatedKeyPath, generator) -> {
                  Path backupBookPath = generatedKeyPath.resolveSibling("backup.sqlite");
                  try (StagedBackupPair stagedPair =
                      acceptedValue(
                          SqliteProtectedBookStagingSupport.stageResolvedBackupPair(
                              source.bookPath(),
                              backupBookPath,
                              generatedKeyPath,
                              SqliteBookKeyFile.load(source.keyPath()),
                              VERIFICATION_SUPPORT,
                              checkpoint -> {},
                              generator))) {
                    stagedPair.commit();
                  }
                }),
        Arguments.of(
            "restore-and-rekey",
            (DistinctSecretStagingOperation)
                (source, generatedKeyPath, generator) -> {
                  Path restoredBookPath = source.bookPath().resolveSibling("restored.sqlite");
                  Files.writeString(restoredBookPath, "existing-live-book");
                  try (StagedRestoredBookPair stagedPair =
                      acceptedValue(
                          SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                              source.bookPath(),
                              restoredBookPath,
                              generatedKeyPath,
                              RestoredBookTargetPolicy.REPLACE_SELECTED,
                              SqliteBookKeyFile.load(source.keyPath()),
                              VERIFICATION_SUPPORT,
                              checkpoint -> {},
                              generator))) {
                    stagedPair.commit();
                  }
                }));
  }

  private SourceBook initializedSourceBook(String directoryName) {
    Path sourceBookPath = tempDirectory.resolve(directoryName).resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    return new SourceBook(sourceBookPath, keyFilePath(sourceAccess));
  }

  private static void assertStagingFailsAt(
      SqliteProtectedBookStagingSupport.StagingCheckpoint checkpoint,
      java.util.function.Supplier<? extends MaintenanceDecision<?>> stagingOperation) {
    MaintenanceFailure failure =
        stagingOperation
            .get()
            .fold(
                accepted -> {
                  throw new AssertionError("Expected staging failure at " + checkpoint + ".");
                },
                failed -> failed);
    assertEquals(checkpoint.failureMessage(), failure.message());
  }

  private static SqliteProtectedBookStagingSupport.StagingCheckpointListener failAt(
      SqliteProtectedBookStagingSupport.StagingCheckpoint expectedCheckpoint) {
    return checkpoint -> {
      if (checkpoint == expectedCheckpoint) {
        throw new InjectedStagingFailure();
      }
    };
  }

  private static SqliteDistinctStagedSecret.Generator duplicateThenDistinctKey(
      Path sourceKeyPath, AtomicInteger generationCalls) {
    return stagedKeyPath -> {
      if (generationCalls.getAndIncrement() == 0) {
        duplicateSourceKey(sourceKeyPath).generate(stagedKeyPath);
        return;
      }
      SqliteBookKeyFileGenerator.generate(stagedKeyPath);
    };
  }

  private static SqliteDistinctStagedSecret.Generator duplicateSourceKey(Path sourceKeyPath) {
    return stagedKeyPath -> {
      try {
        Files.copy(
            sourceKeyPath,
            stagedKeyPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
    };
  }

  private static void assertNoOwnedStages(Path bookPath, Path keyPath) {
    assertTrue(SqliteOwnedStageRecord.findFor(bookPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(keyPath).isEmpty());
  }

  private static List<String> childNames(Path directory) throws IOException {
    try (Stream<Path> children = Files.list(directory)) {
      return children.map(path -> path.getFileName().toString()).sorted().toList();
    }
  }

  private static Path requiredParent(Path path) {
    return Objects.requireNonNull(path.getParent(), "test book path parent");
  }

  private static Path keyFilePath(BookAccess bookAccess) {
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("Expected one key-file-backed test access tuple.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("Expected one key-file-backed test access tuple.");
    };
  }

  private record SourceBook(Path bookPath, Path keyPath) {}

  /** Stages and commits one maintenance pair with a controlled generated-secret collaborator. */
  @FunctionalInterface
  private interface DistinctSecretStagingOperation {
    void stageAndCommit(
        SourceBook source, Path generatedKeyPath, SqliteDistinctStagedSecret.Generator generator)
        throws IOException;
  }

  /** Signals the selected test-only staging boundary. */
  private static final class InjectedStagingFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
