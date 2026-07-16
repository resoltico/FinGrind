package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Durable lifecycle state of one payroll settlement posting in the payroll register. */
public record LatvianPayrollSettlementStatus(
    LatvianPayrollSettlementKind settlementKind,
    PostingId postingId,
    LocalDate effectiveDate,
    Optional<PostingId> reversalPostingId) {
  /** Validates one immutable settlement lifecycle row. */
  public LatvianPayrollSettlementStatus {
    Objects.requireNonNull(settlementKind, "settlementKind");
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(reversalPostingId, "reversalPostingId");
  }

  /** Returns whether this retained settlement is currently active. */
  public boolean active() {
    return reversalPostingId.isEmpty();
  }
}
