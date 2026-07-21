package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Map;

/** Period-administration-owned rejection descriptors. */
final class BookPeriodAdministrationRejectionDescriptorDefinitions {
  private BookPeriodAdministrationRejectionDescriptorDefinitions() {}

  static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      definitions() {
    return BookAdministrationRejectionDescriptorDefinitionSupport.merge(
        closeTargetDefinitions(), interimResultSweepDefinitions(), fiscalYearCloseDefinitions());
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      closeTargetDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor
                .CLOSING_EQUITY_ACCOUNT_CANDIDATE_MISSING,
            definition(
                "close-target-account-candidate-missing",
                "Close command refused because policy could not find one active declared account for the required close-target classification.",
                List.of(
                    detailField(
                        "requiredFinancialPositionLineClassification",
                        "Required financialPositionLineClassification for the selected close-target policy."),
                    detailField(
                        "inactiveCandidateAccountCodes",
                        "Matching declared account codes that satisfy the required close-target classification but are inactive.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor
                .CLOSING_EQUITY_ACCOUNT_CANDIDATE_AMBIGUOUS,
            definition(
                "close-target-account-candidate-ambiguous",
                "Close command refused because more than one active declared account satisfies the required close-target classification.",
                List.of(
                    detailField(
                        "requiredFinancialPositionLineClassification",
                        "Required financialPositionLineClassification for the selected close-target policy."),
                    detailField(
                        "candidateAccountCodes",
                        "Active declared account codes that all satisfy the required close-target classification and therefore make the selected close target ambiguous.")))));
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      interimResultSweepDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.INTERIM_RESULT_SWEEP_MUST_START_AT,
            definition(
                "interim-result-sweep-must-start-at",
                "Interim result sweep refused because the requested effectiveDateFrom does not match the live unswept horizon.",
                List.of(
                    detailField(
                        "requiredEffectiveDateFrom",
                        "Only admissible effectiveDateFrom for the next contiguous interim-result sweep.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.INTERIM_RESULT_SWEEP_FUTURE_DATE,
            definition(
                "interim-result-sweep-future-date",
                "Interim result sweep refused because the requested effectiveDateTo lies after the current UTC date.",
                List.of(
                    detailField(
                        "attemptedEffectiveDateTo",
                        "Requested effectiveDateTo that lies after the current UTC date.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor
                .INTERIM_RESULT_SWEEP_CROSSES_FISCAL_YEAR_BOUNDARY,
            definition(
                "interim-result-sweep-crosses-fiscal-year-boundary",
                "Interim result sweep refused because the requested reporting period crosses the configured fiscal-year boundary.",
                List.of(
                    detailField(
                        "attemptedEffectiveDateFrom",
                        "Requested effectiveDateFrom for a sweep period that crosses the fiscal-year boundary."),
                    detailField(
                        "attemptedEffectiveDateTo",
                        "Requested effectiveDateTo for a sweep period that crosses the fiscal-year boundary."),
                    detailField(
                        "fiscalYearStart",
                        "Configured fiscal-year start anchor that the requested period crosses.")))));
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      fiscalYearCloseDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.FISCAL_YEAR_CLOSE_MUST_START_AT,
            definition(
                "fiscal-year-close-must-start-at",
                "Fiscal-year close refused because the requested effectiveDateFrom does not match the fiscal year start for the selected period.",
                List.of(
                    detailField(
                        "requiredEffectiveDateFrom",
                        "Only admissible effectiveDateFrom for a fiscal-year close covering the selected year.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.FISCAL_YEAR_CLOSE_MUST_END_AT,
            definition(
                "fiscal-year-close-must-end-at",
                "Fiscal-year close refused because the requested effectiveDateTo does not match the fiscal year end for the selected period.",
                List.of(
                    detailField(
                        "requiredEffectiveDateTo",
                        "Only admissible effectiveDateTo for a fiscal-year close covering the selected year.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor
                .FISCAL_YEAR_CLOSE_PRECEDES_TRANSFERRED_THROUGH_HORIZON,
            definition(
                "fiscal-year-close-precedes-transferred-through-horizon",
                "Fiscal-year close refused because the selected fiscal year ends before the live transferred-through horizon already recorded in this book.",
                List.of(
                    detailField(
                        "attemptedEffectiveDateTo",
                        "Selected fiscal-year end that precedes the live transferred-through horizon."),
                    detailField(
                        "transferredThroughEffectiveDate",
                        "Inclusive effective date through which interim-result sweeps already transfer this book.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.FISCAL_YEAR_CLOSE_FUTURE_DATE,
            definition(
                "fiscal-year-close-future-date",
                "Fiscal-year close refused because the requested effectiveDateTo lies after the current UTC date.",
                List.of(
                    detailField(
                        "attemptedEffectiveDateTo",
                        "Requested effectiveDateTo that lies after the current UTC date.")))),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor
                .FISCAL_YEAR_CLOSE_REQUIRES_GENERATED_POSTINGS,
            definition(
                "fiscal-year-close-requires-generated-postings",
                "Fiscal-year close refused because the selected period would not generate any durable close postings.",
                List.of())));
  }

  private static BookAdministrationRejectionDescriptorDefinition definition(
      String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
    return new BookAdministrationRejectionDescriptorDefinition(
        ContractResponse.FailureCategory.DOMAIN_SEMANTIC,
        code,
        description,
        List.copyOf(detailFields));
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }
}
