package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.PrivateOutputFile;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Proves exact live-page and manifest-attested backup physical-envelope admission. */
class SqliteProtectedBookPhysicalEnvelopeTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void authenticatedLiveBookHasExactlyItsSQLitePageEnvelope() throws Exception {
    BookAccess access = initializedBookAccess("live-envelope.sqlite");

    try (SqliteNativeDatabase database = openNativeDatabase(access)) {
      assertEquals(
          SqliteProtectedBookPhysicalEnvelope.EnvelopeKind.LIVE_BOOK,
          SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(
              database, access.bookFilePath()));
      SqliteOpenedDatabaseVerification.requireAdmissible(
          database, access.bookFilePath(), SqliteStoreAccessMode.READ_ONLY);
    }
  }

  @Test
  void longerFilesRequireACompleteManifestAttestedBackupArtifact() throws Exception {
    BookAccess sourceAccess = initializedBookAccess("manifest-source.sqlite");
    Path artifactPath = signedBackupArtifact(sourceAccess, "manifest-backup.fgba");

    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess(artifactPath))) {
      assertEquals(
          SqliteProtectedBookPhysicalEnvelope.EnvelopeKind.MANIFEST_ATTESTED_BACKUP_ARTIFACT,
          SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(database, artifactPath));
      SqliteOpenedDatabaseVerification.requireAdmissible(
          database, artifactPath, SqliteStoreAccessMode.READ_ONLY);
    }
  }

  @Test
  void mutableAccessToAManifestAttestedBackupIsRejectedAndTheDatabaseIsClosed() throws Exception {
    BookAccess sourceAccess = initializedBookAccess("immutable-backup-source.sqlite");
    Path artifactPath = signedBackupArtifact(sourceAccess, "immutable-backup.fgba");

    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess(artifactPath))) {
      assertThrows(
          SqliteProtectedBookVerificationException.class,
          () ->
              SqliteOpenedDatabaseVerification.requireAdmissible(
                  database, artifactPath, SqliteStoreAccessMode.READ_WRITE_EXISTING));
      assertThrows(IllegalStateException.class, () -> database.prepare("pragma page_size"));
    }
  }

  @Test
  void truncatedAndAppendedLiveBooksAreRejectedWithoutTreatingTrailingBytesAsPages()
      throws Exception {
    BookAccess access = initializedBookAccess("tampered-envelope.sqlite");
    Path truncatedPath = tempDirectory.resolve("truncated-envelope.sqlite");
    Files.write(truncatedPath, new byte[0]);

    try (SqliteNativeDatabase database = openNativeDatabase(access)) {
      assertThrows(
          SqliteProtectedBookVerificationException.class,
          () ->
              SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(
                  database, truncatedPath));

      Files.writeString(
          access.bookFilePath(), "unexpected trailing bytes", StandardOpenOption.APPEND);
      assertThrows(
          SqliteProtectedBookVerificationException.class,
          () ->
              SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(
                  database, access.bookFilePath()));
    }
  }

  @Test
  void signedArtifactWithTheWrongSnapshotLengthIsRejectedBeforeEvidenceCanAuthorizeIt()
      throws Exception {
    BookAccess sourceAccess = initializedBookAccess("wrong-snapshot-source.sqlite");
    byte[] sourceSnapshot = Files.readAllBytes(sourceAccess.bookFilePath());
    byte[] wrongSnapshot = Arrays.copyOf(sourceSnapshot, sourceSnapshot.length + 1);
    Path wrongSnapshotArtifact = signedArtifact(sourceAccess, "wrong-snapshot.fgba", wrongSnapshot);

    try (SqliteNativeDatabase database = openNativeDatabase(sourceAccess)) {
      assertThrows(
          SqliteProtectedBookVerificationException.class,
          () ->
              SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(
                  database, wrongSnapshotArtifact));
    }
  }

  @Test
  void malformedDimensionsAndVerificationFailuresCloseTheOpenedDatabaseWithoutReplacingTheCause()
      throws Exception {
    BookAccess access = initializedBookAccess("dimension-envelope.sqlite");
    Path missingPath = tempDirectory.resolve("missing-envelope.sqlite");

    try (SqliteNativeDatabase database = openNativeDatabase(access)) {
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(
                  replacingPragma(database, "pragma page_size", "select '0'"),
                  access.bookFilePath()));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteProtectedBookPhysicalEnvelope.requireAuthenticatedEnvelope(
                  replacingPragma(database, "pragma page_count", "select '-1'"),
                  access.bookFilePath()));

      try (CloseRecordingDatabase closingDatabase = new CloseRecordingDatabase(database, null)) {
        assertThrows(
            SqliteProtectedBookVerificationException.class,
            () ->
                SqliteOpenedDatabaseVerification.requireAdmissible(
                    closingDatabase, missingPath, SqliteStoreAccessMode.READ_ONLY));
        assertTrue(closingDatabase.closeRequested());
      }

      IllegalStateException closeFailure = new IllegalStateException("close failure");
      SqliteProtectedBookVerificationException primaryFailure;
      try (CloseRecordingDatabase failingCloseDatabase =
          new CloseRecordingDatabase(database, closeFailure)) {
        primaryFailure =
            assertThrows(
                SqliteProtectedBookVerificationException.class,
                () ->
                    SqliteOpenedDatabaseVerification.requireAdmissible(
                        failingCloseDatabase, missingPath, SqliteStoreAccessMode.READ_ONLY));
        assertTrue(failingCloseDatabase.closeRequested());
      }
      assertEquals(1, primaryFailure.getSuppressed().length);
      assertSame(closeFailure, primaryFailure.getSuppressed()[0]);
    }
  }

  private BookAccess initializedBookAccess(String fileName) {
    BookAccess access = bookAccess(tempDirectory.resolve(fileName));
    initializeBook(access);
    return access;
  }

  private Path signedBackupArtifact(BookAccess sourceAccess, String artifactName)
      throws IOException {
    return signedArtifact(
        sourceAccess, artifactName, Files.readAllBytes(sourceAccess.bookFilePath()));
  }

  private Path signedArtifact(BookAccess sourceAccess, String artifactName, byte[] snapshot)
      throws IOException {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    AttestationVerification verification;
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, sourceAccess)) {
      verification = AttestationVerifier.verifyBook(store.loadAttestationEvidence(verifiedBook));
    }
    Path artifactPath = tempDirectory.resolve(artifactName);
    writeOwnerOnlyBytes(
        artifactPath,
        SqliteAttestationTestSupport.signedBackupArtifact(
            snapshot, verification, UUID.fromString("5506b3a9-5f27-41c6-a2cd-7b587aab974c")));
    return artifactPath;
  }

  private static void writeOwnerOnlyBytes(Path path, byte[] bytes) throws IOException {
    try (PrivateOutputFile.OpenedFile output = PrivateOutputFile.createNew(path)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        if (output.write(buffer) <= 0) {
          throw new IOException("Could not write the complete test backup artifact.");
        }
      }
      output.force();
    }
  }

  private static SqliteNativeDatabase replacingPragma(
      SqliteNativeDatabase delegate, String sql, String replacementSql) {
    return new SqliteNativeDatabase(MemorySegment.NULL) {
      @Override
      SqliteNativeStatement prepare(String candidateSql) {
        return delegate.prepare(candidateSql.equals(sql) ? replacementSql : candidateSql);
      }

      @Override
      public void close() {}
    };
  }

  /** Delegates SQL probes while recording one verification-boundary close attempt. */
  private static final class CloseRecordingDatabase extends SqliteNativeDatabase {
    private final SqliteNativeDatabase delegate;
    private final @Nullable RuntimeException closeFailure;
    private boolean closeRequested;

    private CloseRecordingDatabase(
        SqliteNativeDatabase delegate, @Nullable RuntimeException closeFailure) {
      super(MemorySegment.NULL);
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.closeFailure = closeFailure;
    }

    @Override
    SqliteNativeStatement prepare(String sql) {
      return delegate.prepare(sql);
    }

    @Override
    public void close() {
      if (closeRequested) {
        return;
      }
      closeRequested = true;
      if (closeFailure != null) {
        throw closeFailure;
      }
    }

    private boolean closeRequested() {
      return closeRequested;
    }
  }
}
