package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.PostingLatvianPayrollRejectionSemantics;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.core.CurrencyUnit;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** First-defense admission and calculation resolution for the Latvian monthly-payroll profile. */
public final class LatvianPayrollAdmissionPolicy {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  /** Resolves an admitted payroll request or returns its deterministic refusal. */
  public Resolution resolve(
      BookkeepingEntry entry, PostingValidationStore book, String selectorValue) {
    if (!(entry instanceof LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll)) {
      return Resolution.accepted(entry);
    }
    if (!EUR.equals(book.requireInitializedBookIdentity().functionalCurrency())) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.requiresEurBook(
              selectorValue, book.requireInitializedBookIdentity().functionalCurrency().code()));
    }
    if (book.findLatvianPayrollRun(payroll.payrollRunId()).isPresent()) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.runIdAlreadyExists(
              selectorValue, payroll.payrollRunId()));
    }
    if (book.findActiveLatvianPayrollRun(payroll.employeeReference(), payroll.payrollMonth())
        .isPresent()) {
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.employeeMonthAlreadyExists(
              selectorValue, payroll.employeeReference(), payroll.payrollMonth()));
    }
    try {
      return Resolution.accepted(
          new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
              payroll.effectiveDate(),
              payroll.payrollRunId(),
              payroll.employeeReference(),
              payroll.payrollMonth(),
              payroll.withholdingProfile(),
              payroll.wageExpenseAccountCode(),
              payroll.employerSocialContributionExpenseAccountCode(),
              payroll.netWagesPayableAccountCode(),
              payroll.employeeSocialContributionPayableAccountCode(),
              payroll.employerSocialContributionPayableAccountCode(),
              payroll.personalIncomeTaxPayableAccountCode(),
              payroll.grossWages(),
              LatvianMonthlyPayroll2026.calculate(
                  payroll.payrollMonth(),
                  payroll.grossWages().toMoney(),
                  payroll.withholdingProfile())));
    } catch (IllegalArgumentException exception) {
      ProfileFact rejectedFact = profileFact(payroll, exception);
      return Resolution.rejected(
          PostingLatvianPayrollRejectionSemantics.profileNotAdmitted(
              selectorValue, rejectedFact.field(), rejectedFact.renderedValue()));
    }
  }

  private static ProfileFact profileFact(
      LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll,
      IllegalArgumentException exception) {
    String message = java.util.Objects.requireNonNullElse(exception.getMessage(), "");
    if (message.startsWith("Gross wages exceed")) {
      return new ProfileFact(
          "grossWages", "grossWages '" + payroll.grossWages().canonicalDecimal() + "'");
    }
    if (message.contains("requires EUR gross wages")) {
      return new ProfileFact(
          "grossWages.currencyCode",
          "grossWages.currencyCode '" + payroll.grossWages().currencyCode() + "'");
    }
    if (message.startsWith("taxBookHeldAtEmployer")) {
      return new ProfileFact(
          "taxBookHeldAtEmployer",
          "taxBookHeldAtEmployer '" + payroll.withholdingProfile().taxBookHeldAtEmployer() + "'");
    }
    if (message.startsWith("dependantCount")) {
      return new ProfileFact(
          "dependantCount",
          "dependantCount '" + payroll.withholdingProfile().dependantCount() + "'");
    }
    return new ProfileFact(
        "payrollMonth", "payrollMonth '" + payroll.payrollMonth().wireValue() + "'");
  }

  private record ProfileFact(String field, String renderedValue) {}

  /** One resolved entry or its deterministic entry-semantics rejection. */
  public record Resolution(
      @Nullable BookkeepingEntry entry, Optional<BookkeepingPostingRejection> rejection) {
    public Resolution {
      rejection =
          Optional.ofNullable(
              java.util.Objects.requireNonNull(rejection, "rejection").orElse(null));
      if (rejection.isEmpty() && entry == null) {
        throw new IllegalArgumentException(
            "Accepted Latvian payroll resolution requires one entry.");
      }
    }

    static Resolution accepted(BookkeepingEntry entry) {
      return new Resolution(entry, Optional.empty());
    }

    static Resolution rejected(
        dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation rejection) {
      return new Resolution(
          null,
          Optional.of(
              new BookkeepingPostingRejection.EntrySemanticsViolations(
                  List.of(
                      new BookkeepingPostingRejection.EntrySemanticsViolation(
                          rejection.code(), rejection.field(), rejection.message())))));
    }
  }
}
