package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared fixture support for the durable, no-clobber publication primitives. */
abstract class SqliteArtifactPublicationTestSupport extends SqliteNativeBridgeTestSupport {
  protected static final SqliteProtectedBookVerificationSupport VERIFICATION_SUPPORT =
      new SqliteProtectedBookVerificationSupport();
  protected static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError("This fixture requires a key-file passphrase source.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError("This fixture requires a key-file passphrase source.");
          };

  protected SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  protected static ProtectedBookAccess localAccess(BookAccess bookAccess) {
    return ProtectedBookAccess.fromPublished(bookAccess);
  }

  protected static ProtectedBookMaintenanceStore.VerifiedBook verifiedBook(
      SqliteProtectedBookMaintenanceStore store, BookAccess bookAccess) {
    return switch (acceptedValue(
        store.verifyInitializedBook(
            localAccess(bookAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))) {
      case ProtectedBookMaintenanceStore.VerifiedBook verifiedBook -> verifiedBook;
      case ProtectedBookMaintenanceStore.VerificationFailure verificationFailure ->
          throw new AssertionError(
              "Expected a verified book but got " + verificationFailure.failure());
    };
  }

  protected static ProtectedBookMaintenanceStore.PreparedPairPublication prepareBackupPair(
      SqliteProtectedBookMaintenanceStore store, Path backupFilePath, Path backupKeyFilePath) {
    return store.preparePairPublication(
        backupKeyFilePath,
        backupFilePath,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
  }

  protected static ProtectedBookMaintenanceStore.PreparedPairPublication prepareRestoredBookPair(
      SqliteProtectedBookMaintenanceStore store,
      Path restoredBookPath,
      Path restoredBookKeyPath,
      RestoredBookTargetPolicy targetPolicy) {
    return store.preparePairPublication(
        restoredBookKeyPath,
        restoredBookPath,
        targetPolicy,
        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
  }

  protected void initializeBook(BookAccess bookAccess) {
    Instant initializedAt = Instant.parse("2026-05-19T09:00:00Z");
    try (SqlitePostingFactStore store = SqliteStoreFixtureSupport.openStore(bookAccess)) {
      BookOpeningOutcome outcome =
          store.openAttestedBook(
              initializedAt,
              SqlitePostingFactFixtureSupport.bookIdentity(),
              List.of(),
              SqliteAttestationTestSupport.genesis(
                  SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt));
      if (!(outcome instanceof BookOpeningOutcome.Opened)) {
        throw new AssertionError("Could not create an attested fixture book: " + outcome);
      }
    }
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

  protected static <T> T acceptedValue(MaintenanceDecision<T> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<T>(T value) -> value;
      case MaintenanceDecision.Failed<T>(MaintenanceFailure failure) ->
          throw new AssertionError("Expected accepted maintenance decision but got " + failure);
    };
  }

  protected static MaintenanceFailure failedValue(MaintenanceDecision<?> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<?> accepted ->
          throw new AssertionError("Expected failed maintenance decision but got " + accepted);
      case MaintenanceDecision.Failed<?> failed -> failed.failure();
    };
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

  protected static SqliteStagedRestoredBookPair newStagedRestoredBookPair(
      Path stagedBookPath,
      Path finalBookPath,
      Path stagedBookKeyFilePath,
      Path finalBookKeyFilePath,
      SqliteBookPassphrase restoredPassphrase) {
    return SqliteStagedRestoredBookPairFactory.create(
        new SqliteStagedProtectedBookPairArtifacts(
            SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
            finalBookPath,
            SqliteOwnedStagedArtifact.recordExisting(finalBookKeyFilePath, stagedBookKeyFilePath),
            finalBookKeyFilePath),
        RestoredBookTargetPolicy.REPLACE_SELECTED,
        restoredPassphrase,
        VERIFICATION_SUPPORT);
  }

  protected static void setPrivateField(Object target, String fieldName, @Nullable Object value) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(target.getClass(), MethodHandles.lookup());
      switch (fieldName) {
        case "backupPassphrase", "restoredPassphrase" -> {
          VarHandle field =
              lookup.findVarHandle(target.getClass(), fieldName, SqliteBookPassphrase.class);
          field.set(target, value);
        }
        case "backupFilePublished",
            "backupKeyFilePublished",
            "bookKeyFilePublished",
            "finished" -> {
          VarHandle field = lookup.findVarHandle(target.getClass(), fieldName, boolean.class);
          field.set(
              target,
              ((Boolean) java.util.Objects.requireNonNull(value, fieldName)).booleanValue());
        }
        default ->
            throw new IllegalArgumentException("Unsupported test fixture field: " + fieldName);
      }
    } catch (IllegalAccessException | NoSuchFieldException exception) {
      throw new LinkageError(
          "Failed to set a private fixture field: " + fieldName + ".", exception);
    }
  }
}
