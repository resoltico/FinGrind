package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.TaxReadStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct coverage for tax registration listing and obligation reporting. */
class TaxReadServiceTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-01T10:15:30Z");
  private static final BookIdentity BOOK_IDENTITY = bookIdentity();

  @Test
  void listTaxRegistrations_rejectsWhenBookIsNotInitialized() {
    TaxReadService service =
        new TaxReadService(
            new InMemoryTaxReadStore(
                new BookLifecycleInspection.Missing(1), List.of(), List.of(), emptyPage(10)));

    ListTaxRegistrationsResult.Rejected rejected =
        assertInstanceOf(
            ListTaxRegistrationsResult.Rejected.class,
            service.listTaxRegistrations(new ListTaxRegistrationsQuery(10, Optional.empty())));

    assertInstanceOf(TaxQueryRejection.BookNotInitialized.class, rejected.rejection());
  }

  @Test
  void listTaxRegistrations_returnsCurrentRegistryPage() {
    DeclaredTaxRegistration registration = registration("vat-lv");
    TaxRegistrationPage page =
        new TaxRegistrationPage(BOOK_IDENTITY, List.of(registration), 10, Optional.empty());
    TaxReadService service =
        new TaxReadService(
            new InMemoryTaxReadStore(
                initializedLifecycleInspection(1001, 25, 25, DECLARED_AT),
                List.of(registration),
                List.of(),
                page));

    ListTaxRegistrationsResult.Listed listed =
        assertInstanceOf(
            ListTaxRegistrationsResult.Listed.class,
            service.listTaxRegistrations(new ListTaxRegistrationsQuery(10, Optional.empty())));

    assertEquals(page, listed.page());
  }

  @Test
  void taxObligation_rejectsWhenBookIsNotInitialized() {
    TaxReadService service =
        new TaxReadService(
            new InMemoryTaxReadStore(
                new BookLifecycleInspection.Missing(1),
                List.of(registration("vat-lv")),
                List.of(),
                emptyPage(10)));

    TaxObligationResult.Rejected rejected =
        assertInstanceOf(
            TaxObligationResult.Rejected.class,
            service.taxObligation(
                new TaxObligationQuery(
                    new TaxRegistrationId("vat-lv"),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"))));

    assertInstanceOf(TaxQueryRejection.BookNotInitialized.class, rejected.rejection());
  }

  @Test
  void taxObligation_rejectsUnknownRegistrationAndCadenceMismatch() {
    DeclaredTaxRegistration monthlyRegistration = registration("vat-lv");
    TaxReadService service =
        new TaxReadService(
            new InMemoryTaxReadStore(
                initializedLifecycleInspection(1001, 25, 25, DECLARED_AT),
                List.of(monthlyRegistration),
                List.of(),
                emptyPage(10)));

    TaxObligationResult.Rejected unknownRegistration =
        assertInstanceOf(
            TaxObligationResult.Rejected.class,
            service.taxObligation(
                new TaxObligationQuery(
                    new TaxRegistrationId("missing-tax"),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"))));
    assertInstanceOf(
        TaxQueryRejection.UnknownTaxRegistration.class, unknownRegistration.rejection());

    TaxObligationResult.Rejected cadenceMismatch =
        assertInstanceOf(
            TaxObligationResult.Rejected.class,
            service.taxObligation(
                new TaxObligationQuery(
                    monthlyRegistration.taxRegistrationId(),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-06-30"))));
    assertInstanceOf(TaxQueryRejection.ObligationPeriodMismatch.class, cadenceMismatch.rejection());
  }

  @Test
  void taxObligation_aggregatesOutputRecoverableAndNonrecoverableTaxByCode() {
    DeclaredTaxRegistration registration = registration("vat-lv");
    DeclaredTaxRegistration ignoredRegistration = registration("vat-ee");
    TaxReadService service =
        new TaxReadService(
            new InMemoryTaxReadStore(
                initializedLifecycleInspection(1001, 25, 25, DECLARED_AT),
                List.of(registration, ignoredRegistration),
                List.of(
                    committedPosting(
                        "sale-1",
                        new BookkeepingEntry.Sale(
                            LocalDate.parse("2026-04-05"),
                            new AccountCode("1000"),
                            new AccountCode("4000"),
                            new MonetaryAmount("EUR", "10000"),
                            null,
                            selection("vat-lv", "vat-standard-sale"),
                            appliedTax(
                                "vat-lv",
                                "vat-standard-sale",
                                "VAT Standard Sale",
                                TaxInclusionMode.EXCLUSIVE,
                                TaxApplicationKind.OUTPUT_SALE,
                                "10000",
                                "2100",
                                "12100",
                                "2100"))),
                    committedPosting(
                        "sale-2",
                        new BookkeepingEntry.Sale(
                            LocalDate.parse("2026-04-09"),
                            new AccountCode("1000"),
                            new AccountCode("4000"),
                            new MonetaryAmount("EUR", "5000"),
                            null,
                            selection("vat-lv", "vat-standard-sale"),
                            appliedTax(
                                "vat-lv",
                                "vat-standard-sale",
                                "VAT Standard Sale",
                                TaxInclusionMode.EXCLUSIVE,
                                TaxApplicationKind.OUTPUT_SALE,
                                "5000",
                                "1050",
                                "6050",
                                "2100"))),
                    committedPosting(
                        "expense-recoverable",
                        new BookkeepingEntry.Expense(
                            LocalDate.parse("2026-04-12"),
                            new AccountCode("5000"),
                            new AccountCode("1000"),
                            new MonetaryAmount("EUR", "6050"),
                            null,
                            selection("vat-lv", "vat-standard-expense"),
                            appliedTax(
                                "vat-lv",
                                "vat-standard-expense",
                                "VAT Standard Expense",
                                TaxInclusionMode.INCLUSIVE,
                                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                                "5000",
                                "1050",
                                "6050",
                                "1300"))),
                    committedPosting(
                        "expense-nonrecoverable",
                        new BookkeepingEntry.Expense(
                            LocalDate.parse("2026-04-18"),
                            new AccountCode("5010"),
                            new AccountCode("1000"),
                            new MonetaryAmount("EUR", "5600"),
                            null,
                            selection("vat-lv", "vat-nonrecoverable-expense"),
                            appliedTax(
                                "vat-lv",
                                "vat-nonrecoverable-expense",
                                "VAT Nonrecoverable Expense",
                                TaxInclusionMode.INCLUSIVE,
                                TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
                                "5000",
                                "600",
                                "5600",
                                null))),
                    committedPosting(
                        "ignored-other-registration",
                        new BookkeepingEntry.Sale(
                            LocalDate.parse("2026-04-20"),
                            new AccountCode("1000"),
                            new AccountCode("4000"),
                            new MonetaryAmount("EUR", "1000"),
                            null,
                            selection("vat-ee", "vat-standard-sale"),
                            appliedTax(
                                "vat-ee",
                                "vat-standard-sale",
                                "VAT Standard Sale",
                                TaxInclusionMode.EXCLUSIVE,
                                TaxApplicationKind.OUTPUT_SALE,
                                "1000",
                                "210",
                                "1210",
                                "2100"))),
                    committedPosting(
                        "ignored-no-tax",
                        new BookkeepingEntry.OwnerContribution(
                            LocalDate.parse("2026-04-22"),
                            new AccountCode("1000"),
                            new AccountCode("3000"),
                            new MonetaryAmount("EUR", "5000"),
                            null))),
                emptyPage(10)));

    TaxObligationResult.Reported reported =
        assertInstanceOf(
            TaxObligationResult.Reported.class,
            service.taxObligation(
                new TaxObligationQuery(
                    registration.taxRegistrationId(),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"))));

    TaxObligationReport report = reported.report();
    assertEquals(LocalDate.parse("2026-05-20"), report.dueDate());
    assertEquals("3150", report.outputTax().minorUnits());
    assertEquals("1050", report.recoverableInputTax().minorUnits());
    assertEquals("600", report.nonrecoverableInputTax().minorUnits());
    assertEquals("2100", report.netPayable().minorUnits());
    assertEquals("0", report.netReceivable().minorUnits());
    assertEquals(3, report.codeSummaries().size());
    assertEquals(
        List.of("vat-nonrecoverable-expense", "vat-standard-expense", "vat-standard-sale"),
        report.codeSummaries().stream().map(summary -> summary.taxCode().value()).toList());
    assertEquals(
        2,
        report.codeSummaries().stream()
            .filter(summary -> "vat-standard-sale".equals(summary.taxCode().value()))
            .findFirst()
            .orElseThrow()
            .postingCount());
  }

  @Test
  void taxObligation_reportsNetReceivableWhenRecoverableInputTaxExceedsOutputTax() {
    DeclaredTaxRegistration registration = registration("vat-lv");
    TaxReadService service =
        new TaxReadService(
            new InMemoryTaxReadStore(
                initializedLifecycleInspection(1001, 25, 25, DECLARED_AT),
                List.of(registration),
                List.of(
                    committedPosting(
                        "expense-only",
                        new BookkeepingEntry.Expense(
                            LocalDate.parse("2026-04-12"),
                            new AccountCode("5000"),
                            new AccountCode("1000"),
                            new MonetaryAmount("EUR", "12100"),
                            null,
                            selection("vat-lv", "vat-standard-expense"),
                            appliedTax(
                                "vat-lv",
                                "vat-standard-expense",
                                "VAT Standard Expense",
                                TaxInclusionMode.INCLUSIVE,
                                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                                "10000",
                                "2100",
                                "12100",
                                "1300")))),
                emptyPage(10)));

    TaxObligationResult.Reported reported =
        assertInstanceOf(
            TaxObligationResult.Reported.class,
            service.taxObligation(
                new TaxObligationQuery(
                    registration.taxRegistrationId(),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"))));

    assertEquals("0", reported.report().netPayable().minorUnits());
    assertEquals("2100", reported.report().netReceivable().minorUnits());
  }

  private static TaxRegistrationPage emptyPage(int limit) {
    return new TaxRegistrationPage(BOOK_IDENTITY, List.of(), limit, Optional.empty());
  }

  private static TaxSelection selection(String taxRegistrationId, String taxCode) {
    return new TaxSelection(new TaxRegistrationId(taxRegistrationId), new TaxCode(taxCode));
  }

  private static AppliedTax appliedTax(
      String taxRegistrationId,
      String taxCode,
      String taxCodeName,
      TaxInclusionMode inclusionMode,
      TaxApplicationKind applicationKind,
      String taxableMinorUnits,
      String taxMinorUnits,
      String grossMinorUnits,
      @Nullable String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId(taxRegistrationId),
        new TaxCode(taxCode),
        new TaxCodeName(taxCodeName),
        "vat-nonrecoverable-expense".equals(taxCode) ? new TaxRate(120_000) : new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", taxableMinorUnits),
        new MonetaryAmount("EUR", taxMinorUnits),
        new MonetaryAmount("EUR", grossMinorUnits),
        taxAccountCode == null ? null : new AccountCode(taxAccountCode));
  }

  private static CommittedPosting committedPosting(String postingId, BookkeepingEntry entry) {
    return new CommittedPosting(
        new PostingId(postingId),
        entry.journalEntry(),
        switch (entry.postingLineage()) {
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Direct _ ->
              PostingLineageModel.direct();
          case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal ->
              PostingLineageModel.reversal(reversal.reference(), reversal.reason());
        },
        entry.postingKind(),
        entry.postingOriginKind(),
        accountingEvidence(postingId),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-" + postingId),
                ActorType.PERSON,
                new CommandId("command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.of(new CorrelationId("corr-" + postingId))),
            Instant.parse("2026-04-30T12:00:00Z"),
            SourceChannel.CLI),
        entry);
  }

  private static DeclaredTaxRegistration registration(String taxRegistrationId) {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId(taxRegistrationId),
        new TaxRegistrationName("Latvia VAT " + taxRegistrationId),
        new TaxJurisdiction("LV"),
        null,
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
                TaxApplicationKind.OUTPUT_SALE),
            new TaxCodeDefinition(
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE),
            new TaxCodeDefinition(
                new TaxCode("vat-nonrecoverable-expense"),
                new TaxCodeName("VAT Nonrecoverable Expense"),
                new TaxRate(120_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE)),
        DECLARED_AT);
  }

  /** In-memory read-store double that exposes a fixed tax registry and posting history. */
  private static final class InMemoryTaxReadStore implements TaxReadStore {
    private final BookLifecycleInspection inspection;
    private final List<DeclaredTaxRegistration> registrations;
    private final List<CommittedPosting> postings;
    private final TaxRegistrationPage page;

    private InMemoryTaxReadStore(
        BookLifecycleInspection inspection,
        List<DeclaredTaxRegistration> registrations,
        List<CommittedPosting> postings,
        TaxRegistrationPage page) {
      this.inspection = inspection;
      this.registrations = List.copyOf(registrations);
      this.postings = List.copyOf(postings);
      this.page = page;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return inspection;
    }

    @Override
    public Optional<DeclaredTaxRegistration> findTaxRegistration(
        TaxRegistrationId taxRegistrationId) {
      return registrations.stream()
          .filter(registration -> registration.taxRegistrationId().equals(taxRegistrationId))
          .findFirst();
    }

    @Override
    public List<DeclaredTaxRegistration> allTaxRegistrations() {
      return registrations;
    }

    @Override
    public TaxRegistrationPage listTaxRegistrations(ListTaxRegistrationsQuery query) {
      return page;
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return postings.stream()
          .filter(
              posting ->
                  effectiveDateRange
                          .effectiveDateFrom()
                          .map(date -> !posting.journalEntry().effectiveDate().isBefore(date))
                          .orElse(true)
                      && effectiveDateRange
                          .effectiveDateTo()
                          .map(date -> !posting.journalEntry().effectiveDate().isAfter(date))
                          .orElse(true))
          .sorted(Comparator.comparing(posting -> posting.journalEntry().effectiveDate()))
          .toList();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return postings.stream()
          .map(posting -> posting.journalEntry().effectiveDate())
          .min(LocalDate::compareTo);
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
