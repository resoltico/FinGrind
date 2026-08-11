package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link RekeyBookResult}. */
class RekeyBookResultTest extends ContractTestSupport {
  @TempDir Path temporaryDirectory;

  @Test
  void variants_validateNonNullState() {
    RekeyBookResult.Rekeyed rekeyed =
        new RekeyBookResult.Rekeyed(
            Path.of("book.sqlite"),
            Path.of("book.key"),
            attestationCommit(),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            pairPublication(Path.of("book.sqlite"), Path.of("book.key")));
    RekeyBookResult.Rejected rejected =
        new RekeyBookResult.Rejected(
            new BookMaintenanceRejection.SecretTargetOccupied(Path.of("book.new-key")));
    assertEquals(Path.of("book.sqlite").toAbsolutePath().normalize(), rekeyed.bookFilePath());
    assertEquals(Path.of("book.key").toAbsolutePath().normalize(), rekeyed.newBookKeyFilePath());
    assertEquals(
        new BookMaintenanceRejection.SecretTargetOccupied(Path.of("book.new-key")),
        rejected.rejection());
  }

  @Test
  void variants_rejectNullState() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RekeyBookResult.Rekeyed(
                nullOf(),
                Path.of("book.key"),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(Path.of("book.sqlite"), Path.of("book.key"))));
    assertThrows(
        NullPointerException.class,
        () ->
            new RekeyBookResult.Rekeyed(
                Path.of("book.sqlite"),
                nullOf(),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(Path.of("book.sqlite"), Path.of("book.key"))));
    assertThrows(
        NullPointerException.class,
        () ->
            new RekeyBookResult.Rekeyed(
                Path.of("book.sqlite"),
                Path.of("book.key"),
                attestationCommit(),
                nullOf(),
                pairPublication(Path.of("book.sqlite"), Path.of("book.key"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RekeyBookResult.Rekeyed(
                Path.of("book.sqlite"),
                Path.of("book.key"),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                nullOf()));
    assertThrows(NullPointerException.class, () -> new RekeyBookResult.Rejected(nullOf()));
  }

  @Test
  void rekeyed_rejectsAKeyPathThatIsNotThePublishedGeneratedSecret() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RekeyBookResult.Rekeyed(
                Path.of("book.sqlite"),
                Path.of("different.key"),
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                pairPublication(Path.of("book.sqlite"), Path.of("book.key"))));
  }

  @Test
  void rekeyed_exposesTheAuthoritativePublishedPathsInsteadOfAnAcceptedLexicalAlias()
      throws Exception {
    Path artifactDirectory = Files.createDirectory(temporaryDirectory.resolve("artifacts"));
    Path alias = temporaryDirectory.resolve("artifact-alias");
    try {
      Files.createSymbolicLink(alias, artifactDirectory);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      Assumptions.assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
      return;
    }
    Path bookPath = artifactDirectory.resolve("book.sqlite");
    Path keyPath = artifactDirectory.resolve("book.key");
    var publication = pairPublication(bookPath, keyPath);

    RekeyBookResult.Rekeyed rekeyed =
        new RekeyBookResult.Rekeyed(
            alias.resolve("book.sqlite"),
            alias.resolve("book.key"),
            attestationCommit(),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            publication);

    assertEquals(publication.bookPublication().publishedArtifactPath(), rekeyed.bookFilePath());
    assertEquals(
        publication.generatedSecretPublication().publishedArtifactPath(),
        rekeyed.newBookKeyFilePath());
  }
}
