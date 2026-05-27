package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
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
              "entryKind": "CASH_REVENUE",
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

  protected static String evidenceJson() {
    return """
        {
          "sourceDocuments": [
            {
              "sourceDocumentId": "document-1",
              "sourceDocumentType": "cash-receipt",
              "documentDate": "2026-04-07",
              "capturedAt": "2026-04-07T10:15:30Z",
              "storageLocator": "vault://fixtures/document-1",
              "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
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
                    "accountRole": "ORDINARY",
                    "accountNodeKind": "POSTABLE",
                    "financialPositionLineClassification": "CURRENT_ASSET"
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
                  "kind": "open-book",
                  "openBook": {
                    "entityName": "Acme Studio",
                    "businessActivityTags": ["translation-services"],
                    "functionalCurrency": "EUR",
                    "fiscalYearStart": "01-01"
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
    return declareAccountJson(
        accountCode,
        accountName,
        fixtureAccountTypeWireValue(normalBalance),
        fixtureAccountRoleWireValue(normalBalance));
  }

  protected static String declareAccountJson(
      String accountCode, String accountName, String accountType, String accountRole) {
    return declareAccountJson(
        accountCode,
        accountName,
        accountType,
        accountRole,
        fixtureFinancialPositionLineClassificationWireValue(accountType),
        fixtureProfitAndLossLineClassificationWireValue(accountType));
  }

  protected static String declareAccountJson(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification) {
    return """
            {
              "accountCode": "%s",
              "accountName": "%s",
              "accountType": "%s",
              "accountRole": "%s",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": %s,
              "profitAndLossLineClassification": %s
            }
            """
        .formatted(
            accountCode,
            accountName,
            accountType,
            accountRole,
            quotedOrNull(financialPositionLineClassification),
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

  private static String quotedOrNull(@Nullable String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static String fixtureAccountRoleWireValue(String normalBalance) {
    AccountType accountType = AccountType.fromWireValue(fixtureAccountTypeWireValue(normalBalance));
    NormalBalance parsedNormalBalance = NormalBalance.valueOf(normalBalance);
    return CliAccountingReportFixtureSupport.fixtureAccountRole(accountType, parsedNormalBalance)
        .wireValue();
  }
}
