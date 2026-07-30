package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Shared staged protected-book pair fixtures for failure-boundary test scenarios. */
abstract class SqliteStagedProtectedBookPairFailureTestSupport
    extends SqliteArtifactPublicationTestSupport {
  protected static final String STAGED_BACKUP_FILE_NAME = "staged.sqlite";
  protected static final String STAGED_KEY_FILE_NAME = "staged.key";
  protected static final String FINAL_BACKUP_FILE_NAME = "backup.sqlite";
  protected static final String FINAL_KEY_FILE_NAME = "backup.key";

  protected static SqliteProtectedBookPublicationSupport
          .FinalMemberPublicationGuardRejectedException
      guardRejection(SqliteProtectedBookPublicationSupport.FinalMember member) {
    return assertThrows(
        SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuardRejectedException.class,
        () ->
            SqliteProtectedBookPublicationSupport.requireGuard(
                member,
                () -> {
                  throw new IOException("injected final-member guard refusal");
                }));
  }

  protected static SqliteBookPassphrase testPassphrase() {
    return SqliteBookPassphrase.fromUtf8Bytes(
        "staged protected-book pair", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
  }

  protected static SqliteProtectedBookPublicationSupport.PairDirectoryForcer
      recoveryRecordFailingDirectoryForcer() {
    return (step, parentDirectory) -> {
      if (step
          == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD) {
        throw new IOException("simulated recovery-record promotion failure");
      }
    };
  }

  protected static SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer
      recordForcerFailingAtBookBoundary(AtomicInteger forceCalls) {
    return evidencePath -> {
      if (forceCalls.incrementAndGet() > 3) {
        throw new IOException("simulated record force failure before book publication");
      }
      SqliteOwnedRegularFileAccess.forceFile(evidencePath);
    };
  }

  protected static void closeUnusedBackupPassphrase(SqliteStagedBackupPair stagedPair) {
    try {
      MethodHandle closeUnusedBackupPassphrase =
          MethodHandles.privateLookupIn(SqliteStagedBackupPair.class, MethodHandles.lookup())
              .findVirtual(
                  SqliteStagedBackupPair.class,
                  "closeUnusedBackupPassphrase",
                  MethodType.methodType(void.class));
      closeUnusedBackupPassphrase.invoke(stagedPair);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Failed to invoke staged backup passphrase closure.", throwable);
    }
  }

  protected static Path ownedRecordPath(Path parent) throws IOException {
    try (Stream<Path> children = Files.list(parent)) {
      return children
          .filter(path -> path.getFileName().toString().endsWith(".owner"))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Expected one owned-stage record fixture."));
    }
  }

  protected static SqliteStagedRestoredBookPair createWithNullVerificationSupport(
      Path stagedBookPath,
      Path finalBookPath,
      Path stagedKeyPath,
      Path finalKeyPath,
      SqliteBookPassphrase passphrase) {
    try {
      MethodHandle factory =
          MethodHandles.lookup()
              .findStatic(
                  SqliteStagedRestoredBookPairFactory.class,
                  "create",
                  MethodType.methodType(
                      SqliteStagedRestoredBookPair.class,
                      SqliteStagedProtectedBookPairArtifacts.class,
                      RestoredBookTargetPolicy.class,
                      SqliteBookPassphrase.class,
                      SqliteProtectedBookVerificationSupport.class));
      return (SqliteStagedRestoredBookPair)
          factory.invokeWithArguments(
              new SqliteStagedProtectedBookPairArtifacts(
                  SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                  finalBookPath,
                  SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                  finalKeyPath),
              RestoredBookTargetPolicy.REPLACE_SELECTED,
              passphrase,
              null);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Throwable exception) {
      throw new LinkageError("Failed to invoke the staged restored-book factory.", exception);
    }
  }

  protected static Path ownedRecordPathForStage(Path stagedPath) throws IOException {
    Path normalizedStagedPath = stagedPath.toAbsolutePath().normalize();
    String encodedStagedPath =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(normalizedStagedPath.toString().getBytes(StandardCharsets.UTF_8));
    Path parent = Objects.requireNonNull(normalizedStagedPath.getParent(), "stagedPath parent");
    try (Stream<Path> children = Files.list(parent)) {
      return children
          .filter(path -> path.getFileName().toString().endsWith(".owner"))
          .filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
          .filter(
              path -> {
                try {
                  return Files.readString(path).contains("stage=" + encodedStagedPath);
                } catch (IOException exception) {
                  throw new IllegalStateException(
                      "Unable to inspect one owned-stage record.", exception);
                }
              })
          .findFirst()
          .orElseThrow(() -> new AssertionError("Expected one owned-stage record fixture."));
    }
  }

  protected static void replaceWithNonemptyDirectory(Path path) throws IOException {
    Files.delete(path);
    Files.createDirectory(path);
    Files.writeString(path.resolve("tamper-blocker"), "altered");
  }

  protected static void requirePosixPermissions(Path path) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        path.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "This filesystem cannot model POSIX access denial.");
  }
}
