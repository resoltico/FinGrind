package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves pair result paths always project the authoritative retained-publication artifact. */
class PublishedPairResultAuthoritativePathTest extends ContractTestSupport {
  @TempDir Path temporaryDirectory;

  @Test
  void completedPairResults_replaceAcceptedLexicalAliasesWithPublishedArtifactPaths()
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
        pairPublicationRetention(publishedBookPath, publishedKeyPath);
    UUID backupId = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    BackupBookResult.BackedUp backedUp =
        new BackupBookResult.BackedUp(
            temporaryDirectory.resolve("source.sqlite"),
            aliasedBookPath,
            aliasedKeyPath,
            backupId,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention,
            BackupAcknowledgementState.ACKNOWLEDGED,
            attestationCommit());
    BackupBookResult.AcknowledgementPending pending =
        new BackupBookResult.AcknowledgementPending(
            temporaryDirectory.resolve("source.sqlite"),
            aliasedBookPath,
            aliasedKeyPath,
            backupId,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention);
    BackupBookResult.AcknowledgementAuthorizationRejected authorizationRejected =
        new BackupBookResult.AcknowledgementAuthorizationRejected(
            temporaryDirectory.resolve("source.sqlite"),
            aliasedBookPath,
            aliasedKeyPath,
            backupId,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention,
            AttestationVerificationFailure.QUORUM_BELOW);
    RestoreBookResult.Restored restored =
        new RestoreBookResult.Restored(
            aliasedBookPath,
            aliasedKeyPath,
            attestationCommit(),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            retention);

    assertAuthoritativePairPaths(
        backedUp.backupFilePath(), backedUp.backupBookKeyFilePath(), retention);
    assertAuthoritativePairPaths(
        pending.backupFilePath(), pending.backupBookKeyFilePath(), retention);
    assertAuthoritativePairPaths(
        authorizationRejected.backupFilePath(),
        authorizationRejected.backupBookKeyFilePath(),
        retention);
    assertAuthoritativePairPaths(restored.bookFilePath(), restored.bookKeyFilePath(), retention);
  }

  private static void assertAuthoritativePairPaths(
      Path actualBookPath, Path actualKeyPath, ProtectedBookPairPublicationRetention retention) {
    assertEquals(retention.bookPublication().publishedArtifactPath(), actualBookPath);
    assertEquals(retention.generatedSecretPublication().publishedArtifactPath(), actualKeyPath);
  }
}
