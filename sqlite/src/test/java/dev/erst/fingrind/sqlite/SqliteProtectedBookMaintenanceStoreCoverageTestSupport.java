package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Shared fixture and assertion support for protected-book maintenance store coverage suites. */
abstract class SqliteProtectedBookMaintenanceStoreCoverageTestSupport
    extends SqliteNativeBridgeTestSupport {
  protected static final SqliteProtectedBookVerificationSupport VERIFICATION_SUPPORT =
      new SqliteProtectedBookVerificationSupport();
  protected static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError("This coverage suite uses key-file-backed access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError("This coverage suite uses key-file-backed access only.");
          };

  protected static ProtectedBookMaintenanceStore.HeldLease acceptedLease(
      ProtectedBookMaintenanceStore.LeaseAcquisition acquisition) {
    return switch (acquisition) {
      case ProtectedBookMaintenanceStore.HeldLease heldLease -> heldLease;
      case ProtectedBookMaintenanceStore.LeaseBusy leaseBusy ->
          throw new AssertionError(
              "Expected one acquired lease but got busy: " + leaseBusy.artifactPath());
    };
  }

  protected SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  protected static ProtectedBookAccess localAccess(BookAccess bookAccess) {
    return ProtectedBookAccess.fromPublished(bookAccess);
  }

  protected static ProtectedBookMaintenanceStore.VerifiedBook verifiedBook(
      SqliteProtectedBookMaintenanceStore store, BookAccess bookAccess) {
    return switch (acceptedValue(store.verifyInitializedBook(localAccess(bookAccess)))) {
      case ProtectedBookMaintenanceStore.VerifiedBook verifiedBook -> verifiedBook;
      case ProtectedBookMaintenanceStore.VerificationFailure verificationFailure ->
          throw new AssertionError(
              "Expected one verified book but got " + verificationFailure.failure());
    };
  }

  protected void initializeBook(BookAccess bookAccess) {
    try {
      Path parentDirectory = bookAccess.bookFilePath().getParent();
      if (parentDirectory != null) {
        Files.createDirectories(parentDirectory);
        SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create the test book directory.", exception);
    }
    withOpenDatabase(
        bookAccess,
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          SqliteAuditEventWriter.insertAuditEvent(
              database, BookAuditEvent.bookOpened(Instant.parse("2026-05-19T09:00:00Z")));
        });
  }

  protected Path writeArtifact(String fileName, String content) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parent = artifactPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    }
    Files.writeString(artifactPath, content);
    return artifactPath;
  }

  protected int auditEventCount(BookAccess bookAccess, String eventKind) {
    final int[] count = new int[1];
    withOpenDatabase(
        bookAccess,
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(
                  database,
                  "select count(*) from audit_event where event_kind = '" + eventKind + "'")) {
            assertEquals(SqliteNativeResultCode.code("ROW"), statement.step());
            count[0] = statement.columnInt(0);
            assertEquals(SqliteNativeResultCode.code("DONE"), statement.step());
          }
        });
    return count[0];
  }

  protected static Path maintenanceJournalPath(Path bookPath) {
    return bookPath.resolveSibling(bookPath.getFileName().toString() + ".maintenance-log.jsonl");
  }

  protected static <T> T acceptedValue(MaintenanceDecision<T> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<T>(T value) -> value;
      case MaintenanceDecision.Failed<T>(MaintenanceFailure failure) ->
          throw new AssertionError("Expected accepted maintenance decision but got " + failure);
    };
  }

  protected static void assertFailedDescriptor(
      MaintenanceDecision<?> decision, ContractErrors.Descriptor expectedDescriptor) {
    MaintenanceFailure failure =
        switch (decision) {
          case MaintenanceDecision.Accepted<?> accepted ->
              throw new AssertionError("Expected failed maintenance decision but got " + accepted);
          case MaintenanceDecision.Failed<?> failed -> failed.failure();
        };
    assertEquals(expectedDescriptor, failure.descriptor());
  }

  protected static void assertVerificationFailure(
      ProtectedBookMaintenanceStore.BookVerification verification,
      Path expectedArtifactPath,
      ProtectedBookVerificationFailure expectedFailure) {
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        assertInstanceOf(ProtectedBookMaintenanceStore.VerificationFailure.class, verification);
    assertEquals(expectedArtifactPath.toAbsolutePath().normalize(), failure.artifactPath());
    assertEquals(expectedFailure, failure.failure());
  }

  protected static StagedBookReplacement newStagedReplacement(
      Path stagedBookPath, Path targetBookPath, @Nullable Path previousTargetBackupPath) {
    return new SqliteStagedBookReplacement(
        stagedBookPath, targetBookPath, previousTargetBackupPath);
  }

  protected static void setPrivateField(Object target, String fieldName, @Nullable Object value) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(target.getClass(), MethodHandles.lookup());
      switch (fieldName) {
        case "backupPassphrase" -> {
          VarHandle field =
              lookup.findVarHandle(target.getClass(), fieldName, SqliteBookPassphrase.class);
          field.set(target, value);
        }
        case "backupFilePublished", "backupKeyFilePublished" -> {
          VarHandle field = lookup.findVarHandle(target.getClass(), fieldName, boolean.class);
          field.set(
              target,
              ((Boolean) java.util.Objects.requireNonNull(value, fieldName)).booleanValue());
        }
        default ->
            throw new IllegalArgumentException("Unsupported private test field: " + fieldName);
      }
    } catch (IllegalAccessException | NoSuchFieldException exception) {
      throw new LinkageError(
          "Failed to set one private field on the maintenance test fixture: " + fieldName + ".",
          exception);
    }
  }

  protected static AclEntry ownerDirectoryAccessEntry(UserPrincipal owner) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(owner)
        .setPermissions(
            Set.of(
                AclEntryPermission.LIST_DIRECTORY,
                AclEntryPermission.ADD_FILE,
                AclEntryPermission.EXECUTE))
        .build();
  }

  /** ACL view test double that throws while hardening attempts to rewrite ACL entries. */
  protected static final class ThrowingAclFileAttributeView implements AclFileAttributeView {
    private final UserPrincipal owner;
    private final String message;

    ThrowingAclFileAttributeView(UserPrincipal owner, String message) {
      this.owner = java.util.Objects.requireNonNull(owner, "owner");
      this.message = java.util.Objects.requireNonNull(message, "message");
    }

    @Override
    public String name() {
      return "acl";
    }

    @Override
    public List<AclEntry> getAcl() {
      return List.of();
    }

    @Override
    public void setAcl(List<AclEntry> acl) throws IOException {
      throw new IOException(message);
    }

    @Override
    public UserPrincipal getOwner() {
      return owner;
    }

    @Override
    public void setOwner(UserPrincipal owner) {}
  }
}
