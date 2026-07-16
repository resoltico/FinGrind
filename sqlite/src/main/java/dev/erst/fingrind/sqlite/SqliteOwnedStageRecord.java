package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Durable on-disk record that identifies one owned stage and its final artifact target. */
final class SqliteOwnedStageRecord {
  private static final int RESERVATION_ATTEMPTS = 8;

  private final Path stagedPath;
  private final Path recordPath;

  SqliteOwnedStageRecord(Path stagedPath, Path recordPath) {
    this.stagedPath = normalized(Objects.requireNonNull(stagedPath, "stagedPath"));
    this.recordPath = normalized(Objects.requireNonNull(recordPath, "recordPath"));
  }

  static SqliteOwnedStageRecord create(Path finalPath, String infix, String suffix) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    return create(
        normalizedFinalPath,
        () -> SqliteOwnedStageRecordCodec.stagedPath(normalizedFinalPath, infix, suffix));
  }

  static SqliteOwnedStageRecord create(Path finalPath, Supplier<Path> stagedPathSupplier) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    Objects.requireNonNull(stagedPathSupplier, "stagedPathSupplier");
    for (int attempt = 0; attempt < RESERVATION_ATTEMPTS; attempt++) {
      Path stagedPath = stagedPathSupplier.get();
      SqliteOwnedStageRecord record;
      try {
        record = recordExisting(normalizedFinalPath, stagedPath);
      } catch (IllegalStateException exception) {
        throw creationFailure(normalizedFinalPath, exception);
      }
      try {
        Files.createFile(stagedPath);
        return record;
      } catch (java.nio.file.FileAlreadyExistsException exception) {
        record.discardRecord();
      } catch (IOException exception) {
        record.discardRecord();
        throw creationFailure(normalizedFinalPath, exception);
      }
    }
    throw new IllegalStateException(
        "Unable to reserve a unique owned maintenance stage beside "
            + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
            + ".");
  }

  static SqliteOwnedStageRecord recordExisting(Path finalPath, Path stagedPath) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    Path normalizedStagedPath = normalized(Objects.requireNonNull(stagedPath, "stagedPath"));
    Path finalParent = parentOf(normalizedFinalPath);
    @Nullable Path stagedParent = normalizedStagedPath.getParent();
    if (!finalParent.equals(stagedParent)) {
      throw new IllegalArgumentException(
          "Owned stages must share the final artifact parent directory.");
    }
    return SqliteOwnedStageRecordCodec.write(normalizedFinalPath, normalizedStagedPath);
  }

  static List<SqliteOwnedStageRecord> findFor(Path finalPath) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    Path parent = parentOf(normalizedFinalPath);
    if (Files.notExists(parent, LinkOption.NOFOLLOW_LINKS)) {
      return List.of();
    }
    List<SqliteOwnedStageRecord> records = new ArrayList<>();
    try (DirectoryStream<Path> children = Files.newDirectoryStream(parent)) {
      for (Path candidate : children) {
        SqliteOwnedStageRecordCodec.read(candidate, normalizedFinalPath).ifPresent(records::add);
      }
      return List.copyOf(records);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to recover owned maintenance stages beside "
              + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
              + ".",
          exception);
    }
  }

  Path stagedPath() {
    return stagedPath;
  }

  /** Requires the durable record and staged file to still prove this operation owns the stage. */
  void requireIntactFor(Path finalPath) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    requireRecordFor(normalizedFinalPath);
    if (!Files.isRegularFile(stagedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The durable FinGrind maintenance-stage ownership record was altered before publication for "
              + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
              + ".");
    }
  }

  /** Leaves the durable ownership record in place while a native creator materializes the stage. */
  void vacateForNativeMaterialization(Path finalPath) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    requireIntactFor(normalizedFinalPath);
    try {
      Files.delete(stagedPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to prepare the owned FinGrind maintenance stage for native materialization beside "
              + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
              + ".",
          exception);
    }
  }

  void discard() {
    if (Files.exists(stagedPath, LinkOption.NOFOLLOW_LINKS)
        && !Files.isRegularFile(stagedPath, LinkOption.NOFOLLOW_LINKS)) {
      // The record is ours, but an altered stage is not safe to delete.
      discardRecord();
      return;
    }
    try {
      Files.deleteIfExists(stagedPath);
      Files.deleteIfExists(recordPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to remove the owned FinGrind maintenance stage "
              + SqliteMachinePaths.absoluteValue(stagedPath)
              + ".",
          exception);
    }
  }

  void discardRecord() {
    try {
      Files.deleteIfExists(recordPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to remove the owned FinGrind maintenance record "
              + SqliteMachinePaths.absoluteValue(recordPath)
              + ".",
          exception);
    }
  }

  private void requireRecordFor(Path normalizedFinalPath) {
    boolean recordMatches =
        SqliteOwnedStageRecordCodec.read(recordPath, normalizedFinalPath)
            .map(SqliteOwnedStageRecord::stagedPath)
            .filter(stagedPath::equals)
            .isPresent();
    if (!recordMatches) {
      throw new IllegalStateException(
          "The durable FinGrind maintenance-stage ownership record was altered before publication for "
              + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
              + ".");
    }
  }

  private static IllegalStateException creationFailure(Path finalPath, Exception cause) {
    return new IllegalStateException(
        "Failed to create one owned maintenance stage beside "
            + SqliteMachinePaths.absoluteValue(finalPath)
            + ".",
        cause);
  }

  private static Path normalized(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static Path parentOf(Path path) {
    return Objects.requireNonNull(path.getParent(), "finalPath parent");
  }
}
