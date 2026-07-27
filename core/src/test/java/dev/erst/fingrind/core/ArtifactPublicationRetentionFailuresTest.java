package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves failed stage publication never discards materialized private-stage evidence. */
class ArtifactPublicationRetentionFailuresTest {
  @TempDir Path temporaryDirectory;

  @Test
  void absentStageLeavesTheOriginalOpeningFailureUntouched() {
    IOException failure = new IOException("stage opening failed");

    assertDoesNotThrow(
        () ->
            ArtifactPublicationRetentionFailures.throwIfMaterializedStage(
                temporaryDirectory.resolve("absent-stage.tmp"), failure));
  }

  @Test
  void materializedStageReplacesTheFailureWithExactRetainedStageEvidence() throws IOException {
    Path stage = Files.createFile(temporaryDirectory.resolve("materialized-stage.tmp"));
    IOException failure = new IOException("stage opening failed");

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () -> ArtifactPublicationRetentionFailures.throwIfMaterializedStage(stage, failure));

    assertEquals(stage.toAbsolutePath().normalize(), exception.retainedStage().retainedStagePath());
    assertSame(failure, exception.getCause());
  }

  @Test
  void unreadableStageInspectionRetainsBothTheOriginalFailureAndInspectionFailure() {
    IOException openingFailure = new IOException("stage opening failed");
    Path retainedPath = temporaryDirectory.resolve("retained-stage.tmp").toAbsolutePath();
    Path unreadableStage =
        (Path)
            Proxy.newProxyInstance(
                Objects.requireNonNull(
                    Thread.currentThread().getContextClassLoader(), "context class loader"),
                new Class<?>[] {Path.class},
                (proxy, method, ignored) ->
                    switch (method.getName()) {
                      case "getFileSystem" -> FileSystems.getDefault();
                      case "toAbsolutePath" -> retainedPath;
                      default ->
                          throw new AssertionError(
                              "Unexpected unreadable-stage Path invocation: " + method.getName());
                    });

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationRetentionFailures.throwIfMaterializedStage(
                    unreadableStage, openingFailure));

    assertSame(openingFailure, exception.getCause());
    assertEquals(1, exception.getSuppressed().length);
  }

  @Test
  void fatalFailureCarriesRetainedStageEvidenceOnlyWhenTheStageExists() throws IOException {
    Path materializedStage = Files.createFile(temporaryDirectory.resolve("fatal-stage.tmp"));
    AssertionError materializedFailure = new AssertionError("fatal materialized failure");
    AssertionError absentFailure = new AssertionError("fatal absent failure");

    ArtifactPublicationRetentionFailures.retainMaterializedStageOnFatalError(
        materializedStage, materializedFailure);
    ArtifactPublicationRetentionFailures.retainMaterializedStageOnFatalError(
        temporaryDirectory.resolve("absent-stage.tmp"), absentFailure);

    assertEquals(1, materializedFailure.getSuppressed().length);
    assertEquals(0, absentFailure.getSuppressed().length);
    ArtifactPublicationRetainedStageException retained =
        (ArtifactPublicationRetainedStageException) materializedFailure.getSuppressed()[0];
    assertEquals(
        materializedStage.toAbsolutePath().normalize(),
        retained.retainedStage().retainedStagePath());
  }

  @Test
  void retainedStageMustNameAnArtifactBeneathAPresentParentDirectory() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ArtifactPublicationRetention(temporaryDirectory.getRoot()));

    assertEquals(
        "retainedStagePath must name an artifact in a parent directory.", exception.getMessage());
  }

  @Test
  void retainedStageMustNotAcceptANameWithoutAParentDirectory() {
    Path fileName = temporaryDirectory.resolve("stage.tmp").getFileName();
    Path parentlessStage =
        (Path)
            Proxy.newProxyInstance(
                Objects.requireNonNull(
                    Thread.currentThread().getContextClassLoader(), "context class loader"),
                new Class<?>[] {Path.class},
                (proxy, method, ignored) ->
                    switch (method.getName()) {
                      case "toAbsolutePath", "normalize" -> proxy;
                      case "getFileName" -> fileName;
                      case "getParent" -> null;
                      default ->
                          throw new AssertionError(
                              "Unexpected parentless Path invocation: " + method.getName());
                    });

    assertThrows(
        IllegalArgumentException.class, () -> new ArtifactPublicationRetention(parentlessStage));
  }
}
