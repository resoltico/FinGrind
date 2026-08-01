package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns one durable sidecar reservation while the final artifact path stays absent until
 * publication.
 */
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
    if (Files.exists(checkedFinalPath, LinkOption.NOFOLLOW_LINKS)) {
      reservationStage.releaseRetained();
      throw new FileAlreadyExistsException(checkedFinalPath.toString());
    }
    return new SqliteOwnedDestinationReservation(checkedFinalPath, reservationStage);
  }

  Path finalPath() {
    return finalPath;
  }

  SqliteOwnedStagedArtifact createStage(String infix, String suffix) {
    requireOpen();
    return SqliteOwnedStagedArtifact.create(finalPath, infix, suffix);
  }

  /** Publishes the staged artifact with the sole atomic no-clobber claim on the final path. */
  void publishRetainingStage(
      SqliteOwnedStagedArtifact stagedArtifact,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator)
      throws IOException {
    requireOpen();
    Objects.requireNonNull(stagedArtifact, "stagedArtifact").requireIntactFor(finalPath);
    Objects.requireNonNull(linkCreator, "linkCreator");
    reservationStage.requireIntactFor(finalPath);
    if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileAlreadyExistsException(finalPath.toString());
    }
    linkCreator.create(finalPath, stagedArtifact.stagedPath());
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    reservationStage.releaseRetained();
    closed = true;
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The FinGrind destination reservation was already released.");
    }
  }
}
