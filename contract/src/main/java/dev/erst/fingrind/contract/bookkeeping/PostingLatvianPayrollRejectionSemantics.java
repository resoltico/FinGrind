package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import java.time.LocalDate;
import java.util.Objects;

/** Canonical public rejection vocabulary for Latvian monthly-payroll admission. */
public final class PostingLatvianPayrollRejectionSemantics {
  private PostingLatvianPayrollRejectionSemantics() {}

  /** Returns one refusal when the selected book cannot hold the context's EUR payroll facts. */
  public static PostingRejection.EntrySemanticsViolation requiresEurBook(
      String selectorValue, String functionalCurrency) {
    return violation(
            "latvian-payroll-requires-eur-book",
            "grossWages.currencyCode",
            "entryKind '%s' requires an EUR functional-currency book but this book uses '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(functionalCurrency, "functionalCurrency")))
        .toPostingRejection();
  }

  /** Returns one refusal for a concrete payroll fact outside the published profile. */
  public static PostingRejection.EntrySemanticsViolation profileNotAdmitted(
      String selectorValue, String field, String rejectedFact) {
    return violation(
            "latvian-payroll-profile-not-admitted",
            Objects.requireNonNull(field, "field"),
            "entryKind '%s' does not admit %s."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(rejectedFact, "rejectedFact")))
        .toPostingRejection();
  }

  /** Returns one refusal for one duplicate durable payroll-run identifier. */
  public static PostingRejection.EntrySemanticsViolation runIdAlreadyExists(
      String selectorValue, LatvianPayrollRunId payrollRunId) {
    return violation(
            "latvian-payroll-run-id-already-exists",
            "payrollRunId",
            "entryKind '%s' cannot create payrollRunId '%s' because that identifier already exists."
                .formatted(requireSelectorValue(selectorValue), requireRunId(payrollRunId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal for a second payroll run for the same employee-month. */
  public static PostingRejection.EntrySemanticsViolation employeeMonthAlreadyExists(
      String selectorValue,
      LatvianPayrollEmployeeReference employeeReference,
      LatvianPayrollMonth payrollMonth) {
    return violation(
            "latvian-payroll-employee-month-already-exists",
            "employeeReference",
            "entryKind '%s' cannot create a second payroll run for employeeReference '%s' in payrollMonth '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(employeeReference, "employeeReference").value(),
                    Objects.requireNonNull(payrollMonth, "payrollMonth").wireValue()))
        .toPostingRejection();
  }

  /** Returns one refusal when a payroll settlement references no retained payroll run. */
  public static PostingRejection.EntrySemanticsViolation runNotFound(
      String selectorValue, LatvianPayrollRunId payrollRunId) {
    return violation(
            "latvian-payroll-run-not-found",
            "payrollRunId",
            "entryKind '%s' cannot settle payrollRunId '%s' because no retained payroll run exists."
                .formatted(requireSelectorValue(selectorValue), requireRunId(payrollRunId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal when a compensating reversal has made the run inactive. */
  public static PostingRejection.EntrySemanticsViolation runReversed(
      String selectorValue, LatvianPayrollRunId payrollRunId) {
    return violation(
            "latvian-payroll-run-reversed",
            "payrollRunId",
            "entryKind '%s' cannot settle payrollRunId '%s' because the payroll run has been reversed."
                .formatted(requireSelectorValue(selectorValue), requireRunId(payrollRunId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal when settlement chronology would precede its originating payroll run. */
  public static PostingRejection.EntrySemanticsViolation settlementPrecedesRun(
      String selectorValue,
      LatvianPayrollRunId payrollRunId,
      LocalDate effectiveDate,
      LocalDate payrollRunEffectiveDate) {
    return violation(
            "latvian-payroll-settlement-precedes-run",
            "effectiveDate",
            "entryKind '%s' cannot settle payrollRunId '%s' on '%s' before its payroll effective date '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    requireRunId(payrollRunId).value(),
                    Objects.requireNonNull(effectiveDate, "effectiveDate"),
                    Objects.requireNonNull(payrollRunEffectiveDate, "payrollRunEffectiveDate")))
        .toPostingRejection();
  }

  /** Returns one refusal when the obligation already has one active exact settlement. */
  public static PostingRejection.EntrySemanticsViolation settlementAlreadyExists(
      String selectorValue,
      LatvianPayrollRunId payrollRunId,
      LatvianPayrollSettlementKind settlementKind) {
    return violation(
            "latvian-payroll-settlement-already-exists",
            "payrollRunId",
            "entryKind '%s' cannot create another active %s settlement for payrollRunId '%s'."
                .formatted(
                    requireSelectorValue(selectorValue),
                    Objects.requireNonNull(settlementKind, "settlementKind").wireValue(),
                    requireRunId(payrollRunId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal when a payroll-accrual reversal would orphan active exact settlements. */
  public static PostingRejection.EntrySemanticsViolation runReversalRequiresSettlementsReversed(
      LatvianPayrollRunId payrollRunId) {
    return violation(
            "latvian-payroll-run-reversal-requires-settlements-reversed",
            "reversal.priorPostingId",
            "Payroll run '%s' cannot be reversed while an active payroll settlement remains. Reverse every active payroll settlement before reversing the payroll run."
                .formatted(requireRunId(payrollRunId).value()))
        .toPostingRejection();
  }

  /** Returns one refusal when a payroll-run reversal would precede the retained run. */
  public static PostingRejection.EntrySemanticsViolation runReversalPrecedesRun(
      LatvianPayrollRunId payrollRunId,
      LocalDate reversalEffectiveDate,
      LocalDate payrollRunEffectiveDate) {
    return violation(
            "latvian-payroll-run-reversal-precedes-run",
            "effectiveDate",
            "Payroll run '%s' cannot be reversed on '%s' before its payroll effective date '%s'."
                .formatted(
                    requireRunId(payrollRunId).value(),
                    Objects.requireNonNull(reversalEffectiveDate, "reversalEffectiveDate"),
                    Objects.requireNonNull(payrollRunEffectiveDate, "payrollRunEffectiveDate")))
        .toPostingRejection();
  }

  /** Returns one refusal when a settlement reversal would precede its retained settlement. */
  public static PostingRejection.EntrySemanticsViolation settlementReversalPrecedesSettlement(
      LatvianPayrollRunId payrollRunId,
      LocalDate reversalEffectiveDate,
      LocalDate settlementEffectiveDate) {
    return violation(
            "latvian-payroll-settlement-reversal-precedes-settlement",
            "effectiveDate",
            "Payroll settlement for payrollRunId '%s' cannot be reversed on '%s' before its settlement effective date '%s'."
                .formatted(
                    requireRunId(payrollRunId).value(),
                    Objects.requireNonNull(reversalEffectiveDate, "reversalEffectiveDate"),
                    Objects.requireNonNull(settlementEffectiveDate, "settlementEffectiveDate")))
        .toPostingRejection();
  }

  private static Violation violation(String code, String field, String message) {
    return new Violation(code, field, message);
  }

  private static String requireSelectorValue(String selectorValue) {
    return PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
  }

  private static LatvianPayrollRunId requireRunId(LatvianPayrollRunId payrollRunId) {
    return Objects.requireNonNull(payrollRunId, "payrollRunId");
  }

  private record Violation(String code, String field, String message) {
    private PostingRejection.EntrySemanticsViolation toPostingRejection() {
      return new PostingRejection.EntrySemanticsViolation(code, field, message);
    }
  }
}
