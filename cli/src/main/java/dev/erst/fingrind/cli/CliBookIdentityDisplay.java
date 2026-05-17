package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.TaxCodeDefinition;
import dev.erst.fingrind.core.TaxRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared human-facing identity rows for one accounting book. */
final class CliBookIdentityDisplay {
  private CliBookIdentityDisplay() {}

  static List<List<String>> rows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Entity", bookIdentity.entityName().value()));
    rows.add(
        List.of(
            "Entity profile",
            CliHumanDisplay.wireLabel(bookIdentity.entityProfile().entityForm().wireValue())
                + " / "
                + CliHumanDisplay.wireLabel(
                    bookIdentity.entityProfile().ownerModel().wireValue())));
    rows.add(
        List.of(
            "Reporting profile",
            CliHumanDisplay.wireLabel(
                    bookIdentity.entityProfile().reportingObligationStatus().wireValue())
                + " / "
                + CliHumanDisplay.wireLabel(
                    bookIdentity.entityProfile().taxRegistrationStatus().wireValue())));
    rows.add(
        List.of(
            "Business activity",
            businessActivityTags(bookIdentity.entityProfile().businessActivityTags())));
    rows.add(
        List.of("Tax registrations", taxRegistrations(bookIdentity.taxProfile().registrations())));
    rows.add(
        List.of(
            "Tax code definitions",
            taxCodeDefinitions(bookIdentity.taxProfile().taxCodeDefinitions())));
    rows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    rows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    rows.add(
        List.of(
            "Accounting basis",
            CliHumanDisplay.wireLabel(bookIdentity.accountingBasis().wireValue())));
    return List.copyOf(rows);
  }

  private static String businessActivityTags(List<BusinessActivityTag> businessActivityTags) {
    return businessActivityTags.isEmpty()
        ? "(none)"
        : businessActivityTags.stream()
            .map(BusinessActivityTag::value)
            .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String taxRegistrations(List<TaxRegistration> registrations) {
    return registrations.isEmpty()
        ? "(none)"
        : registrations.stream()
            .map(
                registration ->
                    registration.jurisdictionCode().value()
                        + " / "
                        + registration.registrationId().value()
                        + " / "
                        + CliHumanDisplay.wireLabel(registration.filingFrequency().wireValue()))
            .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String taxCodeDefinitions(List<TaxCodeDefinition> taxCodeDefinitions) {
    return taxCodeDefinitions.isEmpty()
        ? "(none)"
        : taxCodeDefinitions.stream()
            .map(
                definition ->
                    definition.taxCode().value()
                        + " / "
                        + definition.displayName().value()
                        + " / "
                        + percentage(definition.rate().basisPoints())
                        + " / "
                        + CliHumanDisplay.wireLabel(definition.pricingMode().wireValue()))
            .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String percentage(int basisPoints) {
    return "%d.%02d%%".formatted(basisPoints / 100, Math.abs(basisPoints % 100));
  }
}
