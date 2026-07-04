package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct coverage for semantic fingerprint derivation over canonical posting models. */
class RequestFingerprintOwnerTest {
  @Test
  void fingerprint_includesApprovalsAndDistinguishesPostingDraftSourceChannel() {
    PostingCommand commandWithApproval =
        postingCommand(
            SourceChannel.CLI,
            List.of(
                new ApprovalReference(
                    new ApprovalId("approval-1"),
                    new ApprovalType("owner-review"),
                    new ActorId("owner-1"),
                    ActorType.PERSON,
                    ApprovalDecision.APPROVED,
                    Instant.parse("2026-04-07T10:15:30Z"))));
    PostingCommand commandWithoutApproval = postingCommand(SourceChannel.CLI, List.of());
    RequestFingerprint commandFingerprint =
        RequestFingerprintOwner.fingerprint(commandWithApproval);
    PostingDraft draft =
        new PostingDraft(
            commandWithApproval.journalEntry(),
            commandWithApproval.postingLineage(),
            commandWithApproval.postingKind(),
            commandWithApproval.postingOriginKind(),
            commandWithApproval.evidence(),
            new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
            new CommittedProvenance(
                commandWithApproval.requestProvenance(),
                Instant.parse("2026-04-07T12:00:00Z"),
                SourceChannel.SYSTEM));

    assertNotEquals(
        commandFingerprint, RequestFingerprintOwner.fingerprint(commandWithoutApproval));
    assertNotEquals(commandFingerprint, RequestFingerprintOwner.fingerprint(draft));
  }

