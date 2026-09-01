package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.SignedMoney;
import dev.erst.fingrind.executor.spi.TaxReadStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service that owns tax-registration listing and obligation reporting. */
public final class TaxReadService {
  private final TaxReadStore store;

  /** Creates the tax read service with its application-owned seams. */
  public TaxReadService(TaxReadStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  /** Lists one paginated slice of the current tax-registration registry. */
  public ListTaxRegistrationsResult listTaxRegistrations(ListTaxRegistrationsQuery query) {
    Objects.requireNonNull(query, "query");
    if (!store.allowsInitializedWorkflow()) {
      return new ListTaxRegistrationsResult.Rejected(new TaxQueryRejection.BookNotInitialized());
    }
    return new ListTaxRegistrationsResult.Listed(query, store.listTaxRegistrations(query));
  }

  /** Computes one applied-tax obligation report for the selected registration and filing period. */
  public TaxObligationResult taxObligation(TaxObligationQuery query) {
    Objects.requireNonNull(query, "query");
    if (!store.allowsInitializedWorkflow()) {
      return new TaxObligationResult.Rejected(new TaxQueryRejection.BookNotInitialized());
    }
    DeclaredTaxRegistration registration =
        store.findTaxRegistration(query.taxRegistrationId()).orElse(null);
    if (registration == null) {
      return new TaxObligationResult.Rejected(
          new TaxQueryRejection.UnknownTaxRegistration(query.taxRegistrationId()));
    }
    if (!TaxValidationSupport.matchesObligationPeriod(
        registration.obligationFrequency(), query.effectiveDateFrom(), query.effectiveDateTo())) {
      return new TaxObligationResult.Rejected(
          new TaxQueryRejection.ObligationPeriodMismatch(
              registration.obligationFrequency(),
              query.effectiveDateFrom(),
              query.effectiveDateTo()));
    }
    BookIdentity bookIdentity = store.requireInitializedBookIdentity();
    CurrencyUnit currencyUnit = bookIdentity.functionalCurrency();
    Map<ObligationKey, ObligationTotals> totalsByCode = mutableTotalsByCode();
    List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> allPostings =
        store.postings(EffectiveDateRange.unbounded());
    Map<dev.erst.fingrind.core.PostingId, dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
        postingsById =
            allPostings.stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        dev.erst.fingrind.executor.bookkeeping.CommittedPosting::postingId,
                        java.util.function.Function.identity()));
    for (var posting :
        allPostings.stream()
            .filter(
                candidate ->
                    !candidate.journalEntry().effectiveDate().isBefore(query.effectiveDateFrom())
                        && !candidate
                            .journalEntry()
                            .effectiveDate()
                            .isAfter(query.effectiveDateTo()))
            .toList()) {
      TaxEffect effect = taxEffect(posting, postingsById);
      if (effect == null
          || !effect.appliedTax().taxRegistrationId().equals(query.taxRegistrationId())) {
        continue;
      }
      totalsFor(totalsByCode, effect.appliedTax(), currencyUnit).add(effect);
    }
    List<TaxObligationCodeSummary> codeSummaries = new ArrayList<>();
    SignedMoney outputTax = SignedMoney.zero(currencyUnit);
    SignedMoney recoverableInputTax = SignedMoney.zero(currencyUnit);
    SignedMoney nonrecoverableInputTax = SignedMoney.zero(currencyUnit);
    for (Map.Entry<ObligationKey, ObligationTotals> entry :
        totalsByCode.entrySet().stream()
            .sorted(Comparator.comparing(value -> value.getKey().taxCode().value()))
            .toList()) {
      ObligationKey key = entry.getKey();
      ObligationTotals totals = entry.getValue();
      if (totals.isZero()) {
        continue;
      }
      codeSummaries.add(
          new TaxObligationCodeSummary(
              key.taxCode(),
              key.taxCodeName(),
              key.applicationKind(),
              totals.postingCount,
              SignedMonetaryAmount.of(totals.taxableAmount),
              SignedMonetaryAmount.of(totals.taxAmount),
              SignedMonetaryAmount.of(totals.grossAmount)));
      if (key.applicationKind() == TaxApplicationKind.OUTPUT_SALE) {
        outputTax = outputTax.plus(totals.taxAmount);
      } else if (key.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE) {
        recoverableInputTax = recoverableInputTax.plus(totals.taxAmount);
      } else {
        nonrecoverableInputTax = nonrecoverableInputTax.plus(totals.taxAmount);
      }
    }
    Money netPayable = nonnegativeDifference(outputTax, recoverableInputTax);
    Money netReceivable = nonnegativeDifference(recoverableInputTax, outputTax);
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(query.effectiveDateFrom(), query.effectiveDateTo());
    return new TaxObligationResult.Reported(
        new TaxObligationReport(
            bookIdentity,
            registration,
            reportingPeriod,
            query.effectiveDateTo().plusDays(registration.dueDaysAfterPeriodEnd()),
            codeSummaries,
            SignedMonetaryAmount.of(outputTax),
            SignedMonetaryAmount.of(recoverableInputTax),
            SignedMonetaryAmount.of(nonrecoverableInputTax),
            MonetaryAmount.of(netPayable),
            MonetaryAmount.of(netReceivable)));
  }

  private static Map<ObligationKey, ObligationTotals> mutableTotalsByCode() {
    return new java.util.LinkedHashMap<>();
  }

  private static ObligationTotals totalsFor(
      Map<ObligationKey, ObligationTotals> totalsByCode,
      AppliedTax appliedTax,
      CurrencyUnit currencyUnit) {
    ObligationKey key = obligationKey(appliedTax);
    ObligationTotals totals = totalsByCode.get(key);
    if (totals != null) {
      return totals;
    }
    ObligationTotals createdTotals = new ObligationTotals(currencyUnit);
    totalsByCode.put(key, createdTotals);
    return createdTotals;
  }

  private static ObligationKey obligationKey(AppliedTax appliedTax) {
    return new ObligationKey(
        appliedTax.taxCode(), appliedTax.taxCodeName(), appliedTax.applicationKind());
  }

  private static @org.jspecify.annotations.Nullable TaxEffect taxEffect(
      dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting,
      Map<dev.erst.fingrind.core.PostingId, dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
          postingsById) {
    AppliedTax directTax = appliedTax(posting);
    if (directTax != null) {
      return new TaxEffect(directTax, 1);
    }
    if (posting.reversalReference().isEmpty()) {
      return null;
    }
    var original = postingsById.get(posting.reversalReference().orElseThrow().priorPostingId());
    if (original == null) {
      throw new IllegalStateException(
          "A tax reversal references a posting that is absent from the book.");
    }
    AppliedTax reversedTax = appliedTax(original);
    return reversedTax == null ? null : new TaxEffect(reversedTax, -1);
  }

  private static @org.jspecify.annotations.Nullable AppliedTax appliedTax(
      dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting) {
    return TaxValidationSupport.appliedTax(
        posting.resolvedOriginatingEntry().or(() -> posting.callerAuthoredEntry()).orElse(null));
  }

  private static Money nonnegativeDifference(SignedMoney minuend, SignedMoney subtrahend) {
    SignedMoney difference = minuend.minus(subtrahend);
    return difference.isPositive() ? difference.magnitude() : Money.zero(minuend.currencyUnit());
  }

  private record TaxEffect(AppliedTax appliedTax, int direction) {
    private TaxEffect {
      Objects.requireNonNull(appliedTax, "appliedTax");
    }
  }

  /** Stable aggregation key for one declared tax code inside one obligation report. */
  private record ObligationKey(
      dev.erst.fingrind.contract.tax.TaxCode taxCode,
      dev.erst.fingrind.contract.tax.TaxCodeName taxCodeName,
      TaxApplicationKind applicationKind) {}

  /** Mutable accumulator for one tax-code obligation bucket in one report build. */
  private static final class ObligationTotals {
    private SignedMoney taxableAmount;
    private SignedMoney taxAmount;
    private SignedMoney grossAmount;
    private int postingCount;

    private ObligationTotals(CurrencyUnit currencyUnit) {
      Objects.requireNonNull(currencyUnit, "currencyUnit");
      this.taxableAmount = SignedMoney.zero(currencyUnit);
      this.taxAmount = SignedMoney.zero(currencyUnit);
      this.grossAmount = SignedMoney.zero(currencyUnit);
    }

    private void add(TaxEffect effect) {
      AppliedTax appliedTax = effect.appliedTax();
      SignedMoney taxable = SignedMoney.of(appliedTax.taxableAmount().toMoney());
      SignedMoney tax = SignedMoney.of(appliedTax.taxAmount().toMoney());
      SignedMoney gross = SignedMoney.of(appliedTax.grossAmount().toMoney());
      if (effect.direction() == -1) {
        taxable = taxable.negated();
        tax = tax.negated();
        gross = gross.negated();
      }
      taxableAmount = taxableAmount.plus(taxable);
      taxAmount = taxAmount.plus(tax);
      grossAmount = grossAmount.plus(gross);
      postingCount++;
    }

    private boolean isZero() {
      // Evaluate each independently: a tax code's rate can change between postings, so taxable
      // cancellation alone must never hide a residual tax or gross obligation.
      return Boolean.logicalAnd(
          Boolean.logicalAnd(taxableAmount.isZero(), taxAmount.isZero()), grossAmount.isZero());
    }
  }
}
