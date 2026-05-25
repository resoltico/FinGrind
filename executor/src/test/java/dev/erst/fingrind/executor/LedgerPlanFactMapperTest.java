package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.workflow.BookWorkflowFact;
import dev.erst.fingrind.executor.workflow.LedgerPlanFactMapper;
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
    dev.erst.fingrind.executor.bookkeeping.CommittedPosting reversalPosting =
        BookkeepingPublishedLanguageTranslator.fromPublished(reversalPostingFact());
    PostingHistoryPage page =
        new PostingHistoryPage(
            List.of(reversalPosting),
            25,
            Optional.of(
                new PostingHistoryCursor(
                    reversalPosting.journalEntry().effectiveDate(),
                    reversalPosting.provenance().recordedAt(),
                    reversalPosting.postingId())));

    List<BookWorkflowFact> facts = LedgerPlanFactMapper.postingPageFacts(page);

    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "nextCursor".equals(text.name())
                        && page.nextCursor().orElseThrow().wireValue().equals(text.value())));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "posting".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                child ->
                                    child instanceof BookWorkflowFact.Group evidence
                                        && "evidence".equals(evidence.name())
                                        && evidence.facts().stream()
                                            .anyMatch(
                                                nested ->
                                                    nested
                                                            instanceof
                                                            BookWorkflowFact.Group approval
                                                        && "approval".equals(approval.name())
                                                        && approval.facts().stream()
                                                            .anyMatch(
                                                                field ->
                                                                    field
                                                                            instanceof
                                                                            BookWorkflowFact.Text
                                                                                text
                                                                        && "approvalType"
                                                                            .equals(text.name())
                                                                        && "manager-signoff"
                                                                            .equals(
                                                                                text.value()))))));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "posting".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                child ->
                                    child instanceof BookWorkflowFact.Group reversal
                                        && "reversal".equals(reversal.name())
                                        && reversal.facts().stream()
                                            .anyMatch(
                                                nested ->
                                                    nested instanceof BookWorkflowFact.Text text
                                                        && "reason".equals(text.name())
                                                        && "operator reversal"
                                                            .equals(text.value())))));
  }

  @Test
  void balanceFacts_includeOptionalDateBoundsWhenPresent() {
    RegisteredAccount account =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            FIXED_INSTANT);
    AccountBalanceView snapshot =
        new AccountBalanceView(
            account,
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)));

    List<BookWorkflowFact> facts = LedgerPlanFactMapper.balanceFacts(snapshot);

    assertEquals(
        1,
        facts.stream()
            .filter(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "effectiveDateFrom".equals(text.name())
                        && "2026-04-01".equals(text.value()))
            .count());
    assertEquals(
        1,
        facts.stream()
            .filter(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "effectiveDateTo".equals(text.name())
                        && "2026-04-30".equals(text.value()))
            .count());
    assertEquals(
        1,
        facts.stream()
            .filter(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "account".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                child ->
                                    child instanceof BookWorkflowFact.Text text
                                        && "accountType".equals(text.name())
                                        && "ASSET".equals(text.value())))
            .count());
  }

  @Test
  void declaredAccountFacts_includeParentAccountCodeWhenTaxonomyIsNested() {
    RegisteredAccount account =
        registeredAccount(
            new AccountCode("1110"),
            new AccountName("Operating Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            new AccountTaxonomy(
                AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("1100")),
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.empty()),
            true,
            FIXED_INSTANT);

    List<BookWorkflowFact> facts = LedgerPlanFactMapper.declaredAccountFacts(account);

    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "parentAccountCode".equals(text.name())
                        && "1100".equals(text.value())));
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
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
        new AccountingEvidence(
            List.of(
                new SourceDocumentReference(
                    new SourceDocumentId("document-idem-1"),
                    new SourceDocumentType("cash-receipt"),
                    LocalDate.parse("2026-04-07"),
                    Instant.parse("2026-04-07T10:15:30Z"),
                    new dev.erst.fingrind.core.StorageLocator("vault://fixtures/document-idem-1"),
                    new dev.erst.fingrind.core.ContentSha256(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))),
            List.of(
                new ApprovalReference(
                    new ApprovalId("approval-idem-1"),
                    new ApprovalType("manager-signoff"),
                    new ActorId("approver-1"),
                    ActorType.PERSON,
                    dev.erst.fingrind.core.ApprovalDecision.APPROVED,
                    Instant.parse("2026-04-07T10:20:30Z")))),
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
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  private static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(Money.parse("EUR", debitAmount), Money.parse("EUR", creditAmount));
    if (!balance.netAmount().equals(Money.parse("EUR", netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }
}
