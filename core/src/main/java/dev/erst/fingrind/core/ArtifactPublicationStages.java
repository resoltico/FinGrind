package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Exact-channel creation of deliberately retained private artifact stages. */
public final class ArtifactPublicationStages {
  private static final int MAXIMUM_STAGE_NAME_ATTEMPTS = 64;
  private static final int COPY_BUFFER_BYTES = 16 * 1024;

  private ArtifactPublicationStages() {}

  /**
   * Atomically creates one fresh owner-only stage, writes the exact bytes, and forces its bound
   * channel before returning its retained path.
   */
  public static Path createAndWrite(
      Path parentDirectory, String prefix, String suffix, byte[] bytes) throws IOException {
    return createAndWrite(
        parentDirectory, prefix, suffix, bytes, ArtifactPublicationStages::openNewPrivateStage);
  }

  static Path createAndWrite(
      Path parentDirectory,
      String prefix,
      String suffix,
      byte[] bytes,
      StageChannelOpener stageChannelOpener)
      throws IOException {
    byte[] checkedBytes = Objects.requireNonNull(bytes, "bytes");
    return create(
        parentDirectory,
        prefix,
        suffix,
        Objects.requireNonNull(stageChannelOpener, "stageChannelOpener"),
        destination -> writeAndForce(destination, checkedBytes));
  }

  /**
   * Atomically creates one fresh owner-only stage, streams a nofollow source into its bound
   * channel, and forces it before returning its retained path.
   */
  public static Path createAndCopy(
      Path parentDirectory, String prefix, String suffix, Path sourcePath) throws IOException {
    return createAndCopy(
        parentDirectory,
        prefix,
        suffix,
        sourcePath,
        ArtifactPublicationStages::openNewPrivateStage,
        ArtifactPublicationStages::openNoFollowSource);
  }

  static Path createAndCopy(
      Path parentDirectory,
      String prefix,
      String suffix,
      Path sourcePath,
      StageChannelOpener stageChannelOpener,
      SourceChannelOpener sourceChannelOpener)
      throws IOException {
    Path checkedSourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
    if (!Files.isRegularFile(checkedSourcePath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Artifact-publication source must be one regular non-symlink file.");
    }
    SourceChannelOpener checkedSourceChannelOpener =
        Objects.requireNonNull(sourceChannelOpener, "sourceChannelOpener");
    Path completedStage = null;
    try (FileChannel source = checkedSourceChannelOpener.open(checkedSourcePath)) {
      completedStage =
          create(
              parentDirectory,
              prefix,
              suffix,
              Objects.requireNonNull(stageChannelOpener, "stageChannelOpener"),
              destination -> copyAndForce(source, destination));
    } catch (ArtifactPublicationRetainedStageException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      if (completedStage != null) {
        throw ArtifactPublicationRetentionFailures.retainedStageFailure(completedStage, failure);
      }
      throw failure;
    } catch (Error failure) {
      if (completedStage != null) {
        ArtifactPublicationRetentionFailures.retainStageOnFatalError(completedStage, failure);
      }
      throw failure;
    }
    return Objects.requireNonNull(completedStage, "completedStage");
  }

  private static Path create(
      Path parentDirectory,
      String prefix,
      String suffix,
      StageChannelOpener stageChannelOpener,
      ExactStageWriter writer)
      throws IOException {
    Path parent =
        Objects.requireNonNull(parentDirectory, "parentDirectory").toAbsolutePath().normalize();
    String checkedPrefix = requireNamePart(prefix, "prefix");
    String checkedSuffix = requireNamePart(suffix, "suffix");
    StageChannelOpener checkedStageChannelOpener =
        Objects.requireNonNull(stageChannelOpener, "stageChannelOpener");
    ExactStageWriter checkedWriter = Objects.requireNonNull(writer, "writer");
    @Nullable Path createdStage = null;
    for (int attempt = 0;
        attempt < MAXIMUM_STAGE_NAME_ATTEMPTS && createdStage == null;
        attempt++) {
      Path stagedPath =
          parent.resolve(
              ArtifactStageFileName.compose(checkedPrefix, UUID.randomUUID(), checkedSuffix));
      createdStage = createStageAttempt(stagedPath, checkedStageChannelOpener, checkedWriter);
    }
    if (createdStage == null) {
      throw new IOException("FinGrind could not allocate a fresh private artifact stage.");
    }
    return createdStage;
  }

  private static @Nullable Path createStageAttempt(
      Path stagedPath, StageChannelOpener stageChannelOpener, ExactStageWriter writer)
      throws IOException {
    PrivateOutputFile.OpenedFile destination;
    try {
      destination = stageChannelOpener.open(stagedPath);
    } catch (FileAlreadyExistsException collision) {
      // A collision never authorizes reuse; the caller allocates a fresh stage name.
      return null;
    } catch (IOException | RuntimeException failure) {
      ArtifactPublicationRetentionFailures.throwIfMaterializedStage(stagedPath, failure);
      throw failure;
    } catch (Error failure) {
      ArtifactPublicationRetentionFailures.retainMaterializedStageOnFatalError(stagedPath, failure);
      throw failure;
    }
    try (PrivateOutputFile.OpenedFile openedDestination = destination) {
      writer.write(openedDestination);
    } catch (IOException | RuntimeException failure) {
      throw ArtifactPublicationRetentionFailures.retainedStageFailure(stagedPath, failure);
    } catch (Error failure) {
      ArtifactPublicationRetentionFailures.retainStageOnFatalError(stagedPath, failure);
      throw failure;
    }
    return stagedPath;
  }

  static PrivateOutputFile.OpenedFile openNewPrivateStage(Path stagedPath) throws IOException {
    return PrivateOutputFile.createNew(stagedPath);
  }

  static FileChannel openNoFollowSource(Path sourcePath) throws IOException {
    return openNoFollowSource(sourcePath, ArtifactPublicationStages::openReadOnlyNoFollowSource);
  }

  static FileChannel openNoFollowSource(Path sourcePath, SourceChannelOpener sourceChannelOpener)
      throws IOException {
    Path checkedSourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
    SourceChannelOpener checkedSourceChannelOpener =
        Objects.requireNonNull(sourceChannelOpener, "sourceChannelOpener");
    try {
      return checkedSourceChannelOpener.open(checkedSourcePath);
    } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
      throw new IOException(
          "The selected filesystem cannot enforce nofollow access for an artifact-publication source.",
          unsupported);
    }
  }

