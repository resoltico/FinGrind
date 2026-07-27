package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves local pair outcomes retain the authoritative paths recorded by publication evidence. */
class ProtectedBookPairOutcomeAuthoritativePathTest {
  private static final AttestationCommit COMMIT =
      new AttestationCommit(BigInteger.ONE, "a".repeat(64));

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void completedPairOutcomes_replaceAcceptedLexicalAliasesWithPublishedArtifactPaths()
      throws Exception {
    Path artifactDirectory = Files.createDirectory(temporaryDirectory.resolve("artifacts"));
    Path alias = temporaryDirectory.resolve("artifact-alias");
    try {
      Files.createSymbolicLink(alias, artifactDirectory);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      Assumptions.assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
      return;
    }
    Path publishedBookPath = artifactDirectory.resolve("book.sqlite");
    Path publishedKeyPath = artifactDirectory.resolve("book.key");
    Path aliasedBookPath = alias.resolve("book.sqlite");
    Path aliasedKeyPath = alias.resolve("book.key");
    ProtectedBookPairPublicationRetention retention =
        retention(publishedBookPath, publishedKeyPath);

    ProtectedBookBackupOutcome.BackedUp backedUp =
        new ProtectedBookBackupOutcome.BackedUp(
            temporaryDirectory.resolve("source.sqlite"),
            aliasedBookPath,
            aliasedKeyPath,
            UUID.fromString("018f0000-0000-7000-8000-000000000001"),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention,
            BackupAcknowledgementState.ACKNOWLEDGED,
            COMMIT);
    ProtectedBookBackupOutcome.AcknowledgementPending pending =
        new ProtectedBookBackupOutcome.AcknowledgementPending(
            temporaryDirectory.resolve("source.sqlite"),
            aliasedBookPath,
            aliasedKeyPath,
            UUID.fromString("018f0000-0000-7000-8000-000000000001"),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention);
    ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected authorizationRejected =
        new ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected(
            temporaryDirectory.resolve("source.sqlite"),
            aliasedBookPath,
            aliasedKeyPath,
            UUID.fromString("018f0000-0000-7000-8000-000000000001"),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention,
            AttestationAuthorizationFailure.QUORUM_BELOW);
    ProtectedBookRestoreOutcome.Restored restored =
        new ProtectedBookRestoreOutcome.Restored(
            aliasedBookPath,
            aliasedKeyPath,
            COMMIT,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention);
    ProtectedBookRekeyOutcome.Rekeyed rekeyed =
        new ProtectedBookRekeyOutcome.Rekeyed(
            aliasedBookPath,
            aliasedKeyPath,
            COMMIT,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention);

    assertAuthoritativePairPaths(
        backedUp.backupFilePath(),
        backedUp.backupBookKeyFilePath(),
        publishedBookPath,
        publishedKeyPath);
    assertAuthoritativePairPaths(
        pending.backupFilePath(),
        pending.backupBookKeyFilePath(),
        publishedBookPath,
        publishedKeyPath);
    assertAuthoritativePairPaths(
        authorizationRejected.backupFilePath(),
        authorizationRejected.backupBookKeyFilePath(),
        publishedBookPath,
        publishedKeyPath);
    assertAuthoritativePairPaths(
        restored.bookFilePath(), restored.bookKeyFilePath(), publishedBookPath, publishedKeyPath);
    assertAuthoritativePairPaths(
        rekeyed.bookFilePath(), rekeyed.newBookKeyFilePath(), publishedBookPath, publishedKeyPath);
  }

  private static ProtectedBookPairPublicationRetention retention(
      Path publishedBookPath, Path publishedKeyPath) {
    return new ProtectedBookPairPublicationRetention(
        new ArtifactPublicationResult(
            publishedBookPath,
            new ArtifactPublicationRetention(
                publishedBookPath.resolveSibling(".retained-book.stage"))),
        new ArtifactPublicationResult(
            publishedKeyPath,
            new ArtifactPublicationRetention(
                publishedKeyPath.resolveSibling(".retained-secret.stage"))));
  }

  private static void assertAuthoritativePairPaths(
      Path actualBookPath, Path actualKeyPath, Path publishedBookPath, Path publishedKeyPath) {
    assertEquals(publishedBookPath.toAbsolutePath().normalize(), actualBookPath);
    assertEquals(publishedKeyPath.toAbsolutePath().normalize(), actualKeyPath);
  }
}
