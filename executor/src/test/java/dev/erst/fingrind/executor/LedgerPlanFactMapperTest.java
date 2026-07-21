package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CashFlowAssetClassification;
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
import java.util.Locale;
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
  void taxRegistrationFacts_preserveAnOptionalRegistrationNumber() {
    DeclaredTaxRegistration registration =
        new DeclaredTaxRegistration(
            new TaxRegistrationId("vat-lv"),
            new TaxRegistrationName("Latvia VAT"),
            new TaxJurisdiction("LV"),
            new TaxRegistrationNumber("LV40001234567"),
            new AccountCode("2100"),
            new AccountCode("1300"),
            TaxObligationFrequency.MONTHLY,
            20,
            List.of(
                new TaxCodeDefinition(
                    new TaxCode("vat-standard-sale"),
                    new TaxCodeName("VAT Standard Sale"),
                    new TaxRate(210_000),
                    TaxInclusionMode.EXCLUSIVE,
                    TaxApplicationKind.OUTPUT_SALE)),
            FIXED_INSTANT);

    List<BookWorkflowFact> facts =
        LedgerPlanFactMapper.taxRegistrationFacts("updated", registration);

    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "registrationNumber".equals(text.name())
                        && "LV40001234567".equals(text.value())));
  }

  @Test
  void postingFacts_includeCallerAuthoredSaleEntryGroup() {
    BookkeepingEntry.SaleSettled sale =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-23"),
            new AccountCode("cash"),
            new AccountCode("service-revenue"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);
    dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting =
        BookkeepingPublishedLanguageTranslator.fromPublished(
            new PostingFact(
                new PostingId("posting-sale-1"),
                sale.journalEntry(),
                sale.postingLineage(),
                sale.postingKind(),
                sale.postingOriginKind(),
                new AccountingEvidence(
                    List.of(
                        new SourceDocumentReference(
                            new SourceDocumentId("document-sale-1"),
                            new SourceDocumentType("cash-receipt"),
                            LocalDate.parse("2026-04-23"))),
                    List.of()),
                new CommittedProvenance(
                    new RequestProvenance(
                        new CommandId("command-sale-1"),
                        new IdempotencyKey("idem-sale-1"),
                        new CausationId("cause-sale-1"),
                        Optional.empty()),
                    FIXED_INSTANT,
                    SourceChannel.CLI),
                sale));

    List<BookWorkflowFact> facts = LedgerPlanFactMapper.postingFacts(posting);

    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "entry".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                nested ->
                                    nested instanceof BookWorkflowFact.Text text
                                        && "entryKind".equals(text.name())
                                        && BookkeepingEntryKind.SALE_SETTLED
                                            .wireValue()
                                            .equals(text.value()))));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "entry".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                nested ->
                                    nested instanceof BookWorkflowFact.Text text
                                        && "revenueAccountCode".equals(text.name())
                                        && "service-revenue".equals(text.value()))));
  }

  @Test
  void postingFacts_includeRemainingCallerAuthoredEntryVariants() {
    assertEntryFacts(
        new BookkeepingEntry.DirectJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-23"),
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
            null),
        facts ->
            assertEntryText(
                facts,
                "entryKind",
                dev.erst.fingrind.core.BookkeepingEntryKind.DIRECT_JOURNAL.wireValue()));
    assertEntryFacts(
        new BookkeepingEntry.ExpenseSettled(
            LocalDate.parse("2026-04-23"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null),
        facts -> assertEntryText(facts, "expenseAccountCode", "5000"));
    assertEntryFacts(
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-23"),
            new AccountCode("1000"),
            new AccountCode("3000"),
            new MonetaryAmount("EUR", "1000"),
            null),
        facts -> assertEntryText(facts, "equityAccountCode", "3000"));
    assertEntryFacts(
        new BookkeepingEntry.OwnerWithdrawal(
            LocalDate.parse("2026-04-23"),
            new AccountCode("3010"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null),
        facts -> assertEntryText(facts, "equityAccountCode", "3010"));
    assertEntryFacts(
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-23"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000"),
                    null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("3000"),
                    JournalLine.EntrySide.CREDIT,
                    new MonetaryAmount("EUR", "1000"),
                    null))),
        facts -> assertEntryGroupContainsText(facts, "openingBalance", "accountCode", "1000"));
    assertEntryFacts(
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-23"),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("prior-posting")),
                new ReversalReason("operator reversal")),
            null,
            null),
        facts -> assertEntryGroupContainsText(facts, "reversal", "reason", "operator reversal"));
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
  void accountDeclarationFacts_includeOutcomeAndParentAccountCodeWhenTaxonomyIsNested() {
    RegisteredAccount account =
        registeredAccount(
            new AccountCode("1110"),
            new AccountName("Operating Cash"),
            AccountType.ASSET,
            new AccountTaxonomy(
                AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("1100")),
                Optional.of(new AccountCode("1105")),
                Optional.of(FinancialPositionLineClassification.INVENTORY),
                Optional.empty(),
                Optional.of(CashFlowAssetClassification.NON_CASH)),
            true,
            FIXED_INSTANT);

    List<BookWorkflowFact> facts = LedgerPlanFactMapper.accountDeclarationFacts("renamed", account);

    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "outcome".equals(text.name())
                        && "renamed".equals(text.value())));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "parentAccountCode".equals(text.name())
                        && "1100".equals(text.value())));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "contraOfAccountCode".equals(text.name())
                        && "1105".equals(text.value())));
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "unitOfMeasure".equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                nested ->
                                    nested instanceof BookWorkflowFact.Text text
                                        && "token".equals(text.name())
                                        && "unit".equals(text.value()))));
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
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        new AccountingEvidence(
            List.of(
                new SourceDocumentReference(
                    new SourceDocumentId("document-idem-1"),
                    new SourceDocumentType("cash-receipt"),
                    LocalDate.parse("2026-04-07"))),
            List.of(
                new ApprovalReference(
                    new ApprovalId("approval-idem-1"),
                    new ApprovalType("manager-signoff"),
                    "approver-1",
                    "person",
                    dev.erst.fingrind.core.ApprovalDecision.APPROVED,
                    Instant.parse("2026-04-07T10:20:30Z")))),
        new CommittedProvenance(
            new RequestProvenance(
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

  private static void assertEntryFacts(
      BookkeepingEntry entry, java.util.function.Consumer<List<BookWorkflowFact>> assertion) {
    BookkeepingEntry persistedEntry = persistedEntry(entry);
    List<BookWorkflowFact> facts =
        LedgerPlanFactMapper.postingFacts(
            BookkeepingPublishedLanguageTranslator.fromPublished(
                new PostingFact(
                    new PostingId(
                        "posting-" + entry.entryKind().wireValue().toLowerCase(Locale.ROOT)),
                    persistedEntry.journalEntry(),
                    persistedEntry.postingLineage(),
                    persistedEntry.postingKind(),
                    persistedEntry.postingOriginKind(),
                    new AccountingEvidence(
                        List.of(
                            new SourceDocumentReference(
                                new SourceDocumentId("document-entry"),
                                new SourceDocumentType("source-document"),
                                persistedEntry.effectiveDate())),
                        List.of()),
                    new CommittedProvenance(
                        new RequestProvenance(
                            new CommandId("command-entry"),
                            new IdempotencyKey("idem-entry"),
                            new CausationId("cause-entry"),
                            Optional.empty()),
                        FIXED_INSTANT,
                        SourceChannel.CLI),
                    persistedEntry)));
    BookWorkflowFact.Group entryGroup =
        facts.stream()
            .filter(
                fact ->
                    fact instanceof BookWorkflowFact.Group group && "entry".equals(group.name()))
            .map(BookWorkflowFact.Group.class::cast)
            .findFirst()
            .orElseThrow();
    assertion.accept(entryGroup.facts());
  }

  private static BookkeepingEntry persistedEntry(BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.Reversal reversal) {
      return reversal.resolvedJournalEntry() == null
          ? new BookkeepingEntry.Reversal(
              reversal.effectiveDate(),
              reversal.reversal(),
              reversal.foreignExchangeDetails(),
              new JournalEntry(
                  reversal.effectiveDate(),
                  List.of(
                      line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                      line("4000", JournalLine.EntrySide.CREDIT, "10.00"))))
          : reversal;
    }
    return entry;
  }

  private static void assertEntryText(
      List<BookWorkflowFact> entryFacts, String fieldName, String expectedValue) {
    assertTrue(
        entryFacts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && fieldName.equals(text.name())
                        && expectedValue.equals(text.value())));
  }

  private static void assertEntryGroupContainsText(
      List<BookWorkflowFact> entryFacts, String groupName, String fieldName, String expectedValue) {
    assertTrue(
        entryFacts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && groupName.equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                nested ->
                                    nested instanceof BookWorkflowFact.Text text
                                        && fieldName.equals(text.name())
                                        && expectedValue.equals(text.value()))));
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
