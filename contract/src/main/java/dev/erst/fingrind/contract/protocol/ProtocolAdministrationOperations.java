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
        declareAccountOperation(),
        amendAccountOperation(),
        retireAccountOperation(),
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
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.BookDefinition.ENTITY_NAME + " <text>",
            ProtocolOptions.BookDefinition.TEMPLATE_ID
                + " <"
                + String.join("|", dev.erst.fingrind.core.BookTemplateId.wireValues())
                + ">",
            ProtocolOptions.BookDefinition.ACCOUNTING_BASIS
                + " <"
                + String.join("|", dev.erst.fingrind.core.AccountingBasis.wireValues())
                + ">",
            "["
                + ProtocolOptions.BookDefinition.INVENTORY_COSTING
                + " <"
                + String.join("|", dev.erst.fingrind.core.InventoryCostingDoctrine.wireValues())
                + ">] (required for OWNER_MANAGED_TRADING)",
            ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY + " <currency-code>",
            ProtocolOptions.BookDefinition.FISCAL_YEAR_START + " <MM-DD>",
            ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID
                + " <uuid> (repeat one through five aligned founder triples)",
            ProtocolOptions.Attestation.FOUNDER_KEY_FILE
                + " <path> (repeat one through five aligned founder triples)",
            ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE
                + " <path> (repeat one through five aligned founder triples)",
            "[" + ProtocolOptions.BookDefinition.TIGHTEN_PARENTS + "]",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Initialize a new book file with the canonical schema, selected seed template, explicit accounting basis, and the inventory costing doctrine required by trading templates.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s \"Acme Studio\" %s OWNER_MANAGED_SERVICE %s CASH %s EUR %s 01-01 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/founder.fgatk %s ./secrets/founder.passphrase"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.BookDefinition.ENTITY_NAME,
                        ProtocolOptions.BookDefinition.TEMPLATE_ID,
                        ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
                        ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
                        ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                        ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
                        ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE)),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s OWNER_MANAGED_SERVICE %s CASH %s EUR %s 01-01 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/founder.fgatk %s ./secrets/founder.passphrase %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolOptions.BookDefinition.ENTITY_NAME,
                        ProtocolOptions.BookDefinition.TEMPLATE_ID,
                        ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
                        ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
                        ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                        ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
                        ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
                        ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT)),
            ProtocolExampleStep.command(
                "cat ./secrets/acme.book-key | fingrind %s %s ./books/acme.sqlite %s \"Acme Studio\" %s OWNER_MANAGED_SERVICE %s CASH %s EUR %s 01-01 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/founder.fgatk %s ./secrets/founder.passphrase %s"
                    .formatted(
                        OperationId.OPEN_BOOK.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolOptions.BookDefinition.ENTITY_NAME,
                        ProtocolOptions.BookDefinition.TEMPLATE_ID,
                        ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
                        ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
                        ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
                        ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                        ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
                        ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
                        ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN))));
  }

  private static ProtocolOperation declareAccountOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.DECLARE_ACCOUNT,
        OperationCategory.ADMINISTRATION,
        "Declare Account",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Declare or reactivate an account in the selected book.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-supplemental-cash-reserve.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE)),
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-account-supplemental-misc-revenue.json"
                    .formatted(
                        OperationId.DECLARE_ACCOUNT.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE))));
  }

  private static ProtocolOperation declareTaxRegistrationOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.DECLARE_TAX_REGISTRATION,
        OperationCategory.ADMINISTRATION,
        "Declare Tax Registration",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Declare or update an owned tax registration using already-declared payable and recoverable accounts; this command never creates accounts implicitly.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./declare-tax-registration.json"
                    .formatted(
                        OperationId.DECLARE_TAX_REGISTRATION.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE))));
  }

  private static ProtocolOperation amendAccountOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.AMEND_ACCOUNT,
        OperationCategory.ADMINISTRATION,
        "Amend Account",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Replace the definition of a never-posted, unreferenced account without erasing its identity or lifecycle history.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./amend-account.json"
                    .formatted(
                        OperationId.AMEND_ACCOUNT.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE))));
  }

  private static ProtocolOperation retireAccountOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.RETIRE_ACCOUNT,
        OperationCategory.ADMINISTRATION,
        "Retire Account",
        List.of(),
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.Request.FILE + " <path|->",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Retire a zero-balance account from new ordinary authored postings while preserving its ledger history and admitting historical reversals.",
        List.of(
            ProtocolExampleStep.command(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./retire-account.json"
                    .formatted(
                        OperationId.RETIRE_ACCOUNT.wireName(),
                        ProtocolBookAccessOptions.BOOK_FILE,
                        ProtocolBookAccessOptions.BOOK_KEY_FILE,
                        ProtocolOptions.Request.FILE))));
  }
}
