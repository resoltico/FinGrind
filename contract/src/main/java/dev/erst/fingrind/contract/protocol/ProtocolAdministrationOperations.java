package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical administration-operation registry for the public FinGrind protocol catalog. */
final class ProtocolAdministrationOperations {
  private ProtocolAdministrationOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolBookMaintenanceOperations.generateBookKeyFileOperation(),
        openBookOperation(),
        ProtocolBookMaintenanceOperations.rekeyBookOperation(),
        ProtocolBookMaintenanceOperations.backupBookOperation(),
        ProtocolBookMaintenanceOperations.restoreBookOperation(),
        ProtocolBookMaintenanceOperations.inspectRekeyRollbackOperation(),
        ProtocolBookMaintenanceOperations.deleteRekeyRollbackOperation(),
        ProtocolBookMaintenanceOperations.restoreRekeyRollbackOperation(),
        declareAccountOperation(),
        declareTaxRegistrationOperation(),
        ProtocolPeriodCloseOperations.interimResultSweepOperation(),
        ProtocolPeriodCloseOperations.fiscalYearCloseOperation());
  }

  private static ProtocolOperation openBookOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.OPEN_BOOK,
        OperationCategory.ADMINISTRATION,
        "Open Book",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.ENTITY_NAME + " <text>",
            ProtocolOptions.BOOK_TEMPLATE_ID
                + " <"
                + String.join("|", dev.erst.fingrind.core.BookTemplateId.wireValues())
                + ">",
            ProtocolOptions.ACCOUNTING_BASIS
                + " <"
                + String.join("|", dev.erst.fingrind.core.AccountingBasis.wireValues())
                + ">",
            "["
                + ProtocolOptions.INVENTORY_COSTING
                + " <"
                + String.join("|", dev.erst.fingrind.core.InventoryCostingDoctrine.wireValues())
                + ">] (required for OWNER_MANAGED_TRADING)",
            ProtocolOptions.FUNCTIONAL_CURRENCY + " <currency-code>",
            ProtocolOptions.FISCAL_YEAR_START + " <MM-DD>",
            "[" + ProtocolOptions.TIGHTEN_PARENTS + "]",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Initialize a new book file with the canonical schema, selected seed template, explicit accounting basis, and the inventory costing doctrine required by trading templates.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s \"Acme Studio\" %s OWNER_MANAGED_SERVICE %s CASH %s EUR %s 01-01"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ENTITY_NAME,
                        ProtocolOptions.BOOK_TEMPLATE_ID,
                        ProtocolOptions.ACCOUNTING_BASIS,
                        ProtocolOptions.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.FISCAL_YEAR_START)),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s OWNER_MANAGED_SERVICE %s CASH %s EUR %s 01-01 %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.ENTITY_NAME,
                        ProtocolOptions.BOOK_TEMPLATE_ID,
                        ProtocolOptions.ACCOUNTING_BASIS,
                        ProtocolOptions.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.FISCAL_YEAR_START,
                        ProtocolOptions.BOOK_PASSPHRASE_PROMPT)),
            ProtocolExampleStep.command(
                "cat ./secrets/acme.book-key | fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s OWNER_MANAGED_SERVICE %s CASH %s EUR %s 01-01 %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.ENTITY_NAME,
                        ProtocolOptions.BOOK_TEMPLATE_ID,
                        ProtocolOptions.ACCOUNTING_BASIS,
                        ProtocolOptions.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.FISCAL_YEAR_START,
                        ProtocolOptions.BOOK_PASSPHRASE_STDIN))));
  }

  private static ProtocolOperation declareAccountOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.DECLARE_ACCOUNT,
        OperationCategory.ADMINISTRATION,
        "Declare Account",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.REQUEST_FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Declare or reactivate an account in the selected book.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-supplemental-cash-reserve.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE)),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-supplemental-misc-revenue.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))));
  }

  private static ProtocolOperation declareTaxRegistrationOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.DECLARE_TAX_REGISTRATION,
        OperationCategory.ADMINISTRATION,
        "Declare Tax Registration",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.REQUEST_FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Declare or update an owned tax registration in the selected book.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-tax-registration.json"
                    .formatted(
                        OperationId.DECLARE_TAX_REGISTRATION.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.REQUEST_FILE))));
  }
}
