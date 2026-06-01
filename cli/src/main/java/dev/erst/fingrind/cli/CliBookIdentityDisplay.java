package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared operator-facing identity rows for one accounting book. */
final class CliBookIdentityDisplay {
  private CliBookIdentityDisplay() {}

  static List<List<String>> detailRows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Entity", bookIdentity.entityName().value()));
    rows.add(List.of("Accounting profile", bookIdentity.accountingKernelProfileId().value()));
    rows.add(
        List.of(
            "Business activity",
            businessActivityTags(bookIdentity.entityProfile().businessActivityTags())));
    rows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    rows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    return List.copyOf(rows);
  }

  static List<List<String>> rows(BookIdentity bookIdentity) {
    return detailRows(bookIdentity);
  }

  static List<List<String>> summaryRows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return List.of(List.of("Book", summaryLine(bookIdentity)));
  }

  private static String businessActivityTags(List<BusinessActivityTag> businessActivityTags) {
    return businessActivityTags.isEmpty()
        ? "(none)"
        : businessActivityTags.stream()
            .map(BusinessActivityTag::value)
            .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String summaryLine(BookIdentity bookIdentity) {
    return bookIdentity.entityName().value()
        + " | "
        + bookIdentity.accountingKernelProfileId().value()
        + " | Currency "
        + bookIdentity.functionalCurrency().code()
        + " | FY "
        + bookIdentity.fiscalYearStart().wireValue();
  }
}
