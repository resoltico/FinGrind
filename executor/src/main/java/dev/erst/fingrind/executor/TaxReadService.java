package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
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
    return new ListTaxRegistrationsResult.Listed(store.listTaxRegistrations(query));
  }

  /** Computes one tax-obligation report for the selected registration and filing period. */
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
    for (var posting :
        store.postings(EffectiveDateRange.of(query.effectiveDateFrom(), query.effectiveDateTo()))) {
      AppliedTax appliedTax =
          TaxValidationSupport.appliedTax(posting.callerAuthoredEntry().orElse(null));
      if (appliedTax == null || !appliedTax.taxRegistrationId().equals(query.taxRegistrationId())) {
        continue;
      }
      totalsFor(totalsByCode, appliedTax, currencyUnit).add(appliedTax);
    }
    List<TaxObligationCodeSummary> codeSummaries = new ArrayList<>();
    Money outputTax = Money.zero(currencyUnit);
    Money recoverableInputTax = Money.zero(currencyUnit);
    Money nonrecoverableInputTax = Money.zero(currencyUnit);
    for (Map.Entry<ObligationKey, ObligationTotals> entry :
        totalsByCode.entrySet().stream()
            .sorted(Comparator.comparing(value -> value.getKey().taxCode().value()))
            .toList()) {
      ObligationKey key = entry.getKey();
      ObligationTotals totals = entry.getValue();
      codeSummaries.add(
          new TaxObligationCodeSummary(
              key.taxCode(),
              key.taxCodeName(),
              key.applicationKind(),
              totals.postingCount,
              MonetaryAmount.of(totals.taxableAmount),
              MonetaryAmount.of(totals.taxAmount),
              MonetaryAmount.of(totals.grossAmount)));
      if (key.applicationKind() == TaxApplicationKind.OUTPUT_SALE) {
        outputTax = outputTax.plus(totals.taxAmount);
      } else if (key.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE) {
        recoverableInputTax = recoverableInputTax.plus(totals.taxAmount);
      } else {
        nonrecoverableInputTax = nonrecoverableInputTax.plus(totals.taxAmount);
      }
    }
    Money netPayable =
        outputTax.compareTo(recoverableInputTax) >= 0
            ? outputTax.minus(recoverableInputTax)
            : Money.zero(currencyUnit);
    Money netReceivable =
        recoverableInputTax.compareTo(outputTax) >= 0
            ? recoverableInputTax.minus(outputTax)
            : Money.zero(currencyUnit);
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(query.effectiveDateFrom(), query.effectiveDateTo());
    return new TaxObligationResult.Reported(
        new TaxObligationReport(
            bookIdentity,
            registration,
            reportingPeriod,
            query.effectiveDateTo().plusDays(registration.dueDaysAfterPeriodEnd()),
            codeSummaries,
            MonetaryAmount.of(outputTax),
            MonetaryAmount.of(recoverableInputTax),
            MonetaryAmount.of(nonrecoverableInputTax),
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

  /** Stable aggregation key for one declared tax code inside one obligation report. */
  private record ObligationKey(
      dev.erst.fingrind.contract.tax.TaxCode taxCode,
      dev.erst.fingrind.contract.tax.TaxCodeName taxCodeName,
      TaxApplicationKind applicationKind) {}

  /** Mutable accumulator for one tax-code obligation bucket in one report build. */
  private static final class ObligationTotals {
    private Money taxableAmount;
    private Money taxAmount;
    private Money grossAmount;
    private int postingCount;

    private ObligationTotals(CurrencyUnit currencyUnit) {
      Objects.requireNonNull(currencyUnit, "currencyUnit");
      this.taxableAmount = Money.zero(currencyUnit);
      this.taxAmount = Money.zero(currencyUnit);
      this.grossAmount = Money.zero(currencyUnit);
    }

    private void add(AppliedTax appliedTax) {
      taxableAmount = taxableAmount.plus(appliedTax.taxableAmount().toMoney());
      taxAmount = taxAmount.plus(appliedTax.taxAmount().toMoney());
      grossAmount = grossAmount.plus(appliedTax.grossAmount().toMoney());
      postingCount++;
    }
  }
}
