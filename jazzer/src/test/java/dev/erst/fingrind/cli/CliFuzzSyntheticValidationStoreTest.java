package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CliFuzzSyntheticValidationStoreTest {
  @Test
  void validationStore_exposes_default_empty_state_for_non_reversal_entries() {
    var declaredAt = CliFuzzFixtures.fixedClock().instant();
    var store =
        CliFuzzSyntheticValidationStore.validationStore(
            SqliteRoundTripWorkflowTestSupport.basicValidCommand().entry(), declaredAt);

    assertEquals(
        new BookLifecycleInspection.Initialized(
            1001, 1, 1, declaredAt, CliFuzzWorkflowFixtures.bookIdentity()),
        store.inspectBook());
    assertTrue(store.findAccount(new AccountCode("1000")).isEmpty());
    assertEquals(
        Map.of(), store.findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))));
    assertTrue(store.findExistingPosting(new IdempotencyKey("idem-direct")).isEmpty());
    assertTrue(store.findPosting(new PostingId("posting-direct")).isEmpty());
    assertTrue(store.findReversalFor(new PostingId("posting-direct")).isEmpty());
    assertEquals(List.of(), store.postings(EffectiveDateRange.unbounded()));
    assertTrue(store.earliestPostingEffectiveDate().isEmpty());
    assertTrue(store.transferredThroughEffectiveDate().isEmpty());
  }

  @Test
  void validationStore_synthesizes_prior_posting_for_matching_reversal_target() {
    var declaredAt = CliFuzzFixtures.fixedClock().instant();
    var store =
        CliFuzzSyntheticValidationStore.validationStore(
            CliFuzzFixtureCommandSupport.reversalAdjustmentCommand("1000", "2000").entry(),
            declaredAt);

    CommittedPosting priorPosting =
        store.findPosting(new PostingId("posting-admin-test")).orElseThrow();

    assertEquals(new PostingId("posting-admin-test"), priorPosting.postingId());
    assertEquals(LocalDate.parse("2026-04-13"), priorPosting.journalEntry().effectiveDate());
    assertEquals(2, priorPosting.journalEntry().lines().size());
    assertTrue(store.findPosting(new PostingId("posting-other")).isEmpty());
  }
}
