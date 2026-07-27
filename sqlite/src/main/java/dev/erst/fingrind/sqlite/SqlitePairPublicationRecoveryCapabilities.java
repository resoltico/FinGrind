package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;

/** Acquires only the primitive witnesses needed by the selected recovery plans. */
final class SqlitePairPublicationRecoveryCapabilities {
  private SqlitePairPublicationRecoveryCapabilities() {}

  static SqlitePublicationCapabilityWitness.Set acquire(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan bookPlan,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan secretPlan,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    try {
      java.util.List<SqlitePublicationCapabilityWitness.Requirement> requirements =
          new java.util.ArrayList<>();
      addBookRequirements(requirements, record, bookPlan);
      addSecretRequirements(requirements, record, secretPlan);
      return SqlitePublicationCapabilityWitness.acquire(
          requirements,
          java.nio.file.Files::createLink,
          SqliteProtectedBookPublicationSupport::moveReplacing);
    } catch (SqlitePublicationCapabilityWitness.AcquisitionFailure failure) {
      throw SqliteProtectedBookPairPublicationTargets.capabilityAcquisitionFailure(
          failure,
          record.bookTargetPath,
          record.secretTargetPath,
          record.bookTargetPolicy,
          bookArtifactRole,
          secretArtifactRole);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire retained FinGrind recovery publication capability witnesses.",
          exception);
    }
  }

  private static void addBookRequirements(
      java.util.List<SqlitePublicationCapabilityWitness.Requirement> requirements,
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan bookPlan) {
    if (bookPlan
        != SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE) {
      return;
    }
    if (record.bookTargetPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED) {
      requirements.add(
          SqlitePublicationCapabilityWitness.Requirement.atomicReplace(record.bookTargetPath));
    }
    requirements.add(
        SqlitePublicationCapabilityWitness.Requirement.noReplace(record.bookTargetPath));
  }

  private static void addSecretRequirements(
      java.util.List<SqlitePublicationCapabilityWitness.Requirement> requirements,
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan secretPlan) {
    if (secretPlan
        == SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE) {
      requirements.add(
          SqlitePublicationCapabilityWitness.Requirement.noReplace(record.secretTargetPath));
    }
  }
}
