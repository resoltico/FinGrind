package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Reserves authenticated journal stages after validating the selected final targets. */
final class SqliteProtectedBookPairPublicationTargets {
  private SqliteProtectedBookPairPublicationTargets() {}

  /**
   * Reserves one journal-owned pair after preflighting its caller-selected targets.
   *
   * <p>No final-name reservation, retained stage, or capability witness survives this boundary. The
   * authenticated transaction journal is the sole owner of the two private stages.
   */
  static PreparedPairPublication prepareJournaledWithHeldLeases(
      SqlitePairPublicationPreparationResources resources,
      Path secretTargetPath,
      Path bookTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      ProtectedBookPairPublicationRecoveryRequest request,
      PublicationTransactionService transactions) {
    SqlitePairPublicationPreparationResources checkedResources =
        Objects.requireNonNull(resources, "resources");
    Path checkedBookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    RestoredBookTargetPolicy checkedPolicy =
        Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    PublicationTransactionOwnerContext ownerContext =
        SqliteProtectedBookPublicationOwnerContext.forPair(
            Objects.requireNonNull(request, "request"),
            checkedBookTargetPath,
            checkedSecretTargetPath,
            checkedPolicy);
    try {
      SqliteProtectedBookStagingTargetPreparation.ensureArtifactParents(
          checkedBookTargetPath, checkedSecretTargetPath);
      if (checkedPolicy == RestoredBookTargetPolicy.REQUIRE_ABSENT
          && Files.exists(checkedBookTargetPath, LinkOption.NOFOLLOW_LINKS)) {
        throw new ProtectedBookMaintenanceRejectionException(
            occupiedBookTargetRejection(bookArtifactRole, checkedBookTargetPath));
      }
      SqliteGeneratedSecretTarget.requireAbsent(checkedSecretTargetPath);
      SqlitePublicationTransactionPair pair =
          SqlitePublicationTransactionPair.reserve(
              Objects.requireNonNull(transactions, "transactions"),
              checkedBookTargetPath,
              checkedSecretTargetPath,
              checkedPolicy,
              ownerContext);
      return checkedResources.transferToJournaledPreparedPublication(
          pair, checkedBookTargetPath, checkedSecretTargetPath, checkedPolicy);
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.SecretTargetOccupied(exception.targetPath()),
          exception);
    } catch (SqliteCallerPathContractException exception) {
      ProtectedBookMaintenanceArtifactRole artifactRole =
          exception.requestedPath().equals(checkedBookTargetPath)
              ? bookArtifactRole
              : secretArtifactRole;
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          artifactRole, exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to reserve the FinGrind protected-book publication transaction.", exception);
    }
  }

  static ProtectedBookMaintenanceRejection occupiedBookTargetRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole, Path bookTargetPath) {
    return switch (artifactRole) {
      case BACKUP_TARGET ->
          new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(bookTargetPath);
      case LIVE_BOOK, RESTORED_TARGET ->
          new ProtectedBookMaintenanceRejection.BookDestinationOccupied(bookTargetPath);
      case LIVE_BOOK_KEY_SOURCE,
          BACKUP_SOURCE,
          BACKUP_KEY_SOURCE,
          BACKUP_KEY_TARGET,
          NEW_BOOK_KEY_TARGET ->
          throw new IllegalArgumentException(
              "An absent protected-book target cannot use artifact role " + artifactRole + ".");
    };
  }
}
