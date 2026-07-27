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
      if (createStageIfAbsent(normalizedFinalPath, stagedPath)) {
        return record;
      }
    }
    throw new IllegalStateException(
        "Unable to reserve a unique owned maintenance stage beside "
            + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
            + ".");
  }

  /**
   * Creates the stage once; a collision leaves opaque residue untouched and asks for a new name.
   */
  private static boolean createStageIfAbsent(Path finalPath, Path stagedPath) {
    try {
      SqliteSecureRegularFileAccess.createNewEmptyFile(stagedPath);
      return true;
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      return false;
    } catch (IOException exception) {
      throw creationFailure(finalPath, exception);
    }
  }

  static SqliteOwnedStageRecord recordExisting(Path finalPath, Path stagedPath) {
    Path normalizedFinalPath = normalized(Objects.requireNonNull(finalPath, "finalPath"));
    Path normalizedStagedPath = normalized(Objects.requireNonNull(stagedPath, "stagedPath"));
    if (SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
        normalizedFinalPath, normalizedStagedPath)) {
      throw new IllegalArgumentException(
          "An owned maintenance stage must never use its final artifact path.");
    }
    Path finalParent = parentOf(normalizedFinalPath);
    @Nullable Path stagedParent = normalizedStagedPath.getParent();
    if (!SqliteProtectedBookPathIdentity.sameExistingFilesystemObject(
        finalParent,
        Objects.requireNonNull(stagedParent, "stagedPath parent"),
        normalizedFinalPath)) {
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

  /**
   * Returns the one exact final target proven by a private stage-owner record, if unambiguous.
   *
   * <p>This is deliberately a capability lookup rather than a general stage-discovery API. A caller
   * can use the result only in conjunction with an already-held exact lease for the returned final
   * target. Two current owner records for the same stage are ambiguous and grant no authority, even
   * when their encoded targets match.
   */
  static @Nullable Path soleCurrentFinalTargetForStage(Path stagedPath) {
    Path normalizedStagedPath = normalized(Objects.requireNonNull(stagedPath, "stagedPath"));
    if (!Files.isRegularFile(normalizedStagedPath, LinkOption.NOFOLLOW_LINKS)) {
      return null;
    }
    Path parent = parentOf(normalizedStagedPath);
    if (Files.notExists(parent, LinkOption.NOFOLLOW_LINKS)) {
      return null;
    }
    @Nullable Path matchedFinalTarget = null;
    int matches = 0;
    try (DirectoryStream<Path> children = Files.newDirectoryStream(parent)) {
      for (Path candidate : children) {
        SqliteOwnedStageRecordCodec.@Nullable CurrentOwnerRecord owner =
            SqliteOwnedStageRecordCodec.readCurrent(candidate).orElse(null);
        if (owner == null
            || !SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
                owner.stagedPath(), normalizedStagedPath)) {
          continue;
        }
        matches++;
        if (matches > 1) {
          return null;
        }
        matchedFinalTarget = owner.finalPath();
      }
      return matchedFinalTarget;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to establish private FinGrind maintenance-stage ownership beside "
              + SqliteMachinePaths.absoluteValue(normalizedStagedPath)
              + ".",
          exception);
    }
  }

  /**
   * Detects untrusted owner-sidecar residue from every requested pair parent without adopting it.
   *
   * <p>Valid current opaque records are not a generic admission lock: only a complete immutable
   * pair claim can bind them into recoverable pair state. Retired target-derived or malformed owner
   * records are fail-closed because their ownership cannot be reconstructed safely.
   */
  static boolean hasUnsafeOwnerRecordResidue(Path firstFinalPath, Path secondFinalPath) {
    Objects.requireNonNull(firstFinalPath, "firstFinalPath");
    Objects.requireNonNull(secondFinalPath, "secondFinalPath");
    for (Path parent :
        SqliteProtectedBookPathIdentity.distinctPhysicalParents(firstFinalPath, secondFinalPath)) {
      if (Files.notExists(parent, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      try (DirectoryStream<Path> children = Files.newDirectoryStream(parent)) {
        for (Path candidate : children) {
          if (SqliteOwnedStageRecordCodec.isUnsafeOwnerRecordResidue(candidate)) {
            return true;
          }
        }
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to inspect protected-book stage-owner evidence beside "
                + SqliteMachinePaths.absoluteValue(parent)
                + ".",
            exception);
      }
    }
    return false;
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

  /**
   * Establishes the durable pre-publication boundary for one staged pair member.
   *
   * <p>The pair record is meaningful only if both stage bytes and the independent ownership records
   * which authorize their later use survive the same crash. Force them before the pair record is
   * promoted, then force this member's parent directory through the pair seam.
   */
  void forceForPairPublicationRecoveryBoundary(
      Path finalPath, SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    requireIntactFor(finalPath);
    forceRegularFile(stagedPath, "staged protected-book pair member");
    forceRegularFile(recordPath, "staged protected-book ownership record");
    Objects.requireNonNull(directoryForcer, "directoryForcer")
        .force(
            SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                .STAGED_MEMBER_DURABILITY,
            parentOf(stagedPath));
  }

  /**
   * Releases this process's authority over the stage while intentionally retaining its opaque stage
   * and owner records.
   *
   * <p>There is no portable Java unlink primitive that proves the object being removed is still the
   * object previously inspected. Retention therefore prevents a same-owner replacement race from
   * turning destructive pathname removal into deletion of another actor's artifact. Valid unbound
   * records are inert; pair-bound records remain immutable recovery evidence.
   */
  void releaseRetained() {
    // Intentionally no filesystem mutation.
  }

  private void requireRecordFor(Path normalizedFinalPath) {
    boolean recordMatches =
        SqliteOwnedStageRecordCodec.read(recordPath, normalizedFinalPath)
            .map(SqliteOwnedStageRecord::stagedPath)
            .filter(
                candidate ->
                    SqliteProtectedBookPathIdentity.sameNormalizedSpelling(candidate, stagedPath))
            .isPresent();
    if (!recordMatches) {
      throw new IllegalStateException(
          "The durable FinGrind maintenance-stage ownership record was altered before publication for "
              + SqliteMachinePaths.absoluteValue(normalizedFinalPath)
              + ".");
    }
  }

  private static void forceRegularFile(Path path, String description) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("The " + description + " is no longer a regular file.");
    }
    SqliteSecureRegularFileAccess.forceFile(path);
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
