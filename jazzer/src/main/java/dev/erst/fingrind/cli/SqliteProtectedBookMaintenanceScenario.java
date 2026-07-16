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
        Path root) {
      SqliteProtectedBookMaintenanceFuzzAssertions.exerciseIndependentBackupAndRestore(
          lifecycleWorkflow, readWorkflow, sourceAccess, root);
    }
  },
  LEGACY_BACKUP_RESTORE {
    @Override
    void exercise(
        CliBookLifecycleWorkflow lifecycleWorkflow,
        CliBookReadWorkflow readWorkflow,
        BookAccess sourceAccess,
        Path sourceBookPath,
        Path sourceKeyPath,
        Path root)
        throws IOException {
      SqliteProtectedBookMaintenanceFuzzAssertions.exerciseLegacyBackupRestore(
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
  REKEY_BACKUP_RESTORE_WITH_RELEASED_FORMER_KEY_PATH {
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
          .exerciseRekeyBackupRestoreWithReleasedFormerKeyPath(
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
