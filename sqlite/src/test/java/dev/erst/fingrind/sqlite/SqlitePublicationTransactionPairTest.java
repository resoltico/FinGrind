package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves a protected-book pair reserves both private transaction stages before production. */
class SqlitePublicationTransactionPairTest {
  @Test
  void plansOneNoReplacePairForAnAbsentBookTarget() {
    PublicationTransactionRequest request =
        SqlitePublicationTransactionPair.requestFor(
            Path.of("private", "backup.sqlite"),
            Path.of("private", "backup.key"),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            ownerContext("backup"));

    PublicationTransactionOwnerContext context = ownerContext("backup");
    assertMembers(request.members(), PublicationMode.NO_REPLACE_LINK);
    assertEquals(Optional.of(context), request.ownerContext());
  }

  @Test
  void plansOneReplacementBookAndNoReplaceGeneratedSecret() {
    PublicationTransactionRequest request =
        SqlitePublicationTransactionPair.requestFor(
            Path.of("private", "live.sqlite"),
            Path.of("private", "live.key"),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            ownerContext("rekey"));

    PublicationTransactionOwnerContext context = ownerContext("rekey");
    assertMembers(request.members(), PublicationMode.REPLACE);
    assertEquals(Optional.of(context), request.ownerContext());
  }

  @Test
  void rejectsAPathThatNamesOnlyItsFilesystemRoot() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePublicationTransactionPair.requestFor(
                Path.of("/"),
                Path.of("private", "backup.key"),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                ownerContext("invalid")));
  }

  private static void assertMembers(
      List<PublicationTransactionMemberRequest> members, PublicationMode expectedBookMode) {
    assertEquals(2, members.size());
    assertEquals(SqlitePublicationTransactionPair.BOOK_MEMBER_ID, members.getFirst().memberId());
    assertEquals(PublicationTransactionMemberRole.PROTECTED_BOOK, members.getFirst().role());
    assertEquals(expectedBookMode, members.getFirst().publicationMode());
    assertEquals(SqlitePublicationTransactionPair.SECRET_MEMBER_ID, members.get(1).memberId());
    assertEquals(PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY, members.get(1).role());
    assertEquals(PublicationMode.NO_REPLACE_LINK, members.get(1).publicationMode());
  }

  private static PublicationTransactionOwnerContext ownerContext(String description) {
    return PublicationTransactionOwnerContext.fromCanonicalDescription(description);
  }
}
