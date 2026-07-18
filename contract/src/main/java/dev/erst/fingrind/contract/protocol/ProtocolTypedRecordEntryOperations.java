package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.stream.Stream;

/** Typed business-entry operations grouped by the context that owns their meaning. */
final class ProtocolTypedRecordEntryOperations {
  private ProtocolTypedRecordEntryOperations() {}

  static List<ProtocolOperation> operations() {
    return Stream.of(
            standardSaleOperations(),
            inventoryOperations(),
            accrualCutoffOperations(),
            latvianPayrollOperations(),
            fixedAssetOperations(),
            financingOperations(),
            realizedForeignExchangeOperations(),
            standardLedgerOperations())
        .flatMap(List::stream)
        .toList();
  }

  private static List<ProtocolOperation> standardSaleOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_SALE_SETTLED,
            "Record Settled Sale",
            "Commit a settled sale entry into the selected SQLite book.",
            "Settled-sale request scaffolds publish the cash, revenue, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_SALE_ON_CREDIT,
            "Record Sale On Credit",
            "Commit a sale-on-credit entry into the selected SQLite book.",
            "Sale-on-credit request scaffolds publish the receivable, revenue, amount, evidence, and provenance fields."));
  }

  private static List<ProtocolOperation> inventoryOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_PURCHASE_SETTLED,
            "Record Settled Purchase",
            "Commit a settled inventory purchase entry into the selected trading-template SQLite book.",
            "Settled-purchase request scaffolds publish inventory, cash, quantity, unitCost, optional tax and FX, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_PURCHASE_ON_CREDIT,
            "Record Purchase On Credit",
            "Commit a purchase-on-credit inventory entry into the selected trading-template SQLite book.",
            "Purchase-on-credit request scaffolds publish inventory, payable, quantity, unitCost, optional tax and FX, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
            "Record Settled Inventory Capitalization",
            "Commit a settled landed-cost capitalization into an existing inventory pool.",
            "Settled inventory-capitalization scaffolds publish inventory, cash, pre-VAT amount, optional tax and FX, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
            "Record Inventory Capitalization On Credit",
            "Commit a payable landed-cost capitalization into an existing inventory pool.",
            "Inventory-capitalization-on-credit scaffolds publish inventory, payable, pre-VAT amount, optional tax and FX, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_INVENTORY_WRITE_DOWN,
            "Record Inventory Write-Down",
            "Commit a carrying-cost write-down against an existing inventory pool.",
            "Inventory write-down scaffolds publish inventory, writeDownLossAccountCode, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_INVENTORY_SHRINKAGE,
            "Record Inventory Shrinkage",
            "Commit a quantity shrinkage adjustment with executor-derived carrying cost.",
            "Inventory shrinkage scaffolds publish inventory, shrinkageLossAccountCode, quantity, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_INVENTORY_COUNT_INCREASE,
            "Record Inventory Count Increase",
            "Commit a count-discovered inventory increase at an exact per-unit carrying cost.",
            "Inventory count-increase scaffolds publish inventory, countGainAccountCode, quantity, unitCost, evidence, and provenance fields."));
  }

  private static List<ProtocolOperation> accrualCutoffOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_PREPAYMENT,
            "Record Prepayment",
            "Commit a cash-funded prepayment with an inclusive recognition interval.",
            "Prepayment scaffolds publish accrualCutoffId, prepaid asset, expense, cash, amount, recognitionInterval, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_DEFERRED_REVENUE,
            "Record Deferred Revenue",
            "Commit a cash-funded deferred-revenue liability with an inclusive recognition interval.",
            "Deferred-revenue scaffolds publish accrualCutoffId, cash, deferred revenue, revenue, amount, recognitionInterval, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_ACCRUED_EXPENSE,
            "Record Accrued Expense",
            "Commit an unpaid accrued expense.",
            "Accrued-expense scaffolds publish accrualCutoffId, expense, accrued-expense liability, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
            "Record Accrual Cut-Off Recognition",
            "Recognize a permitted amount from an existing prepayment or deferred-revenue balance.",
            "Recognition scaffolds publish accrualCutoffId, amount, evidence, and provenance fields; FinGrind resolves the account pair from the aggregate."),
        recordEntryOperation(
            OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
            "Record Accrued Expense Settlement",
            "Settle a permitted amount of an existing accrued-expense liability.",
            "Settlement scaffolds publish accrualCutoffId, cash, amount, evidence, and provenance fields; FinGrind resolves the liability account from the aggregate."));
  }

  private static List<ProtocolOperation> latvianPayrollOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
            "Record Latvian Monthly Payroll",
            "Commit an executor-resolved Latvian 2026 ordinary monthly-payroll accrual.",
            "Payroll request scaffolds publish the opaque employee reference, payroll month, "
                + ProtocolBusinessEventFields.LatvianPayroll.TAX_BOOK_HELD_AT_EMPLOYER
                + ": true admission fact, "
                + ProtocolBusinessEventFields.LatvianPayroll.DEPENDANT_COUNT
                + ": 0 admission fact, six account roles, gross wages, evidence, and provenance; FinGrind derives social contributions, personal income tax, and net wages."),
        recordEntryOperation(
            OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
            "Record Latvian Payroll Net-Wage Settlement",
            "Settle the exact net-wage obligation of the active retained Latvian payroll run.",
            "Net-wage settlement scaffolds publish payrollRunId, cash, evidence, and provenance; FinGrind derives the liability account and amount from the run."),
        recordEntryOperation(
            OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
            "Record Latvian Payroll State Remittance",
            "Remit the exact state obligation of the active retained Latvian payroll run.",
            "State-remittance scaffolds publish payrollRunId, cash, evidence, and provenance; FinGrind derives the three liability accounts and exact remittance amount from the run."));
  }

  private static List<ProtocolOperation> fixedAssetOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
            "Record Fixed-Asset Capitalization",
            "Capitalizes a fixed asset with its owned useful-life and depreciation facts.",
            "Fixed-asset capitalization scaffolds publish fixedAssetId, asset, cash or payable, amount, useful-life, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
            "Record Fixed-Asset Depreciation",
            "Records the admissible periodic depreciation amount for a retained fixed asset.",
            "Fixed-asset depreciation scaffolds publish fixedAssetId, depreciation expense, accumulated depreciation, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_FIXED_ASSET_DISPOSAL,
            "Record Fixed-Asset Disposal",
            "Disposes a retained fixed asset and preserves its lifecycle lineage.",
            "Fixed-asset disposal scaffolds publish fixedAssetId, proceeds, disposal accounts, evidence, and provenance fields; FinGrind resolves retained carrying facts."));
  }

  private static List<ProtocolOperation> financingOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_FINANCING_BORROWING,
            "Record Financing Borrowing",
            "Records a borrowing and opens its retained financing arrangement.",
            "Financing borrowing scaffolds publish financingArrangementId, cash, liability, principal amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
            "Record Financing Principal Repayment",
            "Repays principal against a retained financing arrangement.",
            "Principal-repayment scaffolds publish financingArrangementId, cash, amount, evidence, and provenance fields; FinGrind resolves the liability account from the arrangement."),
        recordEntryOperation(
            OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
            "Record Financing Interest Accrual",
            "Accrues interest against a retained financing arrangement.",
            "Interest-accrual scaffolds publish financingArrangementId, interest expense, interest payable, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
            "Record Financing Interest Payment",
            "Pays accrued interest against a retained financing arrangement.",
            "Interest-payment scaffolds publish financingArrangementId, cash, amount, evidence, and provenance fields; FinGrind resolves the liability account from the arrangement."));
  }

  private static List<ProtocolOperation> realizedForeignExchangeOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
            "Record Foreign-Currency Obligation",
            "Records a foreign-currency receivable with its functional-currency carrying amount.",
            "Foreign-currency obligation scaffolds publish foreignCurrencyObligationId, receivable, revenue, currency, foreign amount, functional carrying amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
            "Record Realized Foreign-Exchange Settlement",
            "Settles a retained foreign-currency obligation and derives the realized gain or loss.",
            "Foreign-exchange settlement scaffolds publish foreignCurrencyObligationId, cash, gain and loss accounts, functional settlement amount, evidence, and provenance fields."));
  }

  private static List<ProtocolOperation> standardLedgerOperations() {
    return List.of(
        recordEntryOperation(
            OperationId.RECORD_EXPENSE_SETTLED,
            "Record Settled Expense",
            "Commit a settled expense entry into the selected SQLite book.",
            "Settled-expense request scaffolds publish the expense, cash, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_EXPENSE_ON_CREDIT,
            "Record Expense On Credit",
            "Commit an expense-on-credit entry into the selected SQLite book.",
            "Expense-on-credit request scaffolds publish the expense, payable, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_RECEIPT,
            "Record Receipt",
            "Commit a trade-receivable settlement entry into the selected SQLite book.",
            "Receipt request scaffolds publish the cash, receivable, amount, optional settlementAdjunct, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_PAYMENT,
            "Record Payment",
            "Commit a trade-payable settlement entry into the selected SQLite book.",
            "Payment request scaffolds publish the payable, cash, amount, optional settlementAdjunct, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_OWNER_CONTRIBUTION,
            "Record Owner Contribution",
            "Commit an owner-contribution entry into the selected SQLite book.",
            "Owner-contribution request scaffolds publish the contribution-first request language with cash, equity, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_OWNER_WITHDRAWAL,
            "Record Owner Withdrawal",
            "Commit an owner-withdrawal entry into the selected SQLite book.",
            "Owner-withdrawal request scaffolds publish the withdrawal-first request language with equity, cash, amount, evidence, and provenance fields."),
        recordEntryOperation(
            OperationId.RECORD_OPENING_POSITION,
            "Record Opening Position",
            "Commit an opening-position entry into the selected SQLite book.",
            "Opening-position request scaffolds publish openingBalances, evidence, and provenance fields. Inventory opening balances carry exact quantity alongside carrying cost."),
        recordEntryOperation(
            OperationId.RECORD_REVERSAL,
            "Record Reversal",
            "Commit a reversal entry into the selected SQLite book.",
            "Reversal request scaffolds publish the reversal-first request language with reversal target facts, evidence, and provenance fields."));
  }

  private static ProtocolOperation recordEntryOperation(
      OperationId operationId, String title, String summary, String scaffoldNote) {
    return ProtocolOperationDefinitions.operation(
        operationId,
        OperationCategory.WRITE,
        title,
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        summary,
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s > request.json"
                    .formatted(
                        OperationId.PRINT_REQUEST_TEMPLATE.wireName(), operationId.wireName())),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                    .formatted(
                        operationId.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE)),
            ProtocolExampleStep.note("request.json starts as a scaffold. " + scaffoldNote)));
  }
}
