package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.testsupport.PostingRouteReachabilityProvenanceFixtures.requestProvenance;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.directJournalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.openingPositionCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.priorPostingCommandForReversal;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityScenarioFactory.reversalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.DECLARED_AT;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.candidateAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.cellToken;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.counterAuxiliaryAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.isInventoryCell;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.payableAuxiliaryAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.testsupport.PostingRouteReachabilityContract;
import dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that published reachability survives the SQLite-backed durable commit boundary. */
class SqlitePostingRouteReachabilityCommitContractTest extends SqlitePostingFactStoreTestSupport
    implements PostingRouteReachabilityContract {
  @Test
  void openingPositionReachabilityMatchesThePublishedMatrix() {
    verifyOpeningPositionReachabilityMatrix();
  }

  @Test
  void directJournalReachabilityMatchesThePublishedMatrix() {
    verifyDirectJournalReachabilityMatrix();
  }

  @Test
  void reversalReachabilityMatchesThePublishedMatrix() {
    verifyReversalReachabilityMatrix();
  }

  @Override
  public void assertOpeningPositionReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    String token = "opening-" + cellToken(cell);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(token, TEST_BOOK_KEY.toCharArray());
        SqlitePostingSession session = SqlitePostingSessions.open(bookPath(token), passphrase)) {
      session.openBook(
          DECLARED_AT,
          dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.bookIdentity(cell),
          reachabilitySeedAccounts(cell));
      PostingApplicationService application =
          new PostingApplicationService(
              session, session, oneShotPostingId("posting-" + cellToken(cell)), FIXED_CLOCK);

      PostEntryResult result = application.commit(openingPositionCommand(cell, token));

      if (cell.openingReachable()) {
        assertCommitted(result, "posting-" + cellToken(cell), token);
        return;
      }
      PostEntryResult.CommitRejected rejected =
          assertInstanceOf(PostEntryResult.CommitRejected.class, result);
      PostingRejection.EntrySemanticsViolations violations =
          assertInstanceOf(PostingRejection.EntrySemanticsViolations.class, rejected.rejection());
      assertEquals(requestProvenance(token).idempotencyKey(), rejected.requestIdempotencyKey());
      assertEquals(1, violations.violations().size());
      assertEquals(
          "opening-window-account-not-permitted", violations.violations().getFirst().code());
      assertEquals(
          "entryKind '"
              + BookkeepingEntryKind.OPENING_POSITION.wireValue()
              + "' uses openingBalances[].accountCode '"
              + CANDIDATE_ACCOUNT_CODE.value()
              + "', which is not permitted in the adoption opening window.",
          violations.violations().getFirst().message());
    }
  }

  @Override
  public void assertDirectJournalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    String token = "journal-" + cellToken(cell);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(token, TEST_BOOK_KEY.toCharArray());
        SqlitePostingSession session = SqlitePostingSessions.open(bookPath(token), passphrase)) {
      session.openBook(
          DECLARED_AT,
          dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.bookIdentity(cell),
          reachabilitySeedAccounts(cell));
      PostingApplicationService application =
          new PostingApplicationService(
              session, session, oneShotPostingId("posting-" + cellToken(cell)), FIXED_CLOCK);

      PostEntryResult result = application.commit(directJournalCommand(cell, token));

      if (cell.operationalJournalReachable()) {
        assertCommitted(result, "posting-" + cellToken(cell), token);
        return;
      }
      if (isInventoryCell(cell)) {
        PostEntryResult.CommitRejected rejected =
            assertInstanceOf(PostEntryResult.CommitRejected.class, result);
        PostingRejection.EntrySemanticsViolations violations =
            assertInstanceOf(PostingRejection.EntrySemanticsViolations.class, rejected.rejection());
        assertEquals(1, violations.violations().size());
        assertEquals("raw-journal-touches-inventory", violations.violations().getFirst().code());
        assertEquals(
            "entryKind '"
                + BookkeepingEntryKind.DIRECT_JOURNAL.wireValue()
                + "' contains lines[].accountCode '"
                + CANDIDATE_ACCOUNT_CODE.value()
                + "', which resolves to the inventory role. Raw direct-journal requests cannot create or change exact inventory quantity.",
            violations.violations().getFirst().message());
        return;
      }
      assertEquals(
          new PostEntryResult.CommitRejected(
              requestProvenance(token).idempotencyKey(),
              new PostingRejection.ReservedResultClassification(
                  CANDIDATE_ACCOUNT_CODE,
                  FinancialPositionLineClassification.fromWireValue(cell.classification()))),
          result);
    }
  }

  @Override
  public void assertReversalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    String token = "reversal-" + cellToken(cell);
    String priorToken = "prior-" + cellToken(cell);
    String priorPostingIdValue = "posting-" + cellToken(cell) + "-prior";
    String reversalPostingIdValue = "posting-" + cellToken(cell) + "-reversal";
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(token, TEST_BOOK_KEY.toCharArray());
        SqlitePostingSession session = SqlitePostingSessions.open(bookPath(token), passphrase)) {
      session.openBook(
          DECLARED_AT,
          dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.bookIdentity(cell),
          reachabilitySeedAccounts(cell));
      PostingApplicationService application =
          new PostingApplicationService(
              session,
              session,
              postingIds(priorPostingIdValue, reversalPostingIdValue),
              FIXED_CLOCK);

      PostEntryResult seedResult =
          application.commit(priorPostingCommandForReversal(cell, priorToken));
      PostEntryResult.Committed committedSeed =
          assertInstanceOf(
              PostEntryResult.Committed.class, seedResult, "seed commit must succeed for " + cell);

      PostEntryResult result =
          application.commit(reversalCommand(cell, token, committedSeed.postingId()));

      if (cell.reversalReachable()) {
        assertCommitted(result, reversalPostingIdValue, token);
        return;
      }
      assertEquals(
          new PostEntryResult.CommitRejected(
              requestProvenance(token).idempotencyKey(),
              new PostingRejection.ReservedResultClassification(
                  CANDIDATE_ACCOUNT_CODE,
                  FinancialPositionLineClassification.fromWireValue(cell.classification()))),
          result);
    }
  }

  private static List<AccountDeclaration> reachabilitySeedAccounts(
      RequestSurfaceFacts.ReachabilityCellFacts cell) {
    var counterAccount = counterAuxiliaryAccount();
    var candidateAccount = candidateAccount(cell);
    if (isInventoryCell(cell)) {
      return List.of(
          accountDeclaration(counterAccount),
          accountDeclaration(candidateAccount),
          accountDeclaration(payableAuxiliaryAccount()));
    }
    return List.of(accountDeclaration(counterAccount), accountDeclaration(candidateAccount));
  }

  private static AccountDeclaration accountDeclaration(
      dev.erst.fingrind.executor.bookkeeping.RegisteredAccount account) {
    return new AccountDeclaration(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountTaxonomy(),
        account.unitOfMeasure());
  }

  private void assertCommitted(PostEntryResult result, String postingId, String token) {
    PostEntryResult.Committed committed = assertInstanceOf(PostEntryResult.Committed.class, result);
    assertEquals(new PostingId(postingId), committed.postingId());
    assertEquals(requestProvenance(token).idempotencyKey(), committed.idempotencyKey());
    assertEquals(PostingRouteReachabilityTestSupport.EFFECTIVE_DATE, committed.effectiveDate());
    assertEquals(FIXED_CLOCK.instant(), committed.recordedAt());
    assertFalse(committed.idempotentReplay());
  }

  private Path bookPath(String token) {
    return tempDirectory.resolve(token + ".sqlite");
  }

  private static PostingIdGenerator oneShotPostingId(String postingId) {
    return () -> new PostingId(postingId);
  }

  private static PostingIdGenerator postingIds(String... postingIds) {
    Deque<String> queue = new ArrayDeque<>(List.of(postingIds));
    return () -> new PostingId(queue.removeFirst());
  }
}
