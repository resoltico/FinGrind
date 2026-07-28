package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies member filtering keeps source and final-target requests explicitly disjoint. */
class SqliteWorkflowScopeRequestsTest {

  @Test
  void exceptMemberRemovesOnlyTheRequestedWorkflowPosition() {
    Path parent = Path.of("workflow-member-filter").toAbsolutePath();
    var source =
        new SqliteWorkflowScopeRequests.Request(
            parent.resolve("source.sqlite"),
            parent,
            SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT,
            SqliteWorkflowScopeRequests.Member.SOURCE,
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);
    var book =
        new SqliteWorkflowScopeRequests.Request(
            parent.resolve("book.sqlite"),
            parent,
            SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
            SqliteWorkflowScopeRequests.Member.BOOK_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);
    var secret =
        new SqliteWorkflowScopeRequests.Request(
            parent.resolve("book.key"),
            parent,
            SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
            SqliteWorkflowScopeRequests.Member.SECRET_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);

    assertEquals(
        List.of(book, secret),
        SqliteWorkflowScopeRequests.exceptMember(
            List.of(source, book, secret), SqliteWorkflowScopeRequests.Member.SOURCE));
    assertEquals(
        List.of(source, secret),
        SqliteWorkflowScopeRequests.exceptMember(
            List.of(source, book, secret), SqliteWorkflowScopeRequests.Member.BOOK_TARGET));
  }
}
