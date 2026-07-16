package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct coverage for account extraction across retained posting request shapes. */
class PostingRequestAccountsTest {
  @Test
  void requestedAccounts_extractsAccountsForEveryTypedEntryVariant() {
    List<RequestCase> requestCases =
        List.of(
            new RequestCase(
                "direct-journal",
                callerRequest(
                    new BookkeepingEntry.DirectJournal(
                        journalEntry("1000", "2000", "10.00"), null)),
                Set.of(account("1000"), account("2000"))),
            new RequestCase(
                "sale-settled",
                callerRequest(
                    new BookkeepingEntry.SaleSettled(
                        date(),
                        account("1000"),
                        account("4000"),
                        new MonetaryAmount("EUR", "1000"),
                        new InventoryRelief(
                            account("1400"),
                            account("5000"),
                            new dev.erst.fingrind.contract.bookkeeping.QuantityText("2")),
                        null,
                        null,
                        null,
                        null)),
                Set.of(account("1000"), account("4000"), account("1400"), account("5000"))),
            new RequestCase(
                "sale-on-credit",
                callerRequest(
                    new BookkeepingEntry.SaleOnCredit(
                        date(),
                        account("1200"),
                        account("4000"),
                        new MonetaryAmount("EUR", "1000"),
                        new InventoryRelief(
                            account("1400"),
                            account("5000"),
                            new dev.erst.fingrind.contract.bookkeeping.QuantityText("2")),
                        null,
                        null,
                        null,
                        null)),
                Set.of(account("1200"), account("4000"), account("1400"), account("5000"))),
            new RequestCase(
                "purchase-settled",
                callerRequest(
                    new BookkeepingEntry.PurchaseSettled(
                        date(),
                        account("1400"),
                        account("1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("3"),
                        new MonetaryAmount("EUR", "250"),
                        null,
                        null,
                        null,
                        null)),
                Set.of(account("1400"), account("1000"))),
            new RequestCase(
                "purchase-on-credit",
                callerRequest(
                    new BookkeepingEntry.PurchaseOnCredit(
                        date(),
                        account("1400"),
                        account("2000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("3"),
                        new MonetaryAmount("EUR", "250"),
                        null,
                        null,
                        null,
                        null)),
                Set.of(account("1400"), account("2000"))),
            new RequestCase(
                "expense-settled",
                callerRequest(
                    new BookkeepingEntry.ExpenseSettled(
                        date(),
                        account("5000"),
                        account("1000"),
                        new MonetaryAmount("EUR", "250"),
                        null,
                        null,
                        null)),
                Set.of(account("5000"), account("1000"))),
            new RequestCase(
                "expense-on-credit",
                callerRequest(
                    new BookkeepingEntry.ExpenseOnCredit(
                        date(),
                        account("5000"),
                        account("2000"),
                        new MonetaryAmount("EUR", "250"),
                        null,
                        null,
                        null)),
                Set.of(account("5000"), account("2000"))),
            new RequestCase(
                "receipt",
                callerRequest(
                    new BookkeepingEntry.Receipt(
                        date(),
                        account("1000"),
                        account("1200"),
                        new MonetaryAmount("EUR", "250"),
                        new SettlementAdjunct(account("6090"), new MonetaryAmount("EUR", "5")))),
                Set.of(account("1000"), account("1200"), account("6090"))),
            new RequestCase(
                "payment",
                callerRequest(
                    new BookkeepingEntry.Payment(
                        date(),
                        account("2000"),
                        account("1000"),
                        new MonetaryAmount("EUR", "250"),
                        new SettlementAdjunct(account("7090"), new MonetaryAmount("EUR", "5")))),
                Set.of(account("2000"), account("1000"), account("7090"))),
            new RequestCase(
                "owner-contribution",
                callerRequest(
                    new BookkeepingEntry.OwnerContribution(
                        date(),
                        account("1000"),
                        account("3000"),
                        new MonetaryAmount("EUR", "250"),
                        null)),
                Set.of(account("1000"), account("3000"))),
            new RequestCase(
                "owner-withdrawal",
                callerRequest(
                    new BookkeepingEntry.OwnerWithdrawal(
                        date(),
                        account("3010"),
                        account("1000"),
                        new MonetaryAmount("EUR", "250"),
                        null)),
                Set.of(account("3010"), account("1000"))),
            new RequestCase(
                "opening-position",
                callerRequest(
                    new BookkeepingEntry.OpeningPosition(
                        date(),
                        List.of(
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                account("1000"),
                                JournalLine.EntrySide.DEBIT,
                                new MonetaryAmount("EUR", "250"),
                                null),
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                account("3000"),
                                JournalLine.EntrySide.CREDIT,
                                new MonetaryAmount("EUR", "250"),
                                null)))),
                Set.of(account("1000"), account("3000"))),
            new RequestCase(
                "reversal",
                callerRequest(
                    new BookkeepingEntry.Reversal(
                        date(),
                        new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                            new ReversalReference(new PostingId("posting-1")),
                            new ReversalReason("operator reversal")),
                        null,
                        journalEntry("1000", "4000", "10.00"))),
                Set.of(account("1000"), account("4000"))));

