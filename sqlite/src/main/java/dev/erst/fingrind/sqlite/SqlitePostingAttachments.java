package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Fully loaded posting attachments required to materialize one committed posting. */
record SqlitePostingAttachments(
    List<JournalLine> lines,
    AccountingEvidence evidence,
    @Nullable AppliedTax appliedTax,
    @Nullable ForeignExchangeDetails foreignExchangeDetails) {
  SqlitePostingAttachments {
    lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    Objects.requireNonNull(evidence, "evidence");
  }
}
