package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Clean-target facts used only before the authenticated journal reserves a new pair. */
sealed interface SqlitePairPublicationReconciliation
    permits SqlitePairPublicationReconciliationAbsent,
        SqlitePairPublicationReconciliationExistingCompleteBackup,
        SqlitePairPublicationReconciliationEvidenceBlocked {}

/** No retained pair-publication evidence constrains the requested targets. */
enum SqlitePairPublicationReconciliationAbsent implements SqlitePairPublicationReconciliation {
  INSTANCE
}

/** A complete backup already exists at its exact selected artifact pair. */
record SqlitePairPublicationReconciliationExistingCompleteBackup(
    Path backupArtifactPath, Path backupKeyPath) implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationExistingCompleteBackup {
    Objects.requireNonNull(backupArtifactPath, "backupArtifactPath");
    Objects.requireNonNull(backupKeyPath, "backupKeyPath");
  }
}

/** Clean-target occupancy could not prove a safe backup replay or a new transaction. */
record SqlitePairPublicationReconciliationEvidenceBlocked(
    Path bookArtifactPath, Path secretArtifactPath) implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationEvidenceBlocked {
    Objects.requireNonNull(bookArtifactPath, "bookArtifactPath");
    Objects.requireNonNull(secretArtifactPath, "secretArtifactPath");
  }
}
