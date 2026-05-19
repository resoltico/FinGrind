package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolOpenBookFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import java.util.Set;

/** Canonical field sets and root-shape rules for CLI JSON request documents. */
final class CliJsonRequestSchemas {
  static final String ROOT_DOCUMENT_MUST_BE_OBJECT = "Request JSON document must be an object.";
  static final Set<String> DECLARE_ACCOUNT_FIELDS =
      Set.of(
          ProtocolDeclareAccountFields.ACCOUNT_CODE,
          ProtocolDeclareAccountFields.ACCOUNT_NAME,
          ProtocolDeclareAccountFields.ACCOUNT_TYPE,
          ProtocolDeclareAccountFields.ACCOUNT_ROLE,
          ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE,
          ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
          ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION);
  static final Set<String> OPEN_BOOK_FIELDS =
      Set.of(
          ProtocolOpenBookFields.ENTITY_NAME,
          ProtocolOpenBookFields.ENTITY_FORM,
          ProtocolOpenBookFields.OWNER_MODEL,
          ProtocolOpenBookFields.REPORTING_OBLIGATION_STATUS,
          ProtocolOpenBookFields.BUSINESS_ACTIVITY_TAGS,
          ProtocolOpenBookFields.FUNCTIONAL_CURRENCY,
          ProtocolOpenBookFields.FISCAL_YEAR_START,
          ProtocolOpenBookFields.ACCOUNTING_BASIS);
  static final Set<String> POST_ENTRY_TOP_LEVEL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.topLevelFields());
  static final Set<String> PROVENANCE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.provenanceFields());
  static final Set<String> JOURNAL_LINE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.journalLineFields());
  static final Set<String> REVERSAL_FIELDS = Set.copyOf(ProtocolPostEntryFields.reversalFields());
  static final Set<String> LEDGER_PLAN_FIELDS = Set.copyOf(ProtocolLedgerPlanFields.planFields());
  static final Set<String> LEDGER_STEP_FIELDS = Set.copyOf(ProtocolLedgerPlanFields.stepFields());
  static final Set<String> LEDGER_QUERY_FIELDS = Set.copyOf(ProtocolLedgerPlanFields.queryFields());
  static final Set<String> LEDGER_ASSERTION_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.assertionFields());

  private CliJsonRequestSchemas() {}
}
