package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Field-tests seeded attested-book opening and its durable duplicate-opening rejection. */
class SqliteAttestedBookOpeningFieldTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void opening_persistsSeededAccountsAndRejectsASecondInitialization() {
    Path bookPath = tempDirectory.resolve("seeded-attested-book.sqlite");
    Instant initializedAt = Instant.parse("2026-07-21T12:00:00Z");
    BookIdentity identity = SqlitePostingFactFixtureSupport.bookIdentity();
    List<AccountDeclaration> seededAccounts =
        List.of(
            new AccountDeclaration(
                new AccountCode("1000"),
                new AccountName("Seeded cash"),
                AccountType.ASSET,
                SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.ASSET)));
    AttestationEvidence genesis = SqliteAttestationTestSupport.genesis(identity, initializedAt);

    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      BookOpeningOutcome.Opened opened =
          assertInstanceOf(
              BookOpeningOutcome.Opened.class,
              store.openAttestedBook(initializedAt, identity, seededAccounts, genesis));
      assertEquals(initializedAt, opened.initializedAt());
      assertEquals(identity, opened.bookIdentity());
      assertEquals(
          seededAccounts.getFirst().accountCode(),
          store.findAccount(seededAccounts.getFirst().accountCode()).orElseThrow().accountCode());

      assertInstanceOf(
          BookOpeningOutcome.Rejected.class,
          store.openAttestedBook(initializedAt, identity, List.of(), genesis));
    }
  }
}
