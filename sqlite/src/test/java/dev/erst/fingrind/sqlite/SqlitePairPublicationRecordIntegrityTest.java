package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies immutable protected-book pair member integrity before publication may proceed. */
class SqlitePairPublicationRecordIntegrityTest {
  @TempDir Path tempDirectory;

  @Test
  void digestsRegularFilesAndTranslatesAStalledDigestInputToItsArtifactContext() throws Exception {
    Path artifact = Files.writeString(tempDirectory.resolve("artifact.sqlite"), "protected book");

    byte[] digest = SqlitePairPublicationRecordIntegrity.digestRegularFile(artifact, "artifact");

    assertTrue(SqlitePairPublicationRecordIntegrity.regularFileMatches(artifact, digest));
    assertFalse(
        SqlitePairPublicationRecordIntegrity.regularFileMatches(artifact, new byte[digest.length]));
    assertFalse(
        SqlitePairPublicationRecordIntegrity.regularFileMatches(
            tempDirectory.resolve("missing.sqlite"), digest));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.digestRegularFile(
                tempDirectory.resolve("missing.sqlite"), "missing artifact"));

    IOException stalled =
        assertThrows(
            IOException.class,
            () ->
                SqlitePairPublicationRecordIntegrity.digest(
                    new NoProgressInputStream(), "artifact"));
    assertEquals("The artifact did not make read progress.", stalled.getMessage());
    IOException propagated =
        assertThrows(
            IOException.class,
            () ->
                SqlitePairPublicationRecordIntegrity.digest(new FailingInputStream(), "artifact"));
    assertEquals("injected input failure", propagated.getMessage());
  }

  @Test
  void acceptsOnlyDefensiveSha256DigestsAndAbsentOptionalReplacementBytes() {
    byte[] supplied = new byte[32];
    byte[] checked = SqlitePairPublicationRecordIntegrity.checkedDigest(supplied, "bookDigest");
    supplied[0] = 1;

    assertArrayEquals(new byte[32], checked);
    assertThrows(
        IllegalArgumentException.class,
        () -> SqlitePairPublicationRecordIntegrity.checkedDigest(new byte[31], "bookDigest"));
    assertDoesNotThrow(() -> SqlitePairPublicationRecordIntegrity.requireAbsent(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqlitePairPublicationRecordIntegrity.requireAbsent(new byte[32]));
  }

  @Test
  void rejectsEveryMemberIdentityCollisionAndStagesOutsideTheirFinalParents() throws Exception {
    Path book = tempDirectory.resolve("book.sqlite");
    Path secret = tempDirectory.resolve("book.key");
    Path bookStage = tempDirectory.resolve(".book.stage");
    Path secretStage = tempDirectory.resolve(".book-key.stage");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, book, bookStage, secretStage));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, book, secretStage));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, secret, secretStage));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, bookStage, book));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, bookStage, secret));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, bookStage, bookStage));

    Path otherParent = Files.createDirectories(tempDirectory.resolve("other-parent"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, otherParent.resolve(".book.stage"), secretStage));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
                book, secret, bookStage, otherParent.resolve(".book-key.stage")));
  }

  /** Input stream that violates the read-progress contract to exercise the guarded digest loop. */
  private static final class NoProgressInputStream extends InputStream {
    @Override
    public int read() {
      return 0;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) {
      return 0;
    }
  }

  /** Input stream that consistently exposes the injected I/O failure unchanged. */
  private static final class FailingInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      throw new IOException("injected input failure");
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      throw new IOException("injected input failure");
    }
  }
}
