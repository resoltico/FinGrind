package dev.erst.fingrind.cli;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared test support for canonical JSON assertions and request/plan scaffold fixtures. */
class CliRequestDocumentFixtureSupport extends CliBookWorkflowFixtureSupport {
  private static final ObjectMapper TEST_JSON_MAPPER = new ObjectMapper();

  protected static String canonicalJsonText(ByteArrayOutputStream outputStream) {
    return canonicalJsonText(outputStream.toString(java.nio.charset.StandardCharsets.UTF_8));
  }

  protected static String canonicalJsonText(String document) {
    return TEST_JSON_MAPPER.writeValueAsString(TEST_JSON_MAPPER.readTree(document));
  }

  protected static void assertJsonContains(ByteArrayOutputStream outputStream, String fragment) {
    assertJsonContains(outputStream.toString(java.nio.charset.StandardCharsets.UTF_8), fragment);
  }

  protected static void assertJsonContains(String document, String fragment) {
    String canonical = canonicalJsonText(document);
    org.junit.jupiter.api.Assertions.assertTrue(canonical.contains(fragment), canonical);
  }

  protected static List<String> readTextArray(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.forEach(element -> values.add(element.stringValue()));
    return List.copyOf(values);
  }

  protected static String validRequestJson() {
    return """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              },
              "evidence": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1"
              }
            }
            """
        .formatted(evidenceJson().indent(14).stripLeading());
  }

  protected static String validRawJournalRequestJson() {
    return rawJournalRequestJson(
        "2026-04-07",
        "command-1",
        "idem-1",
        "document-1",
        "cash-receipt",
        journalLineJson("1000", "DEBIT", "1000"),
        journalLineJson("2000", "CREDIT", "1000"));
  }

  protected static String validAdmissibleRawJournalRequestJson() {
    return rawJournalRequestJson(
        "2026-04-07",
        "command-1",
        "idem-1",
        "document-1",
        "bank-deposit",
        journalLineJson("operating-bank", "DEBIT", "1000"),
        journalLineJson("1000", "CREDIT", "1000"));
  }

  protected static String rawJournalRequestJson(
      String effectiveDate,
      String commandId,
      String idempotencyKey,
      String sourceDocumentId,
      String sourceDocumentType,
      String... lines) {
    return """
            {
              "entryKind": "DIRECT_JOURNAL",
              "effectiveDate": "%s",
              "lines": [
            %s
              ],
              "evidence": {
                "sourceDocuments": [
                  {
                    "sourceDocumentId": "%s",
                    "sourceDocumentType": "%s",
                    "documentDate": "%s"
                  }
                ],
                "approvals": []
              },
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "%s",
                "idempotencyKey": "%s",
                "causationId": "cause-1"
              }
            }
            """
        .formatted(
            effectiveDate,
            String.join(",\n", lines),
            sourceDocumentId,
            sourceDocumentType,
            effectiveDate,
            commandId,
            idempotencyKey);
  }

  protected static String journalLineJson(String accountCode, String side, String minorUnits) {
    return """
                  {
                    "accountCode": "%s",
                    "side": "%s",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "%s"
                    }
                  }
            """
        .formatted(accountCode, side, minorUnits);
  }

  protected static String evidenceJson() {
    return """
        {
          "sourceDocuments": [
            {
              "sourceDocumentId": "document-1",
              "sourceDocumentType": "cash-receipt",
              "documentDate": "2026-04-07"
            }
          ],
          "approvals": []
        }
        """;
  }

  protected static String validPlanJson() {
    return """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "declare-cash",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountNodeKind": "POSTABLE",
                    "financialPositionLineClassification": "CURRENT_ASSET",
                    "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
                  }
                }
              ]
            }
            """;
  }

