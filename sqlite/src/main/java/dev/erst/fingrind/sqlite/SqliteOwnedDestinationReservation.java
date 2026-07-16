package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Owns an absent final-artifact reservation until one staged artifact replaces it. */
final class SqliteOwnedDestinationReservation implements AutoCloseable {
  private final Path finalPath;
  private final SqliteOwnedStagedArtifact reservationStage;
  private boolean closed;

  private SqliteOwnedDestinationReservation(
      Path finalPath, SqliteOwnedStagedArtifact reservationStage) {
    this.finalPath = Objects.requireNonNull(finalPath, "finalPath");
    this.reservationStage = Objects.requireNonNull(reservationStage, "reservationStage");
  }

  static SqliteOwnedDestinationReservation reserve(Path finalPath) throws IOException {
    Path checkedFinalPath = Objects.requireNonNull(finalPath, "finalPath");
    SqliteOwnedStagedArtifact reservationStage =
        SqliteOwnedStagedArtifact.create(checkedFinalPath, ".reservation-", ".claim");
    try {
      Files.createLink(checkedFinalPath, reservationStage.stagedPath());
      return new SqliteOwnedDestinationReservation(checkedFinalPath, reservationStage);
    } catch (IOException exception) {
      reservationStage.discard();
      throw exception;
    }
  }

  Path finalPath() {
    return finalPath;
  }

  SqliteOwnedStagedArtifact createStage(String infix, String suffix) {
    requireOpen();
    return SqliteOwnedStagedArtifact.create(finalPath, infix, suffix);
  }

  /**
   * Replaces this exact reservation with a durable hard-link publication of the staged artifact.
   */
  void publishRetainingStage(
      SqliteOwnedStagedArtifact stagedArtifact,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator)
      throws IOException {
    requireOpen();
    Objects.requireNonNull(stagedArtifact, "stagedArtifact").requireIntactFor(finalPath);
    Objects.requireNonNull(linkCreator, "linkCreator");
    reservationStage.requireIntactFor(finalPath);
    if (!isCurrentReservation()) {
      if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
        throw new FileAlreadyExistsException(finalPath.toString());
      }
      throw new IOException(
          "The FinGrind final-artifact reservation disappeared before publication: "
              + SqliteMachinePaths.absoluteValue(finalPath)
              + ".");
    }
    Files.delete(finalPath);
    linkCreator.create(finalPath, stagedArtifact.stagedPath());
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    try {
      if (isCurrentReservation()) {
        Files.delete(finalPath);
      }
      reservationStage.discard();
      closed = true;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to release one owned FinGrind destination reservation at "
              + SqliteMachinePaths.absoluteValue(finalPath)
              + ".",
          exception);
    }
  }

  private boolean isCurrentReservation() throws IOException {
    return Files.isRegularFile(finalPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(reservationStage.stagedPath(), LinkOption.NOFOLLOW_LINKS)
        && Files.isSameFile(finalPath, reservationStage.stagedPath());
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The FinGrind destination reservation was already released.");
    }
  }
}