  private static FileChannel openReadOnlyNoFollowSource(Path sourcePath) throws IOException {
    return FileChannel.open(sourcePath, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
  }

  private static void writeAndForce(PrivateOutputFile.OpenedFile destination, byte[] bytes)
      throws IOException {
    ByteBuffer source = ByteBuffer.wrap(bytes);
    while (source.hasRemaining()) {
      if (destination.write(source) <= 0) {
        throw new IOException("Failed to write the complete private artifact stage.");
      }
    }
    destination.force();
  }

  private static void copyAndForce(FileChannel source, PrivateOutputFile.OpenedFile destination)
      throws IOException {
    source.position(0L);
    ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
    while (true) {
      int read = source.read(buffer);
      if (read < 0) {
        break;
      }
      if (read == 0) {
        throw new IOException("Failed to read the complete artifact-publication source.");
      }
      buffer.flip();
      while (buffer.hasRemaining()) {
        if (destination.write(buffer) <= 0) {
          throw new IOException("Failed to write the complete private artifact stage.");
        }
      }
      buffer.clear();
    }
    destination.force();
  }

  private static String requireNamePart(String value, String parameterName) {
    String checkedValue = Objects.requireNonNull(value, parameterName);
    if (checkedValue.isEmpty()) {
      throw new IllegalArgumentException(
          parameterName + " must be a nonempty single path name part.");
    }
    if (checkedValue.contains("/")) {
      throw new IllegalArgumentException(
          parameterName + " must be a nonempty single path name part.");
    }
    if (checkedValue.contains("\\")) {
      throw new IllegalArgumentException(
          parameterName + " must be a nonempty single path name part.");
    }
    return checkedValue;
  }

  /** Opens the fresh private stage bound to one candidate stage path. */
  @FunctionalInterface
  interface StageChannelOpener {
    /** Opens the candidate private stage. */
    PrivateOutputFile.OpenedFile open(Path stagedPath) throws IOException;
  }

  /** Opens the nofollow source channel that is copied into one private stage. */
  @FunctionalInterface
  interface SourceChannelOpener {
    /** Opens the checked source file. */
    FileChannel open(Path sourcePath) throws IOException;
  }

  /** Writes exact stage content after its private no-clobber creation has succeeded. */
  @FunctionalInterface
  private interface ExactStageWriter {
    /** Writes and forces the exact caller-owned bytes through the newly created stage channel. */
    void write(PrivateOutputFile.OpenedFile destination) throws IOException;
  }
}