  protected static String openOnlyPlanJson() {
    return """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "ensure-book",
                  "ensureBook": {
                    "entityName": "Acme Studio",
                    "bookTemplateId": "OWNER_MANAGED_SERVICE",
                    "accountingBasis": "CASH",
                    "functionalCurrency": "EUR",
                    "fiscalYearStart": "01-01",
                    "bookStartEffectiveDate": "2026-01-01"
                  }
                }
              ]
            }
            """;
  }

  protected static String listAccountsPlanJson(int limit) {
    return """
            {
              "planId": "plan-list-accounts",
              "steps": [
                {
                  "stepId": "accounts",
                  "kind": "list-accounts",
                  "query": {
                    "limit": %d
                  }
                }
              ]
            }
            """
        .formatted(limit);
  }

  protected static String declareAccountJson(
      String accountCode, String accountName, String normalBalance) {
    return declareAccountJsonForAccountType(
        accountCode, accountName, fixtureAccountTypeWireValue(normalBalance));
  }

  protected static String declareAccountJsonForAccountType(
      String accountCode, String accountName, String accountType) {
    return declareAccountJson(
        accountCode,
        accountName,
        accountType,
        fixtureFinancialPositionLineClassificationWireValue(accountType),
        fixtureProfitAndLossLineClassificationWireValue(accountType));
  }

  protected static String declareAccountJson(
      String accountCode,
      String accountName,
      String accountType,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification) {
    return declareAccountJson(
        accountCode,
        accountName,
        accountType,
        financialPositionLineClassification,
        profitAndLossLineClassification,
        fixtureCashFlowAssetClassificationWireValue(
            accountType, financialPositionLineClassification));
  }

  protected static String declareAccountJson(
      String accountCode,
      String accountName,
      String accountType,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification,
      @Nullable String cashFlowAssetClassification) {
    return """
            {
              "accountCode": "%s",
              "accountName": "%s",
              "accountType": "%s",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": %s,
              "cashFlowAssetClassification": %s,
              "profitAndLossLineClassification": %s
            }
            """
        .formatted(
            accountCode,
            accountName,
            accountType,
            quotedOrNull(financialPositionLineClassification),
            quotedOrNull(cashFlowAssetClassification),
            quotedOrNull(profitAndLossLineClassification));
  }

  private static String fixtureAccountTypeWireValue(String normalBalance) {
    return switch (normalBalance) {
      case "DEBIT" -> "ASSET";
      case "CREDIT" -> "REVENUE";
      default -> "ASSET";
    };
  }

  private static @Nullable String fixtureFinancialPositionLineClassificationWireValue(
      String accountType) {
    return switch (accountType) {
      case "ASSET" -> "CURRENT_ASSET";
      case "LIABILITY" -> "CURRENT_LIABILITY";
      case "EQUITY" -> "OTHER_EQUITY";
      case "REVENUE", "EXPENSE" -> null;
      default ->
          throw new IllegalArgumentException("Unsupported fixture accountType: " + accountType);
    };
  }

  private static @Nullable String fixtureProfitAndLossLineClassificationWireValue(
      String accountType) {
    return switch (accountType) {
      case "REVENUE" -> "OPERATING_REVENUE";
      case "EXPENSE" -> "OPERATING_EXPENSE";
      case "ASSET", "LIABILITY", "EQUITY" -> null;
      default ->
          throw new IllegalArgumentException("Unsupported fixture accountType: " + accountType);
    };
  }

  private static @Nullable String fixtureCashFlowAssetClassificationWireValue(
      String accountType, @Nullable String financialPositionLineClassification) {
    if (!"ASSET".equals(accountType)) {
      return null;
    }
    if (financialPositionLineClassification == null) {
      return "CASH_AND_CASH_EQUIVALENT";
    }
    return switch (financialPositionLineClassification) {
      case "CURRENT_ASSET" -> "CASH_AND_CASH_EQUIVALENT";
      case "NONCURRENT_ASSET" -> "NON_CASH";
      default -> "CASH_AND_CASH_EQUIVALENT";
    };
  }

  private static String quotedOrNull(@Nullable String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }
}
