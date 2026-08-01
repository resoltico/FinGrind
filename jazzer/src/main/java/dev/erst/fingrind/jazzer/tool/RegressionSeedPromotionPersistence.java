package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Persists one reviewed seed promotion while preserving any partially materialized evidence. */
final class RegressionSeedPromotionPersistence {
  private RegressionSeedPromotionPersistence() {}

  static void persist(
      Path projectDirectory,
      Path sourceInputPath,
      byte[] inputBytes,
      Path committedInputPath,
      Path metadataPath,
      RegressionSeedMetadata metadata,
      RegressionSeedPromoter.MetadataWriter metadataWriter)
      throws IOException {
    RegressionSeedRepositoryPathAdmission.createOrRequireRealDirectoryTree(
        projectDirectory,
        Objects.requireNonNull(committedInputPath.getParent(), "committedInputPath parent"));
    RegressionSeedRepositoryPathAdmission.createOrRequireRealDirectoryTree(
        projectDirectory, Objects.requireNonNull(metadataPath.getParent(), "metadataPath parent"));
    try {
      Objects.requireNonNull(sourceInputPath, "sourceInputPath");
      writeNewInput(committedInputPath, Objects.requireNonNull(inputBytes, "inputBytes"));
      metadataWriter.write(metadataPath, metadata);
    } catch (IOException | RuntimeException exception) {
      List<Path> retainedArtifactPaths = retainedArtifactPaths(committedInputPath, metadataPath);
      if (!retainedArtifactPaths.isEmpty()) {
        throw new RegressionSeedPromotionRetainedArtifactsException(
            new RegressionSeedPromotionRetention(
                committedInputPath, metadataPath, retainedArtifactPaths),
            exception);
      }
      throw exception;
    } catch (Error failure) {
      retainArtifactsOnFatalFailure(committedInputPath, metadataPath, failure);
      throw failure;
    }
  }

  private static void writeNewInput(Path committedInputPath, byte[] inputBytes) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            committedInputPath,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS)) {
      channel.write(ByteBuffer.wrap(inputBytes));
      channel.force(true);
    }
  }

  private static List<Path> retainedArtifactPaths(Path committedInputPath, Path metadataPath) {
    return List.of(committedInputPath, metadataPath).stream()
        .filter(path -> Files.exists(path, LinkOption.NOFOLLOW_LINKS))
        .toList();
  }

  private static void retainArtifactsOnFatalFailure(
      Path committedInputPath, Path metadataPath, Error primaryFailure) {
    retainArtifactsOnFatalFailure(
        retainedArtifactPaths(committedInputPath, metadataPath),
        committedInputPath,
        metadataPath,
        primaryFailure);
  }

  static void retainArtifactsOnFatalFailure(
      List<Path> retainedArtifactPaths,
      Path committedInputPath,
      Path metadataPath,
      Error primaryFailure) {
    Objects.requireNonNull(retainedArtifactPaths, "retainedArtifactPaths");
    if (retainedArtifactPaths.isEmpty()) {
      return;
    }
    primaryFailure.addSuppressed(
        new RegressionSeedPromotionRetainedArtifactsException(
            new RegressionSeedPromotionRetention(
                committedInputPath, metadataPath, retainedArtifactPaths),
            new IOException("Fatal seed-promotion failure retained materialized artifacts.")));
  }
}
