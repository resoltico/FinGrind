package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RecoverRekeyResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRecoveryAction;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Inspects and applies sibling rekey rollback artifacts for one protected book path. */
public final class SqliteRekeyRecoveryService {
  /** Inspects, restores, or deletes rollback artifacts for the selected book path. */
  public ContractDecision<RecoverRekeyResult> recover(
      Path bookFilePath, RekeyRecoveryAction action, @Nullable Path rollbackArtifactPath) {
    Path normalizedBookPath = SqliteBookMaintenanceFiles.normalize(bookFilePath, "bookFilePath");
    Objects.requireNonNull(action, "action");
    return switch (action) {
      case INSPECT -> inspect(normalizedBookPath);
      case RESTORE -> restore(normalizedBookPath, rollbackArtifactPath);
      case DELETE -> delete(normalizedBookPath, rollbackArtifactPath);
    };
  }

  private ContractDecision<RecoverRekeyResult> inspect(Path normalizedBookPath) {
    return ContractDecision.accepted(
        new RecoverRekeyResult.Inspected(
            normalizedBookPath, staleRollbackArtifacts(normalizedBookPath)));
  }

  private ContractDecision<RecoverRekeyResult> restore(
      Path normalizedBookPath, @Nullable Path rollbackArtifactPath) {
    List<Path> liveBookBlockingArtifacts = blockingArtifactsWithoutRollback(normalizedBookPath);
    if (!liveBookBlockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new RecoverRekeyResult.Rejected(
              new BookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, liveBookBlockingArtifacts)));
    }
    ArtifactSelection selection =
        selectedRollbackArtifact(normalizedBookPath, rollbackArtifactPath);
    return switch (selection) {
      case SelectedArtifact(Path selectedRollbackArtifact) -> {
        SqliteBookMaintenanceFiles.replaceBook(selectedRollbackArtifact, normalizedBookPath);
        yield ContractDecision.accepted(
            new RecoverRekeyResult.Restored(normalizedBookPath, selectedRollbackArtifact));
      }
      case RejectedSelection(RecoverRekeyResult.Rejected rejected) ->
          ContractDecision.accepted(rejected);
    };
  }

  private ContractDecision<RecoverRekeyResult> delete(
      Path normalizedBookPath, @Nullable Path rollbackArtifactPath) {
    ArtifactSelection selection =
        selectedRollbackArtifact(normalizedBookPath, rollbackArtifactPath);
    return switch (selection) {
      case SelectedArtifact(Path selectedRollbackArtifact) -> {
        SqliteBookMaintenanceFiles.deleteRollbackArtifact(selectedRollbackArtifact);
        yield ContractDecision.accepted(
            new RecoverRekeyResult.Deleted(normalizedBookPath, selectedRollbackArtifact));
      }
      case RejectedSelection(RecoverRekeyResult.Rejected rejected) ->
          ContractDecision.accepted(rejected);
    };
  }

  private ArtifactSelection selectedRollbackArtifact(
      Path normalizedBookPath, @Nullable Path rollbackArtifactPath) {
    List<Path> rollbackArtifacts = staleRollbackArtifacts(normalizedBookPath);
    if (rollbackArtifactPath == null) {
      if (rollbackArtifacts.isEmpty()) {
        return new RejectedSelection(
            new RecoverRekeyResult.Rejected(
                new BookMaintenanceRejection.NoRollbackArtifactsFound(normalizedBookPath)));
      }
      if (rollbackArtifacts.size() > 1) {
        return new RejectedSelection(
            new RecoverRekeyResult.Rejected(
                new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                    normalizedBookPath, rollbackArtifacts)));
      }
      return new SelectedArtifact(rollbackArtifacts.getFirst());
    }
    Path normalizedRollbackArtifactPath =
        SqliteBookMaintenanceFiles.normalize(rollbackArtifactPath, "rollbackArtifactPath");
    if (!Files.exists(normalizedRollbackArtifactPath, LinkOption.NOFOLLOW_LINKS)) {
      return new RejectedSelection(
          new RecoverRekeyResult.Rejected(
              new BookMaintenanceRejection.RollbackArtifactNotFound(
                  normalizedRollbackArtifactPath)));
    }
    if (!SqliteRekeyRollbackFile.isRollbackArtifactForBook(
        normalizedBookPath, normalizedRollbackArtifactPath)) {
      return new RejectedSelection(
          new RecoverRekeyResult.Rejected(
              new BookMaintenanceRejection.RollbackArtifactNotForBook(
                  normalizedBookPath, normalizedRollbackArtifactPath)));
    }
    return new SelectedArtifact(normalizedRollbackArtifactPath);
  }

  private static List<Path> blockingArtifactsWithoutRollback(Path normalizedBookPath) {
    return SqliteBookMaintenanceFiles.blockingArtifactsForBook(normalizedBookPath).stream()
        .filter(
            path -> !SqliteRekeyRollbackFile.isRollbackArtifactForBook(normalizedBookPath, path))
        .toList();
  }

  private static List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
    try {
      return SqliteRekeyRollbackFile.staleRollbackArtifacts(normalizedBookPath);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect FinGrind SQLite rollback artifacts beside " + normalizedBookPath + ".",
          exception);
    }
  }

  /** Internal selection result for one rollback-artifact resolution attempt. */
  private sealed interface ArtifactSelection permits SelectedArtifact, RejectedSelection {}

  private record SelectedArtifact(Path rollbackArtifactPath) implements ArtifactSelection {
    private SelectedArtifact {
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  private record RejectedSelection(RecoverRekeyResult.Rejected rejected)
      implements ArtifactSelection {
    private RejectedSelection {
      Objects.requireNonNull(rejected, "rejected");
    }
  }
}
