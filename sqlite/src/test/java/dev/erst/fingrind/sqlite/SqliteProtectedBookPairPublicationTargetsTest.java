package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct physical-identity and conservative absent-leaf admission coverage. */
class SqliteProtectedBookPairPublicationTargetsTest {
  @TempDir Path tempDirectory;

  @Test
  void exactRawLeafEqualityIsAConflictWithoutCreatingAnArtifact() throws Exception {
    Path target = tempDirectory.resolve("exact.sqlite");

    assertTrue(SqlitePairTargetIdentity.sameFinalTargetIdentity(target, target));

    assertFalse(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void existingHardLinkAcrossDistinctParentsIsOnePhysicalTarget() throws Exception {
    Path bookParent = Files.createDirectory(tempDirectory.resolve("book"));
    Path secretParent = Files.createDirectory(tempDirectory.resolve("secret"));
    Path bookTarget = Files.writeString(bookParent.resolve("book.sqlite"), "book");
    Path secretTarget = secretParent.resolve("book.key");
    try {
      Files.createLink(secretTarget, bookTarget);
    } catch (UnsupportedOperationException | FileSystemException unsupported) {
      Assumptions.assumeTrue(
          false, "The test filesystem does not support hard links: " + unsupported.getMessage());
      return;
    }

    assertTrue(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));
  }

  @Test
  void distinctExistingTargetsRemainDistinctRegardlessOfLeafGrammar() throws Exception {
    Path bookTarget = Files.writeString(tempDirectory.resolve("Book.sqlite"), "book");
    Path secretTarget = Files.writeString(tempDirectory.resolve("book.key"), "secret");

    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));
  }

  @Test
  void nonportableAbsentCaseSpellingsAreTypedAndLeaveNoArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("Book.sqlite");
    Path secretTarget = tempDirectory.resolve("book.sqlite");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertEquals(
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED, exception.pathFailure());
    assertEquals(bookTarget, exception.requestedPath());
    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void nonportableAbsentNormalizationSpellingsAreTypedAndLeaveNoArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("caf\u00e9.sqlite");
    Path secretTarget = tempDirectory.resolve(Normalizer.normalize("caf\u00e9.sqlite", Form.NFD));

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertEquals(
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED, exception.pathFailure());
    assertEquals(bookTarget, exception.requestedPath());
    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void nonportableWindowsDeviceLeafIsTypedAndLeavesNoArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("book.sqlite");
    Path secretTarget = tempDirectory.resolve("con.key");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertEquals(
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED, exception.pathFailure());
    assertEquals(secretTarget, exception.requestedPath());
    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void portableDistinctAbsentLeavesAreAdmittedWithoutCreatingAnArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("book.sqlite");
    Path secretTarget = tempDirectory.resolve("book.key");

    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void distinctPhysicalParentsDoNotNeedThePortableSharedParentLeafGrammar() throws Exception {
    Path bookParent = Files.createDirectory(tempDirectory.resolve("book"));
    Path secretParent = Files.createDirectory(tempDirectory.resolve("secret"));
    Path bookTarget = bookParent.resolve("caf\u00e9.sqlite");
    Path secretTarget = secretParent.resolve(Normalizer.normalize("caf\u00e9.key", Form.NFD));

    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertDirectoryEmpty(bookParent);
    assertDirectoryEmpty(secretParent);
  }

  private static void assertDirectoryEmpty(Path directory) throws java.io.IOException {
    try (var children = Files.list(directory)) {
      assertFalse(children.findAny().isPresent(), () -> "Unexpected residue in " + directory);
    }
  }
}
