package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds operator-facing preparation, request, and exit guidance for command help. */
final class CliDiscoveryCommandGuidance {
  private static final int HELP_STRUCTURE_LABEL_WIDTH_CAP = 32;
  private static final Set<OperationId> BOOK_READ_OPERATIONS =
      Set.of(
          OperationId.ACCOUNT_BALANCE,
          OperationId.ACCOUNT_LEDGER,
          OperationId.TRIAL_BALANCE,
          OperationId.FINANCIAL_POSITION,
          OperationId.INVENTORY_VALUATION,
          OperationId.INCOME_STATEMENT,
          OperationId.CHANGES_IN_EQUITY,
          OperationId.PERIOD_SUMMARY,
          OperationId.TAX_OBLIGATION,
          OperationId.LIST_POSTINGS,
          OperationId.GET_POSTING);

  private static final Set<OperationId> ENTRY_REQUEST_OPERATIONS =
      Set.of(
          OperationId.POST_ENTRY,
          OperationId.PREFLIGHT_ENTRY,
          OperationId.RECORD_SALE_SETTLED,
          OperationId.RECORD_SALE_ON_CREDIT,
          OperationId.RECORD_PURCHASE_SETTLED,
          OperationId.RECORD_PURCHASE_ON_CREDIT,
          OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
          OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
          OperationId.RECORD_INVENTORY_WRITE_DOWN,
          OperationId.RECORD_INVENTORY_SHRINKAGE,
          OperationId.RECORD_INVENTORY_COUNT_INCREASE,
          OperationId.RECORD_EXPENSE_SETTLED,
          OperationId.RECORD_EXPENSE_ON_CREDIT,
          OperationId.RECORD_RECEIPT,
          OperationId.RECORD_PAYMENT,
          OperationId.RECORD_OWNER_CONTRIBUTION,
          OperationId.RECORD_OWNER_WITHDRAWAL,
          OperationId.RECORD_OPENING_POSITION,
          OperationId.RECORD_REVERSAL);
  private static final Set<OperationId> TEMPORAL_SCOPE_OPERATIONS =
      Set.of(
          OperationId.ACCOUNT_BALANCE,
          OperationId.ACCOUNT_LEDGER,
          OperationId.TRIAL_BALANCE,
          OperationId.FINANCIAL_POSITION,
          OperationId.INVENTORY_VALUATION,
          OperationId.INCOME_STATEMENT,
          OperationId.CHANGES_IN_EQUITY,
          OperationId.PERIOD_SUMMARY,
          OperationId.TAX_OBLIGATION,
          OperationId.LIST_POSTINGS,
          OperationId.INTERIM_RESULT_SWEEP,
          OperationId.FISCAL_YEAR_CLOSE);

  private CliDiscoveryCommandGuidance() {}

  static String renderPreparation(OperationId operationId) {
    List<List<String>> rows = preparationRows(operationId);
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section("Preparation", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String renderRequestGuidance(HelpDescriptor helpDescriptor, OperationId operationId) {
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return renderPostingRequestGuidance(helpDescriptor, operationId);
    }
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return renderDeclareAccountRequestGuidance(helpDescriptor);
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return renderDeclareTaxRegistrationRequestGuidance(helpDescriptor);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return renderLedgerPlanRequestGuidance(helpDescriptor);
    }
    return "";
  }

