package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Temporal-scope facts published on the request surface. */
final class RequestSurfaceTemporalContracts {
  private RequestSurfaceTemporalContracts() {}

  static List<RequestSurfaceFacts.TemporalScopeFacts> temporalScopes() {
    return List.of(
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.RANGED_FILTER,
            List.of(
                ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
                ProtocolOptions.DateRange.EFFECTIVE_DATE_TO),
            "Effective date range",
            "Effective date from",
            "Effective date to",
            "Optional lower and upper effective-date filters over committed postings. Omit the lower boundary to start at book start; omit the upper boundary to end at the current book horizon.",
            "selected-date",
            "book-start",
            "current-book-horizon",
            "latest-posting-effective-date",
            "no-postings"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.BOUNDED_PERIOD,
            List.of(ProtocolOptions.DateRange.PERIOD_START, ProtocolOptions.DateRange.PERIOD_END),
            "Reporting period",
            "Period start",
            "Period end",
            "Explicit closed reporting window. Both boundaries must be supplied, and neither boundary falls back to book start or the current book horizon.",
            "selected-date",
            "selected-date",
            "selected-date",
            "selected-date",
            "selected-date"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.THROUGH_DATE,
            List.of(ProtocolOptions.DateRange.THROUGH),
            "Reporting period",
            "Derived period start",
            "Through date",
            "Inclusive through date. FinGrind derives the contiguous sweep window from book start in the selected book or, after a sweep is recorded, from the live transferred-through horizon.",
            "selected-date",
            "derived-start-date",
            "selected-date",
            "selected-date",
            "selected-date"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.FISCAL_YEAR_LABEL,
            List.of(ProtocolOptions.DateRange.YEAR),
            "Reporting period",
            "Fiscal year start",
            "Fiscal year end",
            "Fiscal year label. FinGrind derives the full close window from the selected book fiscal-year start.",
            "selected-fiscal-year-label",
            "selected-fiscal-year-label",
            "selected-fiscal-year-label",
            "selected-fiscal-year-label",
            "selected-fiscal-year-label"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.AS_OF_DATE,
            List.of(ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF),
            "As of",
            "As of",
            "As of",
            "Point-in-time effective-date cutoff. Supply --effective-date-as-of to pin that cutoff explicitly, or omit it to resolve the current book horizon for the selected report.",
            "selected-date",
            "book-start",
            "current-book-horizon",
            "latest-posting-effective-date",
            "no-postings"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.INVENTORY_AS_OF_DATE,
            List.of(ProtocolOptions.DateRange.AS_OF),
            "Inventory valuation as of",
            "As of",
            "As of",
            "Point-in-time effective-date cutoff for canonical inventory-ledger replay. Supply --as-of to pin the cutoff explicitly, or omit it to include every durable inventory movement in the selected book.",
            "selected-date",
            "book-start",
            "current-inventory-ledger-horizon",
            "latest-inventory-movement-effective-date",
            "no-inventory-movements"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.ACCRUAL_CUTOFF_AS_OF_DATE,
            List.of(ProtocolOptions.DateRange.AS_OF),
            "Accrual cut-offs as of",
            "As of",
            "As of",
            "Point-in-time effective-date cutoff for durable accrual cut-off aggregates. Supply --as-of to pin the cutoff explicitly, or omit it to include every durable cut-off lifecycle fact in the selected book.",
            "selected-date",
            "book-start",
            "current-accrual-cut-off-horizon",
            "latest-accrual-cut-off-application-effective-date",
            "no-accrual-cut-offs"));
  }

  static List<RequestSurfaceFacts.CommandTemporalScopeFacts> commandTemporalScopes() {
    return List.of(
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.LIST_POSTINGS, TemporalScopeArchetype.RANGED_FILTER),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.ACCOUNT_LEDGER, TemporalScopeArchetype.RANGED_FILTER),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.RANGED_FILTER),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.INTERIM_RESULT_SWEEP, TemporalScopeArchetype.THROUGH_DATE),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.FISCAL_YEAR_CLOSE, TemporalScopeArchetype.FISCAL_YEAR_LABEL),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.PERIOD_SUMMARY, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.INCOME_STATEMENT, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.CASH_FLOW_STATEMENT, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.CHANGES_IN_EQUITY, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.TAX_OBLIGATION, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.TRIAL_BALANCE, TemporalScopeArchetype.AS_OF_DATE),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.FINANCIAL_POSITION, TemporalScopeArchetype.AS_OF_DATE),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.INVENTORY_VALUATION, TemporalScopeArchetype.INVENTORY_AS_OF_DATE),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.ACCRUAL_CUTOFF_SCHEDULE, TemporalScopeArchetype.ACCRUAL_CUTOFF_AS_OF_DATE));
  }
}
