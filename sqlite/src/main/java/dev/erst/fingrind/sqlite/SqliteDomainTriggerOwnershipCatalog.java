package dev.erst.fingrind.sqlite;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Names the executor context that owns each business-rule trigger in the canonical schema.
 *
 * <p>SQLite enforces these rules after the executor has admitted a command. The catalog is checked
 * against the schema so a new business trigger cannot become the only owner of accounting meaning.
 */
final class SqliteDomainTriggerOwnershipCatalog {
  /** Executor context that owns the business meaning enforced by one durable trigger. */
  enum Owner {
    /** Declared-account hierarchy, lifecycle, and contra-account invariants. */
    ACCOUNT_REGISTRY,
    /** Tax-registration account-binding invariants. */
    TAX_REGISTRATION,
    /** Shared posting-origin and journal-line admission invariants. */
    POSTING_ADMISSION,
    /** Inventory movement, ordering, and materialized-state invariants. */
    INVENTORY_COSTING,
    /** Interim-result and fiscal-year close invariants. */
    PERIOD_CLOSE,
    /** Accrual cut-off origin and application invariants. */
    ACCRUAL_CUTOFF,
    /** Fixed-asset lifecycle and compensating-reversal invariants. */
    FIXED_ASSETS,
    /** Financing-arrangement lifecycle and compensating-reversal invariants. */
    FINANCING,
    /** Foreign-currency obligation and settlement invariants. */
    REALIZED_FOREIGN_EXCHANGE,
    /** Latvian payroll-run and settlement invariants. */
    LATVIAN_PAYROLL
  }

  private SqliteDomainTriggerOwnershipCatalog() {}

  static Map<String, Owner> domainOwners() {
    return Stream.of(
            triggerOwners(
                Owner.ACCOUNT_REGISTRY,
                "account_validate_parent_on_insert",
                "account_validate_contra_on_insert",
                "account_validate_lifecycle_update",
                "account_validate_contra_on_update",
                "account_validate_parent_on_update"),
            triggerOwners(
                Owner.TAX_REGISTRATION,
                "tax_registration_validate_accounts_on_insert",
                "tax_registration_validate_accounts_on_update"),
            triggerOwners(
                Owner.POSTING_ADMISSION,
                "posting_fact_validate_opening_balance_window_on_insert",
                "posting_fact_validate_closed_period_on_insert",
                "posting_fact_validate_generated_close_provenance_on_insert",
                "posting_applied_tax_validate_origin_on_insert",
                "posting_foreign_exchange_validate_origin_on_insert",
                "journal_line_validate_active_account_on_insert",
                "journal_line_validate_functional_currency_on_insert",
                "journal_line_validate_opening_balance_account_type_on_insert"),
            triggerOwners(
                Owner.INVENTORY_COSTING,
                "inventory_movement_validate_inventory_account_on_insert",
                "inventory_movement_validate_account_horizon_on_insert",
                "inventory_movement_validate_account_sequence_on_insert",
                "inventory_movement_validate_typed_posting_origin_on_insert",
                "inventory_movement_validate_opening_on_insert",
                "inventory_on_hand_validate_inventory_account_on_insert",
                "inventory_on_hand_validate_inventory_account_on_update"),
            triggerOwners(
                Owner.PERIOD_CLOSE,
                "interim_result_sweep_validate_result_holding_account_on_insert",
                "interim_result_sweep_validate_contiguous_horizon_on_insert",
                "interim_result_sweep_posting_validate_interim_result_sweep_posting_on_insert",
                "fiscal_year_close_validate_target_accounts_on_insert",
                "fiscal_year_close_posting_validate_fiscal_year_close_posting_on_insert",
                "audit_event_validate_close_operation_order_on_insert"),
            triggerOwners(
                Owner.ACCRUAL_CUTOFF,
                "accrual_cutoff_validate_origin_on_insert",
                "accrual_cutoff_application_validate_on_insert"),
            triggerOwners(
                Owner.FIXED_ASSETS,
                "fixed_asset_validate_origin_on_insert",
                "fixed_asset_application_validate_on_insert",
                "fixed_asset_reversal_validate_on_insert",
                "fixed_asset_application_reversal_validate_on_insert"),
            triggerOwners(
                Owner.FINANCING,
                "financing_arrangement_validate_origin_on_insert",
                "financing_application_validate_on_insert",
                "financing_arrangement_reversal_validate_on_insert",
                "financing_application_reversal_validate_on_insert"),
            triggerOwners(
                Owner.REALIZED_FOREIGN_EXCHANGE,
                "foreign_currency_obligation_validate_origin_on_insert",
                "foreign_currency_obligation_settlement_validate_on_insert",
                "foreign_currency_obligation_reversal_validate_on_insert",
                "foreign_currency_obligation_settlement_reversal_validate_on_insert"),
            triggerOwners(
                Owner.LATVIAN_PAYROLL,
                "latvian_payroll_run_validate_on_insert",
                "latvian_payroll_run_reversal_validate_on_insert",
                "latvian_payroll_settlement_validate_on_insert",
                "latvian_payroll_settlement_reversal_validate_on_insert"))
        .flatMap(triggerOwnerEntries -> triggerOwnerEntries)
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  static boolean isDurabilityOnlyTrigger(String triggerName) {
    return triggerName.endsWith("_reject_update") || triggerName.endsWith("_reject_delete");
  }

  static Set<String> domainTriggerNames() {
    return domainOwners().keySet();
  }

  private static Stream<Map.Entry<String, Owner>> triggerOwners(
      Owner owner, String... triggerNames) {
    return Arrays.stream(triggerNames).map(triggerName -> Map.entry(triggerName, owner));
  }
}
