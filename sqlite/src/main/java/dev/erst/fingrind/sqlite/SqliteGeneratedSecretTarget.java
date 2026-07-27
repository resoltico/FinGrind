package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Owns absent-target reservation, staging, and atomic publication for generated secrets. */
final class SqliteGeneratedSecretTarget {
  private final Path finalPath;

  private SqliteGeneratedSecretTarget(Path finalPath) {
    this.finalPath = Objects.requireNonNull(finalPath, "finalPath");
  }

  static SqliteGeneratedSecretTarget requireAbsent(Path finalPath) {
    Path checkedPath = Objects.requireNonNull(finalPath, "finalPath");
    if (Files.exists(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteGeneratedSecretTargetOccupiedException(checkedPath);
    }
    return new SqliteGeneratedSecretTarget(checkedPath);
  }

  SqliteOwnedStagedArtifact createStage(String infix, String suffix) {
    return SqliteOwnedStagedArtifact.create(finalPath, infix, suffix);
  }

  /** Publishes a staged secret without deleting the stage until its pair is durably complete. */
  void publishRetainingStage(
      Path stagedPath, SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator)
      throws IOException {
    Objects.requireNonNull(stagedPath, "stagedPath");
    Objects.requireNonNull(linkCreator, "linkCreator");
    try {
      linkCreator.create(finalPath, stagedPath);
    } catch (FileAlreadyExistsException exception) {
      throw new SqliteGeneratedSecretTargetOccupiedException(finalPath, exception);
    } catch (UnsupportedOperationException exception) {
      throw atomicPublicationUnsupported(finalPath, exception);
    } catch (FileSystemException exception) {
      if (signalsUnsupportedAtomicPublication(exception)) {
        throw atomicPublicationUnsupported(finalPath, exception);
      }
      throw exception;
    }
  }

  private static SqliteCallerPathContractException atomicPublicationUnsupported(
      Path finalPath, Throwable cause) {
    return new SqliteCallerPathContractException(
        finalPath,
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
        "The FinGrind generated-secret target requires atomic no-replace publication: "
            + finalPath
            + ".",
        cause);
  }

  private static boolean signalsUnsupportedAtomicPublication(FileSystemException exception) {
    String reason = exception.getReason();
    return reason != null && reason.toLowerCase(Locale.ROOT).contains("not supported");
  }
}
