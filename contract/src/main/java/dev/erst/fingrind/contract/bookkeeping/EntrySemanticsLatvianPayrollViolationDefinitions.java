package dev.erst.fingrind.contract.bookkeeping;

import java.util.List;

/** Latvian monthly-payroll profile and aggregate-admission violation definitions. */
final class EntrySemanticsLatvianPayrollViolationDefinitions {
  private EntrySemanticsLatvianPayrollViolationDefinitions() {}

  static List<EntrySemanticsViolationDefinition> definitions() {
    return List.of(
        definition(
            "latvian-payroll-requires-eur-book",
            "latvian-payroll-profile",
            "The Latvian monthly-payroll context requires an EUR functional-currency book.",
            "Use an EUR book for this context; do not translate the payroll through a generic journal."),
        definition(
            "latvian-payroll-profile-not-admitted",
            "latvian-payroll-profile",
            "The supplied payroll facts fall outside the published Latvian monthly-payroll profile.",
            "Use only the published period and worker assumptions, or record the case in an owned context that admits it."),
        definition(
            "latvian-payroll-run-id-already-exists",
            "latvian-payroll-lifecycle",
            "The selected payrollRunId already identifies one durable payroll run in this book.",
            "Reuse the existing run for readback or choose a new payrollRunId for a distinct employee-month run."),
        definition(
            "latvian-payroll-employee-month-already-exists",
            "latvian-payroll-lifecycle",
            "The selected employeeReference already has a durable payroll run for the selected payrollMonth.",
            "Use the existing payroll run; corrections require their own supported lifecycle rather than a second run."),
        definition(
            "latvian-payroll-run-not-found",
            "latvian-payroll-lifecycle",
            "The selected payrollRunId does not identify a retained payroll run in this book.",
            "Use a retained payrollRunId from this book before recording its exact settlement."),
        definition(
            "latvian-payroll-run-reversed",
            "latvian-payroll-lifecycle",
            "The selected payroll run has already been reversed and has no active obligation to settle.",
            "Use an active retained payroll run; do not settle a reversed accrual."),
        definition(
            "latvian-payroll-settlement-precedes-run",
            "latvian-payroll-lifecycle",
            "A payroll settlement cannot precede its retained payroll run.",
            "Use an effective date on or after the retained payroll run date."),
        definition(
            "latvian-payroll-settlement-already-exists",
            "latvian-payroll-lifecycle",
            "The selected payroll obligation already has an active exact settlement.",
            "Use the existing settlement or reverse it before recording a replacement."),
        definition(
            "latvian-payroll-run-reversal-requires-settlements-reversed",
            "latvian-payroll-lifecycle",
            "A payroll run with active settlements cannot be reversed.",
            "Reverse every active payroll settlement before reversing the payroll run."),
        definition(
            "latvian-payroll-settlement-reversal-precedes-settlement",
            "latvian-payroll-lifecycle",
            "A payroll settlement reversal cannot precede its retained settlement.",
            "Use a reversal effective date on or after the retained settlement date."),
        definition(
            "latvian-payroll-run-reversal-precedes-run",
            "latvian-payroll-lifecycle",
            "A payroll run reversal cannot precede its retained payroll run.",
            "Use a reversal effective date on or after the retained payroll run date."));
  }

  private static EntrySemanticsViolationDefinition definition(
      String code, String category, String description, String repair) {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
