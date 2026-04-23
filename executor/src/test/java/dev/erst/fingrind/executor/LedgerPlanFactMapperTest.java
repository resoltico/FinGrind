package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for structured ledger-plan fact expansion branches. */
class LedgerPlanFactMapperTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-23T10:15:30Z");

  @Test
  void postingPageFacts_includeNextCursorAndStructuredReversalFacts() {
    PostingFact postingFact = reversalPostingFact();
    PostingPage page =
        new PostingPage(
            List.of(postingFact), 25, Optional.of(PostingPageCursor.fromPosting(postingFact)));

    List<LedgerFact> facts = LedgerPlanFactMapper.postingPageFacts(page);

    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof LedgerFact.Text text
                        && "nextCursor".equals(text.name())
                        && PostingPageCursor.fromPosting(postingFact)
                            .wireValue()
                            .equals(text.value())));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof LedgerFact.Group group
                        && "posting".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                child ->
                                    child instanceof LedgerFact.Group reversal
                                        && "reversal".equals(reversal.name())
                                        && reversal.facts().stream()
                                            .anyMatch(
                                                nested ->
                                                    nested instanceof LedgerFact.Text text
                                                        && "reason".equals(text.name())
                                                        && "operator reversal"
                                                            .equals(text.value())))));
  }

  @Test
  void balanceFacts_includeOptionalDateBoundsWhenPresent() {
    DeclaredAccount account =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            FIXED_INSTANT);
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            account,
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new CurrencyBalance(
                    money("10.00"), money("0.00"), money("10.00"), BalanceSide.DEBIT)));

    List<LedgerFact> facts = LedgerPlanFactMapper.balanceFacts(snapshot);

    assertEquals(
        1,
        facts.stream()
            .filter(
                fact ->
                    fact instanceof LedgerFact.Text text
                        && "effectiveDateFrom".equals(text.name())
                        && "2026-04-01".equals(text.value()))
            .count());
    assertEquals(
        1,
        facts.stream()
            .filter(
                fact ->
                    fact instanceof LedgerFact.Text text
                        && "effectiveDateTo".equals(text.name())
                        && "2026-04-30".equals(text.value()))
            .count());
  }

  private static PostingFact reversalPostingFact() {
    return new PostingFact(
        new PostingId("posting-1"),
        new JournalEntry(
            LocalDate.parse("2026-04-23"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineage.reversal(
            new ReversalReference(new PostingId("prior-posting")),
            new ReversalReason("operator reversal")),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  private static Money money(String amount) {
    return new Money(new CurrencyCode("EUR"), new BigDecimal(amount));
  }
}
