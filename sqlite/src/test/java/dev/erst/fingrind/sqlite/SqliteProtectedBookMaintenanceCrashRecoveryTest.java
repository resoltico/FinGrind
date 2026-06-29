package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Subprocess crash-coverage tests for staged protected-book maintenance checkpoints. */
class SqliteProtectedBookMaintenanceCrashRecoveryTest extends SqliteNativeBridgeTestSupport {
  private static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError(
                    "Crash-recovery tests expect key-file-backed access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError(
                    "Crash-recovery tests expect key-file-backed access only.");
          };

  @Test
  void killedBackupStage_doesNotPublishOneFinalPairAndOneRetryCleansAbandonedStageArtifacts()
      throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(bookPath);
    initializeBook(sourceAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("source.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("source.key");
    Path signalPath = tempDirectory.resolve("signals").resolve("backup-stage.ready");

    HelperProcess helper =
        startHelperProcess(
            "stage-backup",
            bookPath,
            keyFilePath(sourceAccess),
            backupFilePath,
            backupBookKeyFilePath,
            signalPath);
    waitForSignal(helper, signalPath);
    killHelper(helper);

    assertFalse(Files.exists(backupFilePath));
    assertFalse(Files.exists(backupBookKeyFilePath));
    assertNoStageArtifacts(backupBookKeyFilePath, ".backup-key-", ".tmp");

    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        StagedBackupPair stagedBackupPair =
            acceptedValue(
                store.stageBackupPair(verifiedSourceBook, backupFilePath, backupBookKeyFilePath))) {
      stagedBackupPair.commit();
    }

    assertTrue(Files.exists(backupFilePath));
    assertTrue(Files.exists(backupBookKeyFilePath));
    assertNoStageArtifacts(backupFilePath, ".backup-", ".sqlite");
    assertNoStageArtifacts(backupBookKeyFilePath, ".backup-key-", ".tmp");
  }

  @Test
  void killedReplacementStage_leavesOneLiveTargetUntouchedAndOneRetrySucceeds() throws Exception {
    Path sourcePath = writeArtifact("replacement-source.sqlite", "replacement");
    Path targetPath = writeArtifact("replacement-target.sqlite", "previous");
    Path signalPath = tempDirectory.resolve("signals").resolve("replacement-stage.ready");

    HelperProcess helper =
        startHelperProcess("stage-replacement", sourcePath, targetPath, signalPath);
    waitForSignal(helper, signalPath);
    killHelper(helper);

    assertEquals("previous", Files.readString(targetPath));

    try (StagedBookReplacement stagedReplacement =
        maintenanceStore().stageReplacement(sourcePath, targetPath)) {
      stagedReplacement.commit();
    }

    assertEquals("replacement", Files.readString(targetPath));
    assertNoStageArtifacts(targetPath, ".restore-", ".tmp");
    assertNoStageArtifacts(targetPath, ".previous-", ".sqlite");
  }

  @Test
  void killedRollbackDeletionStage_keepsOneRecoveryArtifactVisibleUntilExplicitCommit()
      throws Exception {
    Path rollbackArtifactPath = writeArtifact("book.sqlite.rekey-rollback-1.sqlite", "rollback");
    Path signalPath = tempDirectory.resolve("signals").resolve("rollback-delete-stage.ready");

    HelperProcess helper =
        startHelperProcess("stage-rollback-delete", rollbackArtifactPath, signalPath);
    waitForSignal(helper, signalPath);
    killHelper(helper);

    assertTrue(Files.exists(rollbackArtifactPath));

    try (StagedRollbackArtifactDeletion stagedDeletion =
        maintenanceStore().stageRollbackArtifactDeletion(rollbackArtifactPath)) {
      stagedDeletion.commit();
    }

    assertFalse(Files.exists(rollbackArtifactPath));
  }

  private SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  private static ProtectedBookMaintenanceStore.VerifiedBook verifiedBook(
      SqliteProtectedBookMaintenanceStore store, BookAccess bookAccess) {
    return switch (acceptedValue(
        store.verifyInitializedBook(
            localAccess(bookAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))) {
      case ProtectedBookMaintenanceStore.VerifiedBook verifiedBook -> verifiedBook;
      case ProtectedBookMaintenanceStore.VerificationFailure verificationFailure ->
          throw new AssertionError(
              "Expected one verified book but got " + verificationFailure.failure());
    };
  }

  private static ProtectedBookAccess localAccess(BookAccess bookAccess) {
    return ProtectedBookAccess.fromPublished(bookAccess);
  }

  private static Path keyFilePath(BookAccess bookAccess) {
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("Expected one key-file-backed access tuple.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("Expected one key-file-backed access tuple.");
    };
  }

  private void initializeBook(BookAccess bookAccess) {
    try {
      Path parentDirectory = bookAccess.bookFilePath().getParent();
      if (parentDirectory != null) {
        Files.createDirectories(parentDirectory);
        SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create the crash-recovery test book directory.", exception);
    }
    withOpenDatabase(
        bookAccess,
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          SqliteAuditEventWriter.insertAuditEvent(
              database, BookAuditEvent.bookOpened(Instant.parse("2026-05-19T13:00:00Z")));
        });
  }

  private Path writeArtifact(String fileName, String content) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parent = artifactPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    }
    Files.writeString(artifactPath, content);
    return artifactPath;
  }

  private HelperProcess startHelperProcess(String mode, Path... args) throws IOException {
    Path logPath = tempDirectory.resolve("logs").resolve(mode + ".log");
    Files.createDirectories(Objects.requireNonNull(logPath.getParent(), "logPath parent"));
    List<String> command = new ArrayList<>();
    command.add(javaBinaryPath().toString());
    command.add("--enable-native-access=ALL-UNNAMED");
    addCurrentSystemProperty(command, "fingrind.runtime.distribution");
    addCurrentSystemProperty(command, "fingrind.source-checkout.root");
    addCurrentSystemProperty(command, "fingrind.source-checkout.build-root");
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(SqliteProtectedBookMaintenanceProcessHelper.class.getName());
    command.add(mode);
    for (Path argument : args) {
      command.add(argument.toAbsolutePath().normalize().toString());
    }
    Process process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(logPath.toFile())
            .start();
    return new HelperProcess(process, logPath);
  }

  private static void addCurrentSystemProperty(List<String> command, String propertyName) {
    String value = System.getProperty(propertyName);
    if (value != null) {
      command.add("-D" + propertyName + "=" + value);
    }
  }

  private void waitForSignal(HelperProcess helper, Path signalPath)
      throws IOException, InterruptedException {
    for (int attempt = 0; attempt < 100; attempt++) {
      if (Files.exists(signalPath)) {
        return;
      }
      if (!helper.process().isAlive()) {
        throw new AssertionError(
            "Maintenance helper exited before signaling readiness:\n"
                + Files.readString(helper.logPath()));
      }
      Thread.sleep(100L);
    }
    killHelper(helper);
    throw new AssertionError(
        "Timed out waiting for the maintenance helper signal:\n"
            + Files.readString(helper.logPath()));
  }

  private void killHelper(HelperProcess helper) throws IOException, InterruptedException {
    helper.process().destroyForcibly();
    if (!helper.process().waitFor(10, TimeUnit.SECONDS)) {
      throw new AssertionError(
          "Timed out waiting for the killed maintenance helper to exit:\n"
              + Files.readString(helper.logPath()));
    }
  }

  private static void assertNoStageArtifacts(Path basePath, String infix, String suffix)
      throws IOException {
    Path parentDirectory = basePath.getParent();
    if (parentDirectory == null || !Files.isDirectory(parentDirectory)) {
      return;
    }
    String baseName =
        Objects.requireNonNull(basePath.getFileName(), "basePath fileName").toString();
    try (Stream<Path> siblings = Files.list(parentDirectory)) {
      assertTrue(
          siblings.noneMatch(
              path ->
                  path.getFileName().toString().startsWith(baseName + infix)
                      && path.getFileName().toString().endsWith(suffix)),
          "Expected no abandoned stage artifacts beside " + basePath + ".");
    }
  }

  private static Path javaBinaryPath() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().normalize();
  }

  private static <T> T acceptedValue(MaintenanceDecision<T> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<T>(T value) -> value;
      case MaintenanceDecision.Failed<T>(MaintenanceFailure failure) ->
          throw new AssertionError("Expected accepted maintenance decision but got " + failure);
    };
  }

  private record HelperProcess(Process process, Path logPath) {
    private HelperProcess {
      Objects.requireNonNull(process, "process");
      Objects.requireNonNull(logPath, "logPath");
    }
  }
}
