package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Durable, immutable settlement of one exact obligation created by a Latvian payroll run. */
public record LatvianPayrollSettlementRecord(
    LatvianPayrollSettlementKind settlementKind,
    LatvianPayrollRunId payrollRunId,
    PostingId originPostingId,
    LocalDate effectiveDate,
    AccountCode cashAccountCode,
    Optional<PostingId> reversalPostingId) {
  /** Validates retained settlement facts and the optional one-to-one compensating reversal. */
  public LatvianPayrollSettlementRecord {
    Objects.requireNonNull(settlementKind, "settlementKind");
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    Objects.requireNonNull(originPostingId, "originPostingId");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(cashAccountCode, "cashAccountCode");
    reversalPostingId =
        Optional.ofNullable(
            Objects.requireNonNull(reversalPostingId, "reversalPostingId").orElse(null));
  }

  /** Returns whether this durable settlement continues to discharge its payroll-run obligation. */
  public boolean active() {
    return reversalPostingId.isEmpty();
  }
}
