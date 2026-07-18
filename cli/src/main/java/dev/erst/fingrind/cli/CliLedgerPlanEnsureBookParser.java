package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.ProtocolBookRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolOpenBookFields;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.InventoryCostingDoctrine;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Parses the doctrine-bearing ensure-book payload nested in one ledger-plan step. */
final class CliLedgerPlanEnsureBookParser {
  private CliLedgerPlanEnsureBookParser() {}

  static OpenBookCommand read(ObjectNode ensureBookNode) {
    rejectUnexpectedFields(
        ensureBookNode, "ensureBook", ProtocolBookRequestFieldSets.openBookFields());
    return new OpenBookCommand(
        new BookIdentity(
            new EntityProfile(
                CliOptionValues.parseBookEntityNameOption(
                    requiredText(ensureBookNode, ProtocolOpenBookFields.ENTITY_NAME),
                    "ensureBook." + ProtocolOpenBookFields.ENTITY_NAME)),
            BookDoctrines.forTemplateAndBasis(
                CliOptionValues.parseBookTemplateIdOption(
                    requiredText(ensureBookNode, ProtocolOpenBookFields.BOOK_TEMPLATE_ID),
                    "ensureBook." + ProtocolOpenBookFields.BOOK_TEMPLATE_ID),
                CliOptionValues.parseAccountingBasisOption(
                    requiredText(ensureBookNode, ProtocolOpenBookFields.ACCOUNTING_BASIS),
                    "ensureBook." + ProtocolOpenBookFields.ACCOUNTING_BASIS),
                inventoryCostingDoctrine(ensureBookNode)),
            CliOptionValues.parseCurrencyUnitOption(
                requiredText(ensureBookNode, ProtocolOpenBookFields.FUNCTIONAL_CURRENCY),
                "ensureBook." + ProtocolOpenBookFields.FUNCTIONAL_CURRENCY),
            CliOptionValues.parseFiscalYearStartOption(
                requiredText(ensureBookNode, ProtocolOpenBookFields.FISCAL_YEAR_START),
                "ensureBook." + ProtocolOpenBookFields.FISCAL_YEAR_START),
            CliOptionValues.parseLocalDateOption(
                requiredText(ensureBookNode, ProtocolOpenBookFields.BOOK_START_EFFECTIVE_DATE),
                "ensureBook." + ProtocolOpenBookFields.BOOK_START_EFFECTIVE_DATE)));
  }

  private static @Nullable InventoryCostingDoctrine inventoryCostingDoctrine(
      ObjectNode ensureBookNode) {
    return optionalText(ensureBookNode, ProtocolOpenBookFields.INVENTORY_COSTING)
        .map(
            rawValue ->
                CliOptionValues.parseInventoryCostingDoctrineOption(
                    rawValue, "ensureBook." + ProtocolOpenBookFields.INVENTORY_COSTING))
        .orElse(null);
  }
}