    for (RequestCase requestCase : requestCases) {
      assertEquals(
          requestCase.expectedAccounts(),
          PostingRequestAccounts.requestedAccounts(requestCase.postingRequest()),
          requestCase.name());
    }
  }

  @Test
  void requestedAccounts_prefersResolvedOriginatingEntryOverCallerAuthoredEntry() {
    BookkeepingEntry resolvedEntry =
        new BookkeepingEntry.SaleSettled(
            date(),
            account("1000"),
            account("4000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);
    BookkeepingEntry callerAuthoredEntry =
        new BookkeepingEntry.SaleSettled(
            date(),
            account("1010"),
            account("4010"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);

    PostingCommand request =
        new PostingCommand(
            resolvedEntry.postingKind(),
            resolvedEntry.postingOriginKind(),
            resolvedEntry.journalEntry(),
            PostingLineageModel.direct(),
            evidence(),
            provenance(),
            SourceChannel.CLI,
            callerAuthoredEntry,
            resolvedEntry);

    assertEquals(
        Set.of(account("1000"), account("4000")),
        PostingRequestAccounts.requestedAccounts(request));
  }

  @Test
  void requestedAccounts_preservesCanonicalEncounterOrderForTypedEntries() {
    BookkeepingEntry callerAuthoredEntry =
        new BookkeepingEntry.SaleSettled(
            date(),
            account("1000"),
            account("4000"),
            new MonetaryAmount("EUR", "1000"),
            new InventoryRelief(
                account("1400"),
                account("5000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("2")),
            null,
            null,
            null,
            null);

    assertEquals(
        List.of(account("1000"), account("4000"), account("1400"), account("5000")),
        new ArrayList<>(
            PostingRequestAccounts.requestedAccounts(callerRequest(callerAuthoredEntry))));
  }

  @Test
  void requestedAccounts_usesCallerAuthoredEntryWhenResolvedOriginatingEntryIsAbsent() {
    BookkeepingEntry callerAuthoredEntry =
        new BookkeepingEntry.SaleSettled(
            date(),
            account("1010"),
            account("4010"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);

    PostingCommand request =
        new PostingCommand(
            callerAuthoredEntry.postingKind(),
            callerAuthoredEntry.postingOriginKind(),
            journalEntry("1000", "4000", "10.00"),
            PostingLineageModel.direct(),
            evidence(),
            provenance(),
            SourceChannel.CLI,
            callerAuthoredEntry,
            null);

    assertEquals(
        Set.of(account("1010"), account("4010")),
        PostingRequestAccounts.requestedAccounts(request));
  }

  @Test
  void requestedAccounts_fallsBackToJournalLinesWhenNoEntriesAreRetained() {
    PostingCommand request =
        new PostingCommand(
            dev.erst.fingrind.core.PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.DIRECT_JOURNAL,
            journalEntry("1000", "2000", "10.00"),
            PostingLineageModel.direct(),
            evidence(),
            provenance(),
            SourceChannel.CLI);

    assertEquals(
        Set.of(account("1000"), account("2000")),
        PostingRequestAccounts.requestedAccounts(request));
  }

  @Test
  void requestedAccounts_omitsOptionalAccountsWhenOptionalFactsAreAbsent() {
    assertEquals(
        Set.of(account("1200"), account("4000")),
        PostingRequestAccounts.requestedAccounts(
            callerRequest(
                new BookkeepingEntry.SaleOnCredit(
                    date(),
                    account("1200"),
                    account("4000"),
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    null,
                    null,
                    null))));
    assertEquals(
        Set.of(account("1000"), account("1200")),
        PostingRequestAccounts.requestedAccounts(
            callerRequest(
                new BookkeepingEntry.Receipt(
                    date(),
                    account("1000"),
                    account("1200"),
                    new MonetaryAmount("EUR", "250"),
                    null))));
    assertEquals(
        Set.of(account("2000"), account("1000")),
        PostingRequestAccounts.requestedAccounts(
            callerRequest(
                new BookkeepingEntry.Payment(
                    date(),
                    account("2000"),
                    account("1000"),
                    new MonetaryAmount("EUR", "250"),
                    null))));
    assertEquals(
        Set.of(),
        PostingRequestAccounts.requestedAccounts(
            callerRequest(
                new BookkeepingEntry.Reversal(
                    date(),
                    new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                        new ReversalReference(new PostingId("posting-2")),
                        new ReversalReason("operator reversal")),
                    null,
                    null))));
  }

  @Test
  void fixedAssetRequestAccounts_waitForExecutorResolvedDepreciationFacts() {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    FixedAssetId fixedAssetId = new FixedAssetId("office-desk");

    PostingRequestFixedAssetAccounts.add(
        accounts, new FixedAssetBookkeepingEntryVariants.Depreciation(date(), fixedAssetId, null));
    assertEquals(Set.of(), accounts);

    PostingRequestFixedAssetAccounts.add(
        accounts,
        new FixedAssetBookkeepingEntryVariants.Depreciation(
            date(),
            fixedAssetId,
            new ResolvedFixedAssetDepreciation(
                account("5000"), account("1601"), new MonetaryAmount("EUR", "1000"))));
    assertEquals(Set.of(account("5000"), account("1601")), accounts);
  }

  @Test
  void requestedAccounts_extractsEveryAccrualCutoffLifecycleAccountShape() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("accrual-cutoff-2026-04");
    AccrualCutoffRecognitionInterval interval =
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31"));
    ResolvedAccrualCutoffApplication prepaymentRecognition =
        new ResolvedAccrualCutoffApplication(
            dev.erst.fingrind.core.AccrualCutoffKind.PREPAYMENT,
            dev.erst.fingrind.core.AccrualCutoffApplicationKind.RECOGNITION,
            account("5000"),
            account("1410"));
    ResolvedAccrualCutoffApplication accruedExpenseSettlement =
        new ResolvedAccrualCutoffApplication(
            dev.erst.fingrind.core.AccrualCutoffKind.ACCRUED_EXPENSE,
            dev.erst.fingrind.core.AccrualCutoffApplicationKind.SETTLEMENT,
            account("2100"),
            account("1000"));

    List<RequestCase> requestCases =
        List.of(
            new RequestCase(
                "prepayment",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.Prepayment(
                        date(),
                        cutoffId,
                        account("1410"),
                        account("5000"),
                        account("1000"),
                        new MonetaryAmount("EUR", "1000"),
                        interval)),
                Set.of(account("1410"), account("5000"), account("1000"))),
            new RequestCase(
                "deferred-revenue",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
                        date(),
                        cutoffId,
                        account("1000"),
                        account("2200"),
                        account("4000"),
                        new MonetaryAmount("EUR", "1000"),
                        interval)),
                Set.of(account("1000"), account("2200"), account("4000"))),
            new RequestCase(
                "accrued-expense",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
                        date(),
                        cutoffId,
                        account("5000"),
                        account("2100"),
                        new MonetaryAmount("EUR", "1000"))),
                Set.of(account("5000"), account("2100"))),
            new RequestCase(
                "unresolved-recognition",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                        date(), cutoffId, new MonetaryAmount("EUR", "1000"), null)),
                Set.of()),
            new RequestCase(
                "resolved-recognition",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                        date(),
                        cutoffId,
                        new MonetaryAmount("EUR", "1000"),
                        prepaymentRecognition)),
                Set.of(account("5000"), account("1410"))),
            new RequestCase(
                "unresolved-settlement",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                        date(),
                        cutoffId,
                        account("1000"),
                        new MonetaryAmount("EUR", "1000"),
                        null)),
                Set.of(account("1000"))),
            new RequestCase(
                "resolved-settlement",
                callerRequest(
                    new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                        date(),
                        cutoffId,
                        account("1000"),
                        new MonetaryAmount("EUR", "1000"),
                        accruedExpenseSettlement)),
                Set.of(account("2100"), account("1000"))));

    for (RequestCase requestCase : requestCases) {
      assertEquals(
          requestCase.expectedAccounts(),
          PostingRequestAccounts.requestedAccounts(requestCase.postingRequest()),
          requestCase.name());
    }
  }

  private static PostingCommand callerRequest(BookkeepingEntry entry) {
    return new PostingCommand(
        entry.postingKind(),
        entry.postingOriginKind(),
        retainedJournalEntry(entry),
        switch (entry.postingLineage()) {
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Direct _ ->
              PostingLineageModel.direct();
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal ->
              PostingLineageModel.reversal(reversal.reference(), reversal.reason());
        },
        evidence(),
        provenance(),
        SourceChannel.CLI,
        entry,
        null);
  }

  private static JournalEntry retainedJournalEntry(BookkeepingEntry entry) {
    try {
      return entry.journalEntry();
    } catch (IllegalStateException ignored) {
      return journalEntry("1000", "2000", "10.00");
    }
  }

  private static AccountingEvidence evidence() {
    return dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence(
        "posting-request-accounts");
  }

  private static RequestProvenance provenance() {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.PERSON,
        new CommandId("command-1"),
        new IdempotencyKey("idem-1"),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }

  private static JournalEntry journalEntry(
      String debitAccountCode, String creditAccountCode, String amountText) {
    return new JournalEntry(
        date(),
        List.of(
            new JournalLine(
                account(debitAccountCode),
                JournalLine.EntrySide.DEBIT,
                Money.parse("EUR", amountText)),
            new JournalLine(
                account(creditAccountCode),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", amountText))));
  }

  private static LocalDate date() {
    return LocalDate.parse("2026-04-07");
  }

  private static AccountCode account(String value) {
    return new AccountCode(value);
  }

  private record RequestCase(
      String name, PostingRequestModel postingRequest, Set<AccountCode> expectedAccounts) {}
}
