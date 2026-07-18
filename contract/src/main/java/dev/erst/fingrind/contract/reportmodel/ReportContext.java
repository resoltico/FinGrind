package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared context rows projected with every report model. */
public record ReportContext(
    String entity,
    String seedTemplate,
    String accountingBasis,
    String functionalCurrency,
    String fiscalYearStart,
    String bookStartEffectiveDate,
    @Nullable String postingCoverage,
    @Nullable String periodStart,
    @Nullable String periodEnd,
    @Nullable String asOf,
    @Nullable String comparativePeriodStart,
    @Nullable String comparativePeriodEnd,
    @Nullable String taxRegistrationId,
    @Nullable String taxRegistrationName,
    @Nullable String taxJurisdiction,
    @Nullable String dueDate,
    List<ReportVerdict> supplementalRows) {
  /** Validates one report context block. */
  public ReportContext {
    entity = ContractDescriptorValidation.requireText(entity, "entity");
    seedTemplate = ContractDescriptorValidation.requireText(seedTemplate, "seedTemplate");
    accountingBasis = ContractDescriptorValidation.requireText(accountingBasis, "accountingBasis");
    functionalCurrency =
        ContractDescriptorValidation.requireText(functionalCurrency, "functionalCurrency");
    fiscalYearStart = ContractDescriptorValidation.requireText(fiscalYearStart, "fiscalYearStart");
    bookStartEffectiveDate =
        ContractDescriptorValidation.requireText(bookStartEffectiveDate, "bookStartEffectiveDate");
    postingCoverage =
        ContractDescriptorValidation.requireOptionalText(postingCoverage, "postingCoverage");
    periodStart = ContractDescriptorValidation.requireOptionalText(periodStart, "periodStart");
    periodEnd = ContractDescriptorValidation.requireOptionalText(periodEnd, "periodEnd");
    asOf = ContractDescriptorValidation.requireOptionalText(asOf, "asOf");
    comparativePeriodStart =
        ContractDescriptorValidation.requireOptionalText(
            comparativePeriodStart, "comparativePeriodStart");
    comparativePeriodEnd =
        ContractDescriptorValidation.requireOptionalText(
            comparativePeriodEnd, "comparativePeriodEnd");
    taxRegistrationId =
        ContractDescriptorValidation.requireOptionalText(taxRegistrationId, "taxRegistrationId");
    taxRegistrationName =
        ContractDescriptorValidation.requireOptionalText(
            taxRegistrationName, "taxRegistrationName");
    taxJurisdiction =
        ContractDescriptorValidation.requireOptionalText(taxJurisdiction, "taxJurisdiction");
    dueDate = ContractDescriptorValidation.requireOptionalText(dueDate, "dueDate");
    supplementalRows = ContractDescriptorValidation.copyList(supplementalRows, "supplementalRows");
  }

  /** Returns this context as ordered key-value verdict rows. */
  public List<ReportVerdict> rows() {
    List<ReportVerdict> rows = new ArrayList<>();
    appendRequiredRows(rows);
    appendOptionalRow(rows, "Posting coverage", postingCoverage);
    appendOptionalRow(rows, "Period start", periodStart);
    appendOptionalRow(rows, "Period end", periodEnd);
    appendOptionalRow(rows, "As of", asOf);
    appendOptionalRow(rows, "Comparative period start", comparativePeriodStart);
    appendOptionalRow(rows, "Comparative period end", comparativePeriodEnd);
    appendOptionalRow(rows, "Tax registration id", taxRegistrationId);
    appendOptionalRow(rows, "Tax registration name", taxRegistrationName);
    appendOptionalRow(rows, "Jurisdiction", taxJurisdiction);
    appendOptionalRow(rows, "Due date", dueDate);
    rows.addAll(supplementalRows);
    return List.copyOf(rows);
  }

  private void appendRequiredRows(List<ReportVerdict> rows) {
    rows.add(new ReportVerdict("Entity", entity));
    rows.add(new ReportVerdict("Seed template", seedTemplate));
    rows.add(new ReportVerdict("Accounting basis", accountingBasis));
    rows.add(new ReportVerdict("Functional currency", functionalCurrency));
    rows.add(new ReportVerdict("Fiscal year start", fiscalYearStart));
    rows.add(new ReportVerdict("Book start effective date", bookStartEffectiveDate));
  }

  private static void appendOptionalRow(
      List<ReportVerdict> rows, String label, @Nullable String value) {
    if (value != null) {
      rows.add(new ReportVerdict(label, value));
    }
  }
}
