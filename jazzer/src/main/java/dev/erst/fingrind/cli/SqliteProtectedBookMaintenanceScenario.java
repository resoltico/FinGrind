package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.IOException;
import java.nio.file.Path;

/** Enumerates the independently executable protected-book maintenance workflows. */
enum SqliteProtectedBookMaintenanceScenario {
  INDEPENDENT_BACKUP_AND_RESTORE {
    @Override
    void exercise(
        CliBookLifecycleWorkflow lifecycleWorkflow,
        CliBookReadWorkflow readWorkflow,
        BookAccess sourceAccess,
        Path sourceBookPath,
        Path sourceKeyPath,
        Path root)
        throws IOException {
      SqliteProtectedBookMaintenanceFuzzAssertions.exerciseIndependentBackupAndRestore(
          lifecycleWorkflow, readWorkflow, sourceAccess, root);
    }
  },
  UNATTESTED_BACKUP_RESTORE_REJECTED {
    @Override
    void exercise(
        CliBookLifecycleWorkflow lifecycleWorkflow,
        CliBookReadWorkflow readWorkflow,
        BookAccess sourceAccess,
        Path sourceBookPath,
        Path sourceKeyPath,
        Path root)
        throws IOException {
      SqliteProtectedBookMaintenanceFuzzAssertions.exerciseUnattestedBackupRestoreRejection(
          lifecycleWorkflow, readWorkflow, sourceBookPath, sourceKeyPath, root);
    }
  },
  GENERATED_SECRET_COLLISION {
    @Override
    void exercise(
        CliBookLifecycleWorkflow lifecycleWorkflow,
        CliBookReadWorkflow readWorkflow,
        BookAccess sourceAccess,
        Path sourceBookPath,
        Path sourceKeyPath,
        Path root)
        throws IOException {
      SqliteProtectedBookMaintenanceFuzzAssertions.exerciseGeneratedSecretCollision(
          lifecycleWorkflow, sourceAccess, sourceBookPath, root);
    }
  },
  UNACKNOWLEDGED_DESTINATION_AND_REKEY_COLLISIONS {
    @Override
    void exercise(
        CliBookLifecycleWorkflow lifecycleWorkflow,
        CliBookReadWorkflow readWorkflow,
        BookAccess sourceAccess,
        Path sourceBookPath,
        Path sourceKeyPath,
        Path root)
        throws IOException {
      SqliteProtectedBookMaintenanceFuzzAssertions
          .exerciseUnacknowledgedDestinationAndRekeyCollisions(
              lifecycleWorkflow, sourceAccess, sourceBookPath, root);
    }
  },
  REKEY_BACKUP_RESTORE_WITHOUT_REMOVING_FORMER_KEY {
    @Override
    void exercise(
        CliBookLifecycleWorkflow lifecycleWorkflow,
        CliBookReadWorkflow readWorkflow,
        BookAccess sourceAccess,
        Path sourceBookPath,
        Path sourceKeyPath,
        Path root)
        throws IOException {
      SqliteProtectedBookMaintenanceFuzzAssertions
          .exerciseRekeyBackupRestoreWithoutRemovingFormerKey(
              lifecycleWorkflow, readWorkflow, sourceAccess, sourceBookPath, sourceKeyPath, root);
    }
  };

  abstract void exercise(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookReadWorkflow readWorkflow,
      BookAccess sourceAccess,
      Path sourceBookPath,
      Path sourceKeyPath,
      Path root)
      throws IOException;
}