  static String renderExitBehavior(List<ExitCodeDescriptor> exitCodes) {
    List<List<String>> rows =
        exitCodes.stream()
            .<List<String>>map(
                exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
            .toList();
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section("Exit Behavior", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String renderTemporalScopeGuidance(OperationId operationId) {
    if (!TEMPORAL_SCOPE_OPERATIONS.contains(operationId)) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Temporal Scope",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Scope kind", CliTemporalScopeText.scopeKind(operationId)),
                List.of(
                    "Boundary flags",
                    String.join(", ", CliTemporalScopeText.optionNames(operationId))),
                List.of(
                    "Boundary behavior", CliTemporalScopeText.boundarySemantics(operationId)))));
  }

  static CliDiscoveryCommandHelpSupport.SupportEntry requestTemplateHint(OperationId operationId) {
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template",
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName());
    }
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template",
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + OperationId.DECLARE_ACCOUNT.wireName());
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template",
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + OperationId.DECLARE_TAX_REGISTRATION.wireName());
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template", CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE));
    }
    return CliDiscoveryCommandHelpSupport.SupportEntry.note("Request template", "(not applicable)");
  }

  private static List<List<String>> preparationRows(OperationId operationId) {
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return List.of(
          List.of("Needs", initializedBookText()),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(OperationId.LIST_ACCOUNTS)));
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return List.of(
          List.of("Needs", initializedBookText()),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(
                  OperationId.LIST_TAX_REGISTRATIONS)));
    }
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return List.of(
          List.of("Needs", initializedBookWithDeclaredAccountsText()),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(OperationId.TRIAL_BALANCE)));
    }
    if (BOOK_READ_OPERATIONS.contains(operationId)) {
      return List.of(List.of("Needs", initializedBookText()));
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return List.of(
          List.of("Needs", "A ledger plan JSON document passed through --request-file."),
          List.of(
              "Starter file", CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
    }
    return List.of();
  }

  private static String initializedBookText() {
    return "A protected book initialized with "
        + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
        + ".";
  }

  private static String initializedBookWithDeclaredAccountsText() {
    return "A protected book initialized with "
        + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
        + " and every referenced account declared.";
  }

  private static String renderPostingRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().bookkeepingEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return "";
    }
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        helpDescriptor.requestShapes().bookkeepingEntry();
    ContractTemplates.PostingRequestTemplateDescriptor requestTemplate =
        helpDescriptor.requestTemplate();
    return CliDiscoveryTextSupport.joinSections(
        CliDiscoveryTextSupport.section(
            "Input Contract",
            requestFileGuidance(
                "Pass a JSON object through --request-file <path|->.",
                CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                    + " "
                    + operationId.wireName())),
        CliDiscoveryTextSupport.section(
            "Posting model",
            CliDiscoveryPostingModelGuidance.renderPostingModel(postEntryShape, requestTemplate)),
        CliDiscoveryTextSupport.section(
            "Entry semantics",
            CliDiscoveryPostingModelGuidance.renderEntrySemantics(
                postEntryShape, requestTemplate)));
  }

  private static String renderDeclareAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Input Contract",
        requestFileGuidance(
            "Pass a JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()));
  }

  private static String renderDeclareTaxRegistrationRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareTaxRegistration() == null
        || helpDescriptor.declareTaxRegistrationTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Input Contract",
        requestFileGuidance(
            "Pass a JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_TAX_REGISTRATION.wireName()));
  }

  private static String renderLedgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return "";
    }
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape =
        helpDescriptor.requestShapes().ledgerPlan();
    ContractTemplates.PostingRequestTemplateDescriptor postingTemplate =
        helpDescriptor.planTemplate().canonicalPostingTemplate();
    return CliDiscoveryTextSupport.joinSections(
        CliDiscoveryTextSupport.section(
            "Input Contract",
            requestFileGuidance(
                "Pass a ledger plan JSON object through --request-file <path|->.",
                CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE))),
        CliDiscoveryTextSupport.section(
            "Plan structure", renderLedgerPlanStructure(ledgerPlanShape, postingTemplate)));
  }

  private static String requestFileGuidance(String introduction, String shortcutCommand) {
    return String.join(
        System.lineSeparator() + System.lineSeparator(),
        CliTextFormat.wrap(introduction, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH),
        "Starter file command"
            + System.lineSeparator()
            + CliTextFormat.renderLiteralBlock(List.of(shortcutCommand), "$ "));
  }

  private static String renderLedgerPlanStructure(
      ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    List<List<String>> rows = new ArrayList<>();
    appendTopLevelLedgerPlanRows(rows, ledgerPlanShape.topLevelFields(), "steps");
    appendLedgerPlanRows(rows, ledgerPlanShape.stepFields(), "steps[].");
    appendLedgerPlanRows(rows, ledgerPlanShape.queryFields(), "steps[].query.");
    appendLedgerPlanRows(rows, ledgerPlanShape.assertionFields(), "steps[].assertion.");
    rows.addAll(
        CliDiscoveryPostingModelGuidance.postingModelRows(
            ledgerPlanShape.postingModel(), postingTemplate, "steps[].posting.", true));
    String primaryBlock =
        CliTextFormat.renderKeyValueBlock(
            List.copyOf(rows),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH,
            HELP_STRUCTURE_LABEL_WIDTH_CAP);
    List<List<String>> supplementalPostingRows =
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
            ledgerPlanShape.postingModel(), postingTemplate, "steps[].posting.");
    if (supplementalPostingRows.isEmpty()) {
      return primaryBlock;
    }
    return primaryBlock
        + System.lineSeparator()
        + System.lineSeparator()
        + CliTextFormat.renderKeyValueBlock(
            supplementalPostingRows,
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH,
            HELP_STRUCTURE_LABEL_WIDTH_CAP);
  }

  private static void appendTopLevelLedgerPlanRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String arrayFieldName) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      String fieldPath = arrayFieldName.equals(field.name()) ? field.name() + "[]" : field.name();
      rows.add(List.of(fieldPath, field.description()));
    }
  }

  private static void appendLedgerPlanRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      rows.add(List.of(prefix + field.name(), field.description()));
    }
  }
}
