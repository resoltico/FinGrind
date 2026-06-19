package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.CANDIDATE_ACCOUNT_CODE;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.DECLARED_AT;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.candidateAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.cellToken;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.counterAssetAccount;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.directJournalCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.openAccountingPositionCommand;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.priorPostingCommandForReversal;
import static dev.erst.fingrind.testsupport.PostingRouteReachabilityTestSupport.reversalCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingApplicationService;
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
      session.openBook(DECLARED_AT, bookIdentity(), List.of());
      declareReachabilityAccounts(session, cell);
      PostingApplicationService application =
          new PostingApplicationService(
              session, session, oneShotPostingId("posting-" + cellToken(cell)), FIXED_CLOCK);

      PostEntryResult result = application.commit(openAccountingPositionCommand(token));

      if (cell.openingReachable()) {
        assertCommitted(result, "posting-" + cellToken(cell), token);
        return;
      }
      assertEquals(
          new PostEntryResult.CommitRejected(
              PostingRouteReachabilityTestSupport.requestProvenance(token).idempotencyKey(),
              new PostingRejection.OpenAccountingPositionTouchesNominalAccount(
                  CANDIDATE_ACCOUNT_CODE, cell.accountType())),
          result);
    }
  }

  @Override
  public void assertDirectJournalReachability(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    String token = "journal-" + cellToken(cell);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(token, TEST_BOOK_KEY.toCharArray());
        SqlitePostingSession session = SqlitePostingSessions.open(bookPath(token), passphrase)) {
      session.openBook(DECLARED_AT, bookIdentity(), List.of());
      declareReachabilityAccounts(session, cell);
      PostingApplicationService application =
          new PostingApplicationService(
              session, session, oneShotPostingId("posting-" + cellToken(cell)), FIXED_CLOCK);

      PostEntryResult result = application.commit(directJournalCommand(token));

      if (cell.operationalJournalReachable()) {
        assertCommitted(result, "posting-" + cellToken(cell), token);
        return;
      }
      assertEquals(
          new PostEntryResult.CommitRejected(
              PostingRouteReachabilityTestSupport.requestProvenance(token).idempotencyKey(),
              new PostingRejection.ResultHoldingAccountReserved(CANDIDATE_ACCOUNT_CODE)),
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
      session.openBook(DECLARED_AT, bookIdentity(), List.of());
      declareReachabilityAccounts(session, cell);
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
          application.commit(reversalCommand(token, committedSeed.postingId()));

      if (cell.reversalReachable()) {
        assertCommitted(result, reversalPostingIdValue, token);
        return;
      }
      assertEquals(
          new PostEntryResult.CommitRejected(
              PostingRouteReachabilityTestSupport.requestProvenance(token).idempotencyKey(),
              new PostingRejection.ResultHoldingAccountReserved(CANDIDATE_ACCOUNT_CODE)),
          result);
    }
  }

  private static void declareReachabilityAccounts(
      SqlitePostingSession session, RequestSurfaceFacts.ReachabilityCellFacts cell) {
    var counterAccount = counterAssetAccount();
    var candidateAccount = candidateAccount(cell);
    session.declareAccount(
        counterAccount.accountCode(),
        counterAccount.accountName(),
        counterAccount.accountType(),
        counterAccount.accountRole(),
        counterAccount.accountTaxonomy(),
        counterAccount.declaredAt());
    session.declareAccount(
        candidateAccount.accountCode(),
        candidateAccount.accountName(),
        candidateAccount.accountType(),
        candidateAccount.accountRole(),
        candidateAccount.accountTaxonomy(),
        candidateAccount.declaredAt());
  }

  private void assertCommitted(PostEntryResult result, String postingId, String token) {
    assertEquals(
        new PostEntryResult.Committed(
            new PostingId(postingId),
            PostingRouteReachabilityTestSupport.requestProvenance(token).idempotencyKey(),
            PostingRouteReachabilityTestSupport.EFFECTIVE_DATE,
            FIXED_CLOCK.instant()),
        result);
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
