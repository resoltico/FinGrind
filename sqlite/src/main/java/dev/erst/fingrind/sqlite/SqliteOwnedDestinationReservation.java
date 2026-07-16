package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Owns an absent final-artifact reservation until one staged artifact replaces it. */
final class SqliteOwnedDestinationReservation implements AutoCloseable {
  private static final String MARKER_PREFIX = "fingrind-destination-reservation-v1:";

  private final Path finalPath;
  private final SqliteOwnedStagedArtifact reservationStage;
  private final byte[] reservationMarker;
  private boolean closed;

  private SqliteOwnedDestinationReservation(
      Path finalPath, SqliteOwnedStagedArtifact reservationStage, byte[] reservationMarker) {
    this.finalPath = Objects.requireNonNull(finalPath, "finalPath");
    this.reservationStage = Objects.requireNonNull(reservationStage, "reservationStage");
    this.reservationMarker = Objects.requireNonNull(reservationMarker, "reservationMarker").clone();
  }

  static SqliteOwnedDestinationReservation reserve(Path finalPath) throws IOException {
    Path checkedFinalPath = Objects.requireNonNull(finalPath, "finalPath");
    SqliteOwnedStagedArtifact reservationStage =
        SqliteOwnedStagedArtifact.create(checkedFinalPath, ".reservation-", ".claim");
    byte[] reservationMarker = reservationMarkerFor(reservationStage.stagedPath());
    try {
      Files.write(
          checkedFinalPath,
          reservationMarker,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      return new SqliteOwnedDestinationReservation(
          checkedFinalPath, reservationStage, reservationMarker);
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
    return isReservationMarkerFor(finalPath, reservationStage.stagedPath(), reservationMarker);
  }

  /** Recognizes a durable reservation marker from its recorded staged-artifact identity. */
  static boolean isReservationMarker(Path finalPath, Path reservationStagePath) {
    try {
      return isReservationMarkerFor(
          Objects.requireNonNull(finalPath, "finalPath"),
          Objects.requireNonNull(reservationStagePath, "reservationStagePath"),
          reservationMarkerFor(reservationStagePath));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect one FinGrind destination reservation at "
              + SqliteMachinePaths.absoluteValue(finalPath)
              + ".",
          exception);
    }
  }

  private static boolean isReservationMarkerFor(
      Path finalPath, Path reservationStagePath, byte[] expectedMarker) throws IOException {
    return Files.isRegularFile(finalPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(reservationStagePath, LinkOption.NOFOLLOW_LINKS)
        && Files.size(finalPath) == expectedMarker.length
        && Arrays.equals(Files.readAllBytes(finalPath), expectedMarker);
  }

  private static byte[] reservationMarkerFor(Path reservationStagePath) {
    String encodedStagePath =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                reservationStagePath
                    .toAbsolutePath()
                    .normalize()
                    .toString()
                    .getBytes(StandardCharsets.UTF_8));
    return (MARKER_PREFIX + encodedStagePath).getBytes(StandardCharsets.US_ASCII);
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The FinGrind destination reservation was already released.");
    }
  }
}
