package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical owner of FinGrind's published capability scope and operative boundaries. */
public final class CapabilityCatalog {
  private static final List<CapabilityCatalogEntry> ENTRIES =
      List.of(
          new CapabilityCatalogEntry(
              "business-event-posting",
              "Typed business events provide settled and on-credit sales and purchases, receipts and payments, inventory capitalization, relief, write-down, shrinkage, and count events, tax application through the declared selector, reversals, opening positions, owner contributions and withdrawals, interim result sweep, and fiscal-year close.",
              CapabilityStatus.IMPLEMENTED,
              null),
          new CapabilityCatalogEntry(
              "direct-journal",
              "The raw DIRECT_JOURNAL path remains available for ordinary journals.",
              CapabilityStatus.PARTIAL,
              "Direct journals remain available, but they cannot touch inventory accounts."),
          new CapabilityCatalogEntry(
              "receivables-and-payables",
              "On-credit sales and purchases plus receipts and payments are available.",
              CapabilityStatus.PARTIAL,
              "On-credit sales and purchases plus receipts and payments are available only on accrual-basis books; invoice lifecycle and settlement allocation are excluded."),
          new CapabilityCatalogEntry(
              "inventory",
              "Inventory capitalization, relief, write-down, shrinkage, and count events are available.",
              CapabilityStatus.PARTIAL,
              "Inventory is available only for the owner-managed trading template."),
          new CapabilityCatalogEntry(
              "tax",
              "Tax selection and obligation reporting are available.",
              CapabilityStatus.PARTIAL,
              "Tax selection and obligation reporting are available; tax determination and filing doctrine are excluded."),
          new CapabilityCatalogEntry(
              "foreign-exchange",
              "Foreign-exchange facts may accompany eligible business events and direct journals, and typed foreign-currency receivable origination and settlement derive realized gain or loss while journal lines remain in the selected functional currency.",
              CapabilityStatus.PARTIAL,
              "Only one foreign-currency receivable and one active settlement per retained obligation are available; rate sourcing, remeasurement, translation, hedging, and mixed-currency journal lines are excluded."),
          new CapabilityCatalogEntry(
              "accrual-cutoffs",
              "Accrual-basis prepayments, deferred revenue, accrued expenses, manual recognition or settlement applications, compensating reversals, and accrual-cutoff schedule reporting are available.",
              CapabilityStatus.PARTIAL,
              "Applications are operator-authored exact amounts within each declared lifecycle; automatic allocation, tax and foreign-exchange composition, and revision-addressable report replay are excluded."),
          new CapabilityCatalogEntry(
              "fixed-assets-and-depreciation",
              "Fixed-asset capitalization, executor-resolved straight-line depreciation, disposal, compensating reversal, and fixed-asset register reporting are available.",
              CapabilityStatus.PARTIAL,
              "The context uses a functional-currency cost model with one straight-line schedule per asset; leases, impairment, revaluation, tax depreciation, and statutory external reporting are excluded."),
          new CapabilityCatalogEntry(
              "financing",
              "Borrowing, principal repayment, interest accrual, interest payment, compensating reversal, and financing-register reporting are available.",
              CapabilityStatus.PARTIAL,
              "The context records nominal principal and exact accrued interest only; leases, effective-interest amortization, fair-value measurement, covenants, tax withholding, and lender integrations are excluded."),
          new CapabilityCatalogEntry(
              "latvian-monthly-payroll",
              "One narrow Latvian 2026 monthly-employment payroll profile derives supported payroll accruals and their net-wage and state-remittance settlements, with payroll-register reporting.",
              CapabilityStatus.PARTIAL,
              "Only the named EUR 2026 ordinary-employee profile is available; other worker profiles, periods, jurisdictions, legal-status determination, and statutory filing are excluded."),
          new CapabilityCatalogEntry(
              "external-financial-reporting",
              "Standards-oriented external cash-flow presentation, OCI or comprehensive-income presentation, and note or disclosure packages are excluded.",
              CapabilityStatus.EXCLUDED,
              null),
          new CapabilityCatalogEntry(
              "jurisdictional-bookkeeping-overlays",
              "Jurisdiction-specific chart templates, filing exports, and close doctrines are excluded.",
              CapabilityStatus.EXCLUDED,
              null));

  private CapabilityCatalog() {}

  /** Returns every public capability fact in stable documentation order. */
  public static List<CapabilityCatalogEntry> entries() {
    return ENTRIES;
  }
}
