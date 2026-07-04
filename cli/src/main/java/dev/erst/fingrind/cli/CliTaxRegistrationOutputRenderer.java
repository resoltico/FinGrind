package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import java.util.ArrayList;
import java.util.List;

/** Renders tax-registration command results for text and CSV output modes. */
final class CliTaxRegistrationOutputRenderer {
  private static final String TAX_REGISTRATIONS_RECORD_KIND = "taxRegistration";

  private CliTaxRegistrationOutputRenderer() {}

  static String renderTaxRegistrationMutationText(
      String outcome, DeclaredTaxRegistration registration) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Outcome", outcome));
    rows.add(List.of("Tax registration id", registration.taxRegistrationId().value()));
    rows.add(List.of("Name", registration.taxRegistrationName().value()));
    rows.add(List.of("Jurisdiction", registration.jurisdiction().value()));
    rows.add(
        List.of(
            "Registration number",
            registration.registrationNumber() == null
                ? "(none)"
                : registration.registrationNumber().value()));
    rows.add(List.of("Payable account", registration.payableAccountCode().value()));
    rows.add(List.of("Recoverable account", registration.recoverableAccountCode().value()));
    rows.add(
        List.of(
            "Obligation frequency",
            CliTextDisplay.wireLabel(registration.obligationFrequency().wireValue())));
    rows.add(
        List.of(
            "Due days after period end", Integer.toString(registration.dueDaysAfterPeriodEnd())));
    rows.add(List.of("Declared at", CliTextDisplay.instant(registration.declaredAt())));
    return CliTextFormat.renderTitledBlock(
        taxRegistrationMutationTitle(outcome),
        CliReportRenderSupport.joinSections(
            CliTextFormat.renderKeyValueBlock(rows),
            CliReportRenderSupport.section(
                "Tax codes",
                CliTextFormat.renderAdaptiveTable(
                    CliReportRenderSupport.TEXT_TABLE_WIDTH,
                    List.of("Tax code", "Name", "Rate", "Inclusion", "Application"),
                    registration.taxCodes().stream()
                        .map(CliTaxRegistrationOutputRenderer::taxCodeRow)
                        .toList()))));
  }

  static String renderTaxRegistrationListText(TaxRegistrationPage page, boolean withContext) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            page.registrations().isEmpty()
                ? List.of(
                    List.of("Outcome", CliQueryScopeText.noMatchesLabel("tax registrations")),
                    List.of("Limit", Integer.toString(page.limit())),
                    List.of(
                        "Next cursor",
                        page.nextCursor().map(value -> value.wireValue()).orElse("(none)")))
                : List.of(
                    List.of(
                        "Returned registrations", Integer.toString(page.registrations().size())),
                    List.of("Limit", Integer.toString(page.limit())),
                    List.of(
                        "Next cursor",
                        page.nextCursor().map(value -> value.wireValue()).orElse("(none)"))));
    String registrations =
        page.registrations().isEmpty()
            ? ""
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of(
                    "Registration id",
                    "Name",
                    "Jurisdiction",
                    "Obligation",
                    "Due days",
                    "Payable",
                    "Recoverable",
                    "Tax codes"),
                page.registrations().stream()
                    .map(CliTaxRegistrationOutputRenderer::taxRegistrationRow)
                    .toList(),
                4);
    String context =
        CliTextFormat.renderKeyValueBlock(CliBookIdentityDisplay.contextRows(page.bookIdentity()));
    return CliTextFormat.renderTitledBlock(
        "Tax Registrations",
        CliReportRenderSupport.joinSections(
            summary,
            registrations,
            withContext ? CliReportRenderSupport.section("Context", context) : ""));
  }

  static String renderTaxRegistrationListCsv(TaxRegistrationPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "recordKind",
            "taxRegistrationId",
            "taxRegistrationName",
            "jurisdiction",
            "registrationNumber",
            "payableAccountCode",
            "recoverableAccountCode",
            "obligationFrequency",
            "dueDaysAfterPeriodEnd",
            "taxCodeIds",
            "taxCodeNames",
            "taxCodeRatesPercent",
            "taxCodeInclusionModes",
            "taxCodeApplicationKinds",
            "declaredAt",
            "message"),
        page.registrations().isEmpty()
            ? List.of(
                List.of(
                    CliCsvExportFamilies.TAX_REGISTRATIONS,
                    "tax-registrations:scope-empty",
                    TAX_REGISTRATIONS_RECORD_KIND,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    CliQueryScopeText.noMatchesLabel("tax registrations")))
            : page.registrations().stream()
                .map(CliTaxRegistrationOutputRenderer::taxRegistrationCsvRow)
                .toList());
  }

  private static List<String> taxCodeRow(TaxCodeDefinition taxCode) {
    return List.of(
        taxCode.taxCode().value(),
        taxCode.taxCodeName().value(),
        ratePercent(taxCode.rate()),
        CliTextDisplay.wireLabel(taxCode.inclusionMode().wireValue()),
        CliTextDisplay.wireLabel(taxCode.applicationKind().wireValue()));
  }

  private static List<String> taxRegistrationRow(DeclaredTaxRegistration registration) {
    return List.of(
        registration.taxRegistrationId().value(),
        registration.taxRegistrationName().value(),
        registration.jurisdiction().value(),
        CliTextDisplay.wireLabel(registration.obligationFrequency().wireValue()),
        Integer.toString(registration.dueDaysAfterPeriodEnd()),
        registration.payableAccountCode().value(),
        registration.recoverableAccountCode().value(),
        pipeJoined(registration.taxCodes().stream().map(code -> code.taxCode().value()).toList()));
  }

  private static List<String> taxRegistrationCsvRow(DeclaredTaxRegistration registration) {
    return List.of(
        CliCsvExportFamilies.TAX_REGISTRATIONS,
        "tax-registration:" + registration.taxRegistrationId().value(),
        TAX_REGISTRATIONS_RECORD_KIND,
        registration.taxRegistrationId().value(),
        registration.taxRegistrationName().value(),
        registration.jurisdiction().value(),
        registration.registrationNumber() == null ? "" : registration.registrationNumber().value(),
        registration.payableAccountCode().value(),
        registration.recoverableAccountCode().value(),
        registration.obligationFrequency().wireValue(),
        Integer.toString(registration.dueDaysAfterPeriodEnd()),
        pipeJoined(registration.taxCodes().stream().map(code -> code.taxCode().value()).toList()),
        pipeJoined(
            registration.taxCodes().stream().map(code -> code.taxCodeName().value()).toList()),
        pipeJoined(registration.taxCodes().stream().map(code -> ratePercent(code.rate())).toList()),
        pipeJoined(
            registration.taxCodes().stream()
                .map(code -> code.inclusionMode().wireValue())
                .toList()),
        pipeJoined(
            registration.taxCodes().stream()
                .map(code -> code.applicationKind().wireValue())
                .toList()),
        registration.declaredAt().toString(),
        "");
  }

  private static String taxRegistrationMutationTitle(String outcome) {
    return switch (outcome) {
      case "declared" -> "Tax Registration Declared";
      case "updated" -> "Tax Registration Updated";
      case "unchanged" -> "Tax Registration Unchanged";
      default -> "Tax Registration";
    };
  }

  private static String ratePercent(TaxRate rate) {
    return rate.canonicalPercent() + "%";
  }

  private static String pipeJoined(List<String> values) {
    return values.stream().collect(java.util.stream.Collectors.joining("|"));
  }
}
