package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
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
            "Entity form",
            CliHumanDisplay.wireLabel(bookIdentity.entityProfile().entityForm().wireValue())));
    rows.add(
        List.of(
            "Owner model",
            CliHumanDisplay.wireLabel(bookIdentity.entityProfile().ownerModel().wireValue())));
    rows.add(
        List.of(
            "Reporting obligation",
            CliHumanDisplay.wireLabel(
                bookIdentity.entityProfile().reportingObligationStatus().wireValue())));
    rows.add(
        List.of(
            "Business activity",
            businessActivityTags(bookIdentity.entityProfile().businessActivityTags())));
    rows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    rows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    rows.add(
        List.of(
            "Accounting basis",
            CliHumanDisplay.wireLabel(bookIdentity.accountingBasis().wireValue())));
    return List.copyOf(rows);
  }

  static List<List<String>> summaryRows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Entity", bookIdentity.entityName().value()));
    rows.add(
        List.of(
            "Profile",
            String.join(
                " | ",
                CliHumanDisplay.wireLabel(bookIdentity.entityProfile().entityForm().wireValue()),
                CliHumanDisplay.wireLabel(bookIdentity.entityProfile().ownerModel().wireValue()),
                CliHumanDisplay.wireLabel(
                    bookIdentity.entityProfile().reportingObligationStatus().wireValue()))));
    rows.add(
        List.of(
            "Accounting",
            String.join(
                " | ",
                bookIdentity.functionalCurrency().code(),
                "FY start " + bookIdentity.fiscalYearStart().wireValue(),
                CliHumanDisplay.wireLabel(bookIdentity.accountingBasis().wireValue()))));
    if (!bookIdentity.entityProfile().businessActivityTags().isEmpty()) {
      rows.add(
          List.of(
              "Business activity",
              businessActivityTags(bookIdentity.entityProfile().businessActivityTags())));
    }
    return List.copyOf(rows);
  }

  private static String businessActivityTags(List<BusinessActivityTag> businessActivityTags) {
    return businessActivityTags.isEmpty()
        ? "(none)"
        : businessActivityTags.stream()
            .map(BusinessActivityTag::value)
            .collect(java.util.stream.Collectors.joining(", "));
  }
}
