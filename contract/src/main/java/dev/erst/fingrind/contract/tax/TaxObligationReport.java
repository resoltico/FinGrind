package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Tax-obligation report for one registration and one filing period. */
public record TaxObligationReport(
    BookIdentity bookIdentity,
    DeclaredTaxRegistration registration,
    ReportingPeriod reportingPeriod,
    LocalDate dueDate,
    List<TaxObligationCodeSummary> codeSummaries,
    MonetaryAmount outputTax,
    MonetaryAmount recoverableInputTax,
    MonetaryAmount nonrecoverableInputTax,
    MonetaryAmount netPayable,
    MonetaryAmount netReceivable) {
  /** Validates one tax-obligation report. */
  public TaxObligationReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(registration, "registration");
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(dueDate, "dueDate");
    codeSummaries = ContractDescriptorValidation.copyList(codeSummaries, "codeSummaries");
    Objects.requireNonNull(outputTax, "outputTax");
    Objects.requireNonNull(recoverableInputTax, "recoverableInputTax");
    Objects.requireNonNull(nonrecoverableInputTax, "nonrecoverableInputTax");
    Objects.requireNonNull(netPayable, "netPayable");
    Objects.requireNonNull(netReceivable, "netReceivable");
  }
}
