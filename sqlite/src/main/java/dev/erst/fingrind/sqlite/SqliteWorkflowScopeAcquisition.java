package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import java.nio.file.Path;
import java.util.Objects;

/** Complete-or-busy result for acquisition of one immutable source-and-target workflow scope. */
sealed interface SqliteWorkflowScopeAcquisition
    permits SqliteWorkflowScopeHeld, SqliteWorkflowScopeBusy {}

/** Every declared source and final-target reference is held. */
record SqliteWorkflowScopeHeld(SqliteWorkflowLeaseScope scope)
    implements SqliteWorkflowScopeAcquisition {
  SqliteWorkflowScopeHeld {
    Objects.requireNonNull(scope, "scope");
  }
}

/** One declared workflow member was active or held by another workflow. */
record SqliteWorkflowScopeBusy(Path artifactPath, ProtectedBookMaintenanceArtifactRole artifactRole)
    implements SqliteWorkflowScopeAcquisition {
  SqliteWorkflowScopeBusy {
    Objects.requireNonNull(artifactPath, "artifactPath");
    Objects.requireNonNull(artifactRole, "artifactRole");
  }
}
