package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the no-replace boundary for newly generated protected-book secrets. */
class SqliteGeneratedSecretTargetTest {
  @TempDir Path tempDirectory;

  @Test
  void requireAbsent_refusesAnOccupiedTargetWithoutChangingIt() throws Exception {
    Path targetPath = Files.writeString(tempDirectory.resolve("occupied.key"), "occupied-secret");

    SqliteGeneratedSecretTargetOccupiedException exception =
        assertThrows(
            SqliteGeneratedSecretTargetOccupiedException.class,
            () -> SqliteGeneratedSecretTarget.requireAbsent(targetPath));

    assertEquals(targetPath, exception.targetPath());
    assertEquals("occupied-secret", Files.readString(targetPath));
  }

  @Test
  void publishRetainingStage_keepsTheOwnedStageUntilItsCallerCompletesThePair() throws Exception {
    Path targetPath = tempDirectory.resolve("retained.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("retained.stage"), "staged-secret");

    SqliteGeneratedSecretTarget.requireAbsent(targetPath).publishRetainingStage(stagedPath);

    assertTrue(Files.isSameFile(targetPath, stagedPath));
    assertEquals("staged-secret", Files.readString(targetPath));
  }

  @Test
  void publishRetainingStage_translatesAConcurrentTargetClaimAndPreservesTheStagedSecret()
      throws Exception {
    Path targetPath = tempDirectory.resolve("raced.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("raced.stage"), "staged-secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);
    Files.writeString(targetPath, "concurrent-secret");

    SqliteGeneratedSecretTargetOccupiedException exception =
        assertThrows(
            SqliteGeneratedSecretTargetOccupiedException.class,
            () -> target.publishRetainingStage(stagedPath));

    assertEquals(targetPath, exception.targetPath());
    assertInstanceOf(FileAlreadyExistsException.class, exception.getCause());
    assertEquals("concurrent-secret", Files.readString(targetPath));
    assertTrue(Files.exists(stagedPath));
    Files.delete(stagedPath);
  }

  @Test
  void requireAtomicNoReplacePublication_rejectsUnsupportedStorageAndCleansItsProbe()
      throws Exception {
    Path targetPath = tempDirectory.resolve("unsupported.key");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteGeneratedSecretTarget.requireAtomicNoReplacePublication(
                    targetPath,
                    (target, staged) -> {
                      throw new FileSystemException(
                          target.toString(), staged.toString(), "Operation not supported");
                    }));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED, exception.pathFailure());
    assertInstanceOf(FileSystemException.class, exception.getCause());
    assertFalse(Files.exists(targetPath));
    try (var paths = Files.list(tempDirectory)) {
      assertEquals(0L, paths.count());
    }
  }

  @Test
  void publishRetainingStage_translatesAnUnsupportedAtomicPrimitiveAndPreservesTheStagedSecret()
      throws Exception {
    Path targetPath = tempDirectory.resolve("unsupported-publish.key");
    Path stagedPath =
        Files.writeString(tempDirectory.resolve("unsupported-publish.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                target.publishRetainingStage(
                    stagedPath,
                    (finalPath, staged) -> {
                      throw new FileSystemException(
                          finalPath.toString(), staged.toString(), "Operation not supported");
                    }));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED, exception.pathFailure());
    assertInstanceOf(FileSystemException.class, exception.getCause());
    assertFalse(Files.exists(targetPath));
    assertEquals("secret", Files.readString(stagedPath));
  }

  @Test
  void requireAtomicNoReplacePublication_translatesUnsupportedOperationsAndWrapsOtherIo()
      throws Exception {
    Path unsupportedTarget = tempDirectory.resolve("unsupported-operation.key");
    UnsupportedOperationException unsupported = new UnsupportedOperationException("no hard links");

    SqliteCallerPathContractException unsupportedException =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteGeneratedSecretTarget.requireAtomicNoReplacePublication(
                    unsupportedTarget,
                    (target, staged) -> {
                      throw unsupported;
                    }));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
        unsupportedException.pathFailure());
    assertSame(unsupported, unsupportedException.getCause());

    Path ioTarget = tempDirectory.resolve("io-failure.key");
    java.io.IOException ioFailure = new java.io.IOException("link probe failed");
    IllegalStateException ioException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteGeneratedSecretTarget.requireAtomicNoReplacePublication(
                    ioTarget,
                    (target, staged) -> {
                      throw ioFailure;
                    }));

    assertSame(ioFailure, ioException.getCause());
    assertFalse(Files.exists(unsupportedTarget));
    assertFalse(Files.exists(ioTarget));
    try (var paths = Files.list(tempDirectory)) {
      assertEquals(0L, paths.count());
    }
  }

  @Test
  void publication_preservesNonCapabilityFilesystemFailures() throws Exception {
    Path probeTarget = tempDirectory.resolve("probe-failure.key");
    FileSystemException probeFailure =
        new FileSystemException(probeTarget.toString(), "stage", "Permission denied");

    IllegalStateException probeException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteGeneratedSecretTarget.requireAtomicNoReplacePublication(
                    probeTarget,
                    (target, staged) -> {
                      throw probeFailure;
                    }));

    assertSame(probeFailure, probeException.getCause());

    Path publishTarget = tempDirectory.resolve("publish-failure.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("publish-failure.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(publishTarget);
    FileSystemException publishFailure =
        new FileSystemException(publishTarget.toString(), stagedPath.toString(), null);

    FileSystemException exception =
        assertThrows(
            FileSystemException.class,
            () ->
                target.publishRetainingStage(
                    stagedPath,
                    (finalPath, staged) -> {
                      throw publishFailure;
                    }));

    assertSame(publishFailure, exception);
    assertFalse(Files.exists(publishTarget));
    assertEquals("secret", Files.readString(stagedPath));
  }

  @Test
  void publishRetainingStage_translatesUnsupportedOperations() throws Exception {
    Path targetPath = tempDirectory.resolve("unsupported-operation-publish.key");
    Path stagedPath =
        Files.writeString(tempDirectory.resolve("unsupported-operation.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);
    UnsupportedOperationException unsupported = new UnsupportedOperationException("no hard links");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                target.publishRetainingStage(
                    stagedPath,
                    (finalPath, staged) -> {
                      throw unsupported;
                    }));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED, exception.pathFailure());
    assertSame(unsupported, exception.getCause());
    assertFalse(Files.exists(targetPath));
    assertEquals("secret", Files.readString(stagedPath));
  }
}
