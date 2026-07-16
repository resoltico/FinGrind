package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Owns atomic no-replace and selected-replacement publication of staged book artifacts. */
final class SqliteProtectedBookPublicationSupport {
  /** Creates one final hard link to a staged artifact without allowing replacement. */
  @FunctionalInterface
  interface NoReplaceLinkCreator {
    /** Creates one final link to the staged artifact. */
    void create(Path finalPath, Path stagedPath) throws IOException;
  }

  /** Deletes one path while a no-replace publication is being completed or rolled back. */
  @FunctionalInterface
  interface PathDeleter {
    /** Deletes the selected path. */
    void delete(Path path) throws IOException;
  }

  /** Atomically replaces one declared book target with its verified staged counterpart. */
  @FunctionalInterface
  interface AtomicBookMover {
    /** Moves the staged book onto its final target. */
    void move(Path stagedPath, Path finalPath) throws IOException;
  }

  private SqliteProtectedBookPublicationSupport() {}

  static void moveReplacing(Path source, Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /** Publishes a staged sibling without deleting it until its paired operation reaches commit. */
  static void publishRetainingStage(Path stagedPath, Path finalPath) throws IOException {
    publishRetainingStage(stagedPath, finalPath, Files::createLink);
  }

  /** Publishes one staged sibling through an explicit no-replace owner. */
  static void publishRetainingStage(
      Path stagedPath, Path finalPath, NoReplaceLinkCreator linkCreator) throws IOException {
    Objects.requireNonNull(stagedPath, "stagedPath");
    Objects.requireNonNull(finalPath, "finalPath");
    Objects.requireNonNull(linkCreator, "linkCreator").create(finalPath, stagedPath);
  }

  /** Publishes one staged secret with explicit filesystem operations for deterministic rollback. */
  static void publishAbsent(
      Path stagedPath,
      Path finalPath,
      NoReplaceLinkCreator linkCreator,
      PathDeleter stagedPathDeleter,
      PathDeleter finalPathDeleter)
      throws IOException {
    linkCreator.create(finalPath, stagedPath);
    try {
      stagedPathDeleter.delete(stagedPath);
    } catch (IOException cleanupFailure) {
      try {
        finalPathDeleter.delete(finalPath);
      } catch (IOException rollbackFailure) {
        cleanupFailure.addSuppressed(rollbackFailure);
      }
      throw cleanupFailure;
    }
  }
}
