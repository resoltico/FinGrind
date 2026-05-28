package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEventKind;
import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import java.time.Instant;

/** Owns protected-book maintenance audit persistence for SQLite-backed workflows. */
final class SqliteProtectedBookMaintenanceAuditSupport {
  MaintenanceDecision<MaintenanceCompletion> appendResolvedMaintenanceAudit(
      java.nio.file.Path normalizedBookPath,
      SqliteBookPassphrase passphrase,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditKind auditKind) {
    try (SqliteBookPassphrase ignored = passphrase;
        SqliteNativeDatabase database =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      database.executeStatement("begin immediate");
      try {
        SqliteAuditEventWriter.insertAuditEvent(
            database, maintenanceAuditEvent(auditKind, recordedAt));
        database.executeStatement("commit");
        return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(database);
        throw exception;
      }
    }
  }

  MaintenanceDecision<MaintenanceCompletion> appendResolvedMaintenanceAuditCompensation(
      java.nio.file.Path normalizedBookPath,
      SqliteBookPassphrase passphrase,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind) {
    try (SqliteBookPassphrase ignored = passphrase;
        SqliteNativeDatabase database =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      database.executeStatement("begin immediate");
      try {
        SqliteAuditEventWriter.insertAuditEvent(
            database, maintenanceAuditCompensationEvent(auditKind, recordedAt));
        database.executeStatement("commit");
        return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(database);
        throw exception;
      }
    }
  }

  static BookAuditEvent maintenanceAuditEvent(
      ProtectedBookMaintenanceAuditKind auditKind, Instant recordedAt) {
    return switch (auditKind) {
      case BACKUP_CREATED ->
          new BookAuditEvent(recordedAt, BookAuditEventKind.BACKUP_CREATED, null, null, null);
      case BACKUP_RESTORED ->
          new BookAuditEvent(recordedAt, BookAuditEventKind.BACKUP_RESTORED, null, null, null);
      case REKEY_ROLLBACK_RESTORED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.REKEY_ROLLBACK_RESTORED, null, null, null);
      case REKEY_ROLLBACK_DELETED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.REKEY_ROLLBACK_DELETED, null, null, null);
    };
  }

  static BookAuditEvent maintenanceAuditCompensationEvent(
      ProtectedBookMaintenanceAuditCompensationKind auditKind, Instant recordedAt) {
    return switch (auditKind) {
      case BACKUP_CREATED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.BACKUP_CREATED_COMPENSATED, null, null, null);
      case REKEY_ROLLBACK_DELETED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.REKEY_ROLLBACK_DELETED_COMPENSATED, null, null, null);
    };
  }
}
