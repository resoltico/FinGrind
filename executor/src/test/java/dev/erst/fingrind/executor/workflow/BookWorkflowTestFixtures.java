package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
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
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Shared valid input fixtures for focused internal workflow tests. */
final class BookWorkflowTestFixtures {
  static final Instant EXECUTED_AT = Instant.parse("2026-07-24T10:15:30Z");
  static final Clock CLOCK = Clock.fixed(EXECUTED_AT, ZoneOffset.UTC);
  static final PostingId POSTING_ID = new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69");

  private BookWorkflowTestFixtures() {}

  static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(EXECUTED_AT, bookIdentity(), List.of());
    return bookSession;
  }

  static BookWorkflowStepId stepId(String value) {
    return new BookWorkflowStepId(value);
  }

  static AccountDeclaration accountDeclaration() {
    return new AccountDeclaration(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT));
  }

  static DeclareTaxRegistrationCommand taxRegistrationCommand() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2200"),
        new AccountCode("2210"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  static AccountRegistryQuery accountQuery() {
    return new AccountRegistryQuery(10, Optional.empty());
  }

  static PostingHistoryQuery postingQuery() {
    return new PostingHistoryQuery(
        Optional.empty(), EffectiveDateRange.unbounded(), 10, Optional.empty());
  }

  static AccountBalanceCriteria balanceCriteria() {
    return new AccountBalanceCriteria(
        new AccountCode("1000"), EffectiveDateRange.unbounded(), PostingCoverage.ALL_POSTING_KINDS);
  }
}
