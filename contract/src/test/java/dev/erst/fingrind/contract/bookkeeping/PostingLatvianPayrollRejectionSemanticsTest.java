package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Contract coverage for every deterministic Latvian payroll admission refusal. */
class PostingLatvianPayrollRejectionSemanticsTest {
  private static final String SELECTOR = "record-latvian-monthly-payroll";
  private static final LatvianPayrollRunId RUN_ID =
      new LatvianPayrollRunId("payroll-run-2026-07-employee-001");

  @Test
  void publishedRejectionsNameTheRejectedFactAndTheConcretePayrollLifecycleCause() {
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.requiresEurBook(SELECTOR, "USD"),
        "latvian-payroll-requires-eur-book",
        "grossWages.currencyCode",
        "USD");
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.profileNotAdmitted(
            SELECTOR, "grossWages", "gross wages above the supported threshold"),
        "latvian-payroll-profile-not-admitted",
        "grossWages",
        "gross wages above the supported threshold");
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.runIdAlreadyExists(SELECTOR, RUN_ID),
        "latvian-payroll-run-id-already-exists",
        "payrollRunId",
        RUN_ID.value());
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.employeeMonthAlreadyExists(
            SELECTOR,
            new LatvianPayrollEmployeeReference("employee-001"),
            LatvianPayrollMonth.parse("2026-07")),
        "latvian-payroll-employee-month-already-exists",
        "employeeReference",
        "employee-001");
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.runNotFound(SELECTOR, RUN_ID),
        "latvian-payroll-run-not-found",
        "payrollRunId",
        RUN_ID.value());
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.runReversed(SELECTOR, RUN_ID),
        "latvian-payroll-run-reversed",
        "payrollRunId",
        RUN_ID.value());
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.settlementPrecedesRun(
            SELECTOR, RUN_ID, LocalDate.parse("2026-07-30"), LocalDate.parse("2026-07-31")),
        "latvian-payroll-settlement-precedes-run",
        "effectiveDate",
        "2026-07-30");
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.settlementAlreadyExists(
            SELECTOR, RUN_ID, LatvianPayrollSettlementKind.NET_WAGES),
        "latvian-payroll-settlement-already-exists",
        "payrollRunId",
        "NET_WAGES");
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.runReversalRequiresSettlementsReversed(RUN_ID),
        "latvian-payroll-run-reversal-requires-settlements-reversed",
        "reversal.priorPostingId",
        RUN_ID.value());
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.runReversalPrecedesRun(
            RUN_ID, LocalDate.parse("2026-07-30"), LocalDate.parse("2026-07-31")),
        "latvian-payroll-run-reversal-precedes-run",
        "effectiveDate",
        "2026-07-30");
    assertViolation(
        PostingLatvianPayrollRejectionSemantics.settlementReversalPrecedesSettlement(
            RUN_ID, LocalDate.parse("2026-08-04"), LocalDate.parse("2026-08-05")),
        "latvian-payroll-settlement-reversal-precedes-settlement",
        "effectiveDate",
        "2026-08-04");
  }

  private static void assertViolation(
      PostingRejection.EntrySemanticsViolation violation,
      String expectedCode,
      String expectedField,
      String messageFragment) {
    assertEquals(expectedCode, violation.code());
    assertEquals(expectedField, violation.field());
    assertTrue(violation.message().contains(messageFragment), violation.message());
  }
}
