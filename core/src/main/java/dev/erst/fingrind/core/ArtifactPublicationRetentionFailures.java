package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Classifies failed private-stage publication attempts without losing retained-stage evidence. */
final class ArtifactPublicationRetentionFailures {
  private ArtifactPublicationRetentionFailures() {}

  static void throwIfMaterializedStage(Path stagedPath, Throwable failure)
      throws ArtifactPublicationRetainedStageException {
    try {
      Files.readAttributes(
          stagedPath, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (NoSuchFileException absent) {
      return;
    } catch (IOException | RuntimeException inspectionFailure) {
      ArtifactPublicationRetainedStageException retainedFailure =
          retainedStageFailure(stagedPath, failure);
      retainedFailure.addSuppressed(inspectionFailure);
      throw retainedFailure;
    }
    throw retainedStageFailure(stagedPath, failure);
  }

  static ArtifactPublicationRetainedStageException retainedStageFailure(
      Path stagedPath, Throwable failure) {
    return new ArtifactPublicationRetainedStageException(
        new ArtifactPublicationRetention(stagedPath), failure);
  }

  static void retainStageOnFatalError(Path stagedPath, Error primaryFailure) {
    primaryFailure.addSuppressed(
        new ArtifactPublicationRetainedStageException(
            new ArtifactPublicationRetention(stagedPath),
            new IOException("Fatal stage-write failure retained the exact private stage.")));
  }

  static void retainMaterializedStageOnFatalError(Path stagedPath, Error primaryFailure) {
    if (Files.exists(stagedPath, LinkOption.NOFOLLOW_LINKS)) {
      retainStageOnFatalError(stagedPath, primaryFailure);
    }
  }
}