  @Test
  void fingerprint_distinguishesRetainedCallerAuthoredEntryVariants() {
    List<RequestFingerprint> fingerprints =
        List.of(
                new BookkeepingEntry.DirectJournal(testJournalEntry(), null),
                new BookkeepingEntry.ExpenseSettled(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("5000"),
                    new AccountCode("1000"),
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    null),
                new BookkeepingEntry.OwnerContribution(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("3000"),
                    new MonetaryAmount("EUR", "1000"),
                    null),
                new BookkeepingEntry.OwnerWithdrawal(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("3010"),
                    new AccountCode("1000"),
                    new MonetaryAmount("EUR", "1000"),
                    null),
                new BookkeepingEntry.OpeningPosition(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            new MonetaryAmount("EUR", "1000")),
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("3000"),
                            JournalLine.EntrySide.CREDIT,
                            new MonetaryAmount("EUR", "1000")))),
                new BookkeepingEntry.Reversal(
                    testJournalEntry().effectiveDate(),
                    new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                        new ReversalReference(new PostingId("prior-posting")),
                        new ReversalReason("operator reversal")),
                    null,
                    testJournalEntry()))
            .stream()
            .map(RequestFingerprintOwnerTest::postingCommand)
            .map(RequestFingerprintOwner::fingerprint)
            .toList();

    assertEquals(fingerprints.size(), new LinkedHashSet<>(fingerprints).size());
  }

  @Test
  void fingerprint_distinguishesTaxSelectionAndAppliedTaxSemantics() {
    TaxSelection selection = taxSelection("vat-standard-sale");
    RequestFingerprint noTaxFingerprint =
        RequestFingerprintOwner.fingerprint(postingCommand(sale(null, null)));
    RequestFingerprint payableTaxFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingCommand(
                sale(
                    selection,
                    appliedTax(
                        TaxApplicationKind.OUTPUT_SALE,
                        TaxInclusionMode.EXCLUSIVE,
                        "vat-standard-sale",
                        "1000",
                        "210",
                        "1210",
                        "2100"))));
    RequestFingerprint otherPayableFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingCommand(
                sale(
                    selection,
                    appliedTax(
                        TaxApplicationKind.OUTPUT_SALE,
                        TaxInclusionMode.EXCLUSIVE,
                        "vat-standard-sale",
                        "1000",
                        "210",
                        "1210",
                        "2199"))));

    assertEquals(
        3,
        new LinkedHashSet<>(
                List.of(noTaxFingerprint, payableTaxFingerprint, otherPayableFingerprint))
            .size());
  }

  @Test
  void fingerprint_followsSemanticInputsAcrossCommandAndDraftModels() {
    BookkeepingEntry saleEntry =
        sale(
            taxSelection("vat-standard-sale"),
            appliedTax(
                TaxApplicationKind.OUTPUT_SALE,
                TaxInclusionMode.EXCLUSIVE,
                "vat-standard-sale",
                "1000",
                "210",
                "1210",
                "2100"));
    RequestFingerprint commandFingerprint =
        RequestFingerprintOwner.fingerprint(postingCommand(saleEntry));
    RequestFingerprint retainedDraftFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingDraft(saleEntry, accountingEvidence("entry-fingerprint"), saleEntry));
    RequestFingerprint droppedEntryFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingDraft(saleEntry, accountingEvidence("entry-fingerprint"), null));
    RequestFingerprint changedEvidenceFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingDraft(saleEntry, accountingEvidence("different-evidence"), saleEntry));
    BookkeepingEntry reversalEntry =
        new BookkeepingEntry.Reversal(
            testJournalEntry().effectiveDate(),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("prior-posting-2")),
                new ReversalReason("operator reversal 2")),
            null,
            testJournalEntry());
    RequestFingerprint directDraftFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingDraft(
                new BookkeepingEntry.DirectJournal(testJournalEntry(), null),
                accountingEvidence("lineage-direct"),
                null));
    RequestFingerprint reversalDraftFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingDraft(reversalEntry, accountingEvidence("lineage-reversal"), null));

    assertEquals(commandFingerprint, retainedDraftFingerprint);
    assertNotEquals(retainedDraftFingerprint, droppedEntryFingerprint);
    assertNotEquals(retainedDraftFingerprint, changedEvidenceFingerprint);
    assertNotEquals(directDraftFingerprint, reversalDraftFingerprint);
  }

  @Test
  void fingerprint_distinguishesNullAndPresentTaxAccountCodes() {
    RequestFingerprint recoverableExpenseFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingCommand(
                expense(
                    taxSelection("vat-standard-expense"),
                    appliedTax(
                        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                        TaxInclusionMode.INCLUSIVE,
                        "vat-standard-expense",
                        "1000",
                        "210",
                        "1210",
                        "1300"))));
    RequestFingerprint nonrecoverableExpenseFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingCommand(
                expense(
                    taxSelection("vat-nonrecoverable-expense"),
                    appliedTax(
                        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
                        TaxInclusionMode.INCLUSIVE,
                        "vat-nonrecoverable-expense",
                        "1000",
                        "120",
                        "1120",
                        null))));

    assertNotEquals(recoverableExpenseFingerprint, nonrecoverableExpenseFingerprint);
  }

  @Test
  void fingerprint_distinguishesOwnedForeignExchangeFacts() {
    RequestFingerprint noForeignExchangeFingerprint =
        RequestFingerprintOwner.fingerprint(postingCommand(sale(null, null)));
    RequestFingerprint ecbQuoteFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingCommand(
                new BookkeepingEntry.SaleSettled(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "9200"),
                    null,
                    foreignExchangeDetails("ecb-spot"),
                    null,
                    null)));
    RequestFingerprint bankQuoteFingerprint =
        RequestFingerprintOwner.fingerprint(
            postingCommand(
                new BookkeepingEntry.SaleSettled(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "9200"),
                    null,
                    foreignExchangeDetails("bank-close"),
                    null,
                    null)));

    assertNotEquals(noForeignExchangeFingerprint, ecbQuoteFingerprint);
    assertNotEquals(ecbQuoteFingerprint, bankQuoteFingerprint);
  }

  @Test
  void fingerprint_distinguishesTradingSaleInventoryReliefFacts() {
    RequestFingerprint noInventoryReliefFingerprint =
        RequestFingerprintOwner.fingerprint(postingCommand(sale(null, null)));
    RequestFingerprint inventoryReliefFingerprint =
        RequestFingerprintOwner.fingerprint(postingCommand(saleWithInventoryRelief("400")));
    RequestFingerprint differentInventoryReliefFingerprint =
        RequestFingerprintOwner.fingerprint(postingCommand(saleWithInventoryRelief("450")));

    assertNotEquals(noInventoryReliefFingerprint, inventoryReliefFingerprint);
    assertNotEquals(inventoryReliefFingerprint, differentInventoryReliefFingerprint);
  }

  private static PostingCommand postingCommand(
      SourceChannel sourceChannel, List<ApprovalReference> approvals) {
    return new PostingCommand(
        dev.erst.fingrind.core.PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new dev.erst.fingrind.core.AccountCode("4000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        PostingLineageModel.direct(),
        new dev.erst.fingrind.core.AccountingEvidence(
            accountingEvidence("fingerprint").sourceDocuments(), approvals),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.PERSON,
            new CommandId("command-1"),
            new IdempotencyKey("idem-1"),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        sourceChannel);
  }

  private static PostingCommand postingCommand(BookkeepingEntry entry) {
    return new PostingCommand(
        entry.postingKind(),
        entry.postingOriginKind(),
        entry.journalEntry(),
        switch (entry.postingLineage()) {
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Direct _ ->
              PostingLineageModel.direct();
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal ->
              PostingLineageModel.reversal(reversal.reference(), reversal.reason());
        },
        accountingEvidence("entry-fingerprint"),
        new RequestProvenance(
            new ActorId("actor-entry"),
            ActorType.PERSON,
            new CommandId("command-entry"),
            new IdempotencyKey("idem-entry"),
            new CausationId("cause-entry"),
            Optional.of(new CorrelationId("corr-entry"))),
        SourceChannel.CLI,
        entry);
  }

  private static PostingDraft postingDraft(
      BookkeepingEntry entry,
      AccountingEvidence evidence,
      @Nullable BookkeepingEntry retainedEntry) {
    return new PostingDraft(
        entry.journalEntry(),
        switch (entry.postingLineage()) {
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Direct _ ->
              PostingLineageModel.direct();
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal ->
              PostingLineageModel.reversal(reversal.reference(), reversal.reason());
        },
        entry.postingKind(),
        entry.postingOriginKind(),
        evidence,
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "1".repeat(64)),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-entry"),
                ActorType.PERSON,
                new CommandId("command-entry"),
                new IdempotencyKey("idem-entry"),
                new CausationId("cause-entry"),
                Optional.of(new CorrelationId("corr-entry"))),
            Instant.parse("2026-04-07T12:00:00Z"),
            SourceChannel.CLI),
        retainedEntry);
  }

  private static BookkeepingEntry.SaleSettled sale(
      @Nullable TaxSelection taxSelection, @Nullable AppliedTax appliedTax) {
    return new BookkeepingEntry.SaleSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        taxSelection,
        appliedTax);
  }

  private static BookkeepingEntry.SaleSettled saleWithInventoryRelief(String inventoryMinorUnits) {
    return new BookkeepingEntry.SaleSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1000"),
        new InventoryRelief(
            new AccountCode("1400"),
            new AccountCode("5000"),
            new MonetaryAmount("EUR", inventoryMinorUnits)),
        null,
        null,
        null);
  }

  private static BookkeepingEntry.ExpenseSettled expense(
      @Nullable TaxSelection taxSelection, @Nullable AppliedTax appliedTax) {
    return new BookkeepingEntry.ExpenseSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("5000"),
        new AccountCode("1000"),
        appliedTax == null
            ? new MonetaryAmount("EUR", "1000")
            : new MonetaryAmount("EUR", appliedTax.grossAmount().minorUnits()),
        null,
        taxSelection,
        appliedTax);
  }

  private static TaxSelection taxSelection(String taxCode) {
    return new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode(taxCode));
  }

  private static AppliedTax appliedTax(
      TaxApplicationKind applicationKind,
      TaxInclusionMode inclusionMode,
      String taxCode,
      String taxableMinorUnits,
      String taxMinorUnits,
      String grossMinorUnits,
      @Nullable String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode(taxCode),
        new TaxCodeName("VAT standard sale"),
        new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", taxableMinorUnits),
        new MonetaryAmount("EUR", taxMinorUnits),
        new MonetaryAmount("EUR", grossMinorUnits),
        taxAccountCode == null ? null : new AccountCode(taxAccountCode));
  }

  private static ForeignExchangeDetails foreignExchangeDetails(String quoteSource) {
    return new ForeignExchangeDetails(
        new MonetaryAmount("USD", "10000"),
        new MonetaryAmount("EUR", "9200"),
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "9200"),
            LocalDate.parse("2026-04-06"),
            quoteSource),
        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);
  }

  private static JournalEntry testJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("4000"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "10.00"))));
  }
}
