package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.AccrualCutoffKind;
import java.util.Objects;

/** Executor-resolved ledger accounts for one cut-off recognition or accrued-expense settlement. */
public record ResolvedAccrualCutoffApplication(
    AccrualCutoffKind accrualCutoffKind,
    AccrualCutoffApplicationKind applicationKind,
    AccountCode debitAccountCode,
    AccountCode creditAccountCode) {
  /** Validates one executor-resolved cut-off application. */
  public ResolvedAccrualCutoffApplication {
    Objects.requireNonNull(accrualCutoffKind, "accrualCutoffKind");
    Objects.requireNonNull(applicationKind, "applicationKind");
    Objects.requireNonNull(debitAccountCode, "debitAccountCode");
    Objects.requireNonNull(creditAccountCode, "creditAccountCode");
    if (debitAccountCode.equals(creditAccountCode)) {
      throw new IllegalArgumentException(
          "Resolved accrual cut-off application accounts must be distinct.");
    }
  }
}
