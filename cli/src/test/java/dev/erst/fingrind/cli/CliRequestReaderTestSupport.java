package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.core.PostingId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared fixtures and JSON samples for split CLI request reader tests. */
class CliRequestReaderTestSupport extends CliFixtureSupport {
  @Override
  protected Path writeRequest(String payload) throws IOException {
    return writeNamedRequest("request.json", payload);
  }

  @Override
  protected Path writeNamedRequest(String fileName, String payload) throws IOException {
    Path requestFile = tempDirectory.resolve(fileName);
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  static String validRequestJson(boolean includeReversal) {
    if (includeReversal) {
      return """
              {
                "entryKind": "REVERSAL",
                "effectiveDate": "2026-04-07",
                "evidence": %s,
                "provenance": {
                  "actorId": "actor-1",
                  "actorType": "AGENT",
                  "commandId": "command-1",
                  "idempotencyKey": "idem-1",
                  "causationId": "cause-1",
                  "correlationId": "corr-1"
                },
                "reversal": {
                  "priorPostingId": "posting-0",
                  "reason": "operator reversal"
                }
              }
              """
          .formatted(evidenceJson().indent(14).stripLeading());
    }
    return """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": %s,
              "evidence": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1",
                "correlationId": "corr-1"
              }
            }
            """
        .formatted(eurMoneyJson("1000"), evidenceJson().indent(14).stripLeading());
  }

  static String validLegacyCorrectionRequestJson() {
    return """
        {
          "entryKind": "REVERSAL",
          "effectiveDate": "2026-04-07",
          "evidence": %s,
          "correction": {
            "kind": "AMENDMENT",
            "priorPostingId": "posting-0"
          },
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1",
            "reason": "operator correction"
          }
        }
        """
        .formatted(evidenceJson().indent(10).stripLeading());
  }

  static String withEvidence(String json) {
    if (json.contains("\"evidence\"")) {
      return json;
    }
    String evidenceField =
        """
            "evidence": %s
            """
            .formatted(evidenceJson().indent(12).stripLeading());
    int provenanceIndex = json.indexOf("\"provenance\"");
    if (provenanceIndex >= 0) {
      return json.substring(0, provenanceIndex)
          + evidenceField
          + ",\n"
          + json.substring(provenanceIndex);
    }
    int closingBraceIndex = json.lastIndexOf('}');
    if (closingBraceIndex < 0) {
      throw new IllegalArgumentException("JSON fixture must end with a closing brace.");
    }
    String prefix = json.substring(0, closingBraceIndex).stripTrailing();
    String suffix = json.substring(closingBraceIndex);
    String separator = prefix.endsWith("{") ? "" : ",";
    return prefix + separator + "\n  " + evidenceField + "\n" + suffix;
  }

  static LedgerAssertion assertionAt(LedgerPlan plan, int index) {
    return ((LedgerStep.Assert) plan.steps().get(index)).assertion();
  }

  static String validLedgerPlanJson() {
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
                "fiscalYearStart": "01-01"
              }
            },
            {
              "stepId": "declare",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "accountType": "ASSET",
                "accountNodeKind": "POSTABLE",
                "financialPositionLineClassification": "CURRENT_ASSET",
                "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
              }
            },
            {
              "stepId": "preflight",
              "kind": "preflight-entry",
              "posting": %s
            },
            {
              "stepId": "post",
              "kind": "record-sale-settled",
              "posting": %s
            },
            {
              "stepId": "inspect",
              "kind": "inspect-book"
            },
            {
              "stepId": "list-accounts",
              "kind": "list-accounts",
              "query": {
                "limit": 25,
                "cursor": "%s"
              }
            },
            {
              "stepId": "get-posting",
              "kind": "get-posting",
              "postingId": "posting-1"
            },
            {
              "stepId": "list-postings",
              "kind": "list-postings",
              "query": {
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30",
                "limit": 25,
                "cursor": "%s"
              }
            },
            {
              "stepId": "account-balance",
              "kind": "account-balance",
              "query": {
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30"
              }
            },
            {
              "stepId": "assert-declared",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-declared",
                "accountCode": "1000"
              }
            },
            {
              "stepId": "assert-active",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-active",
                "accountCode": "1000"
              }
            },
            {
              "stepId": "assert-posting",
              "kind": "assert",
              "assertion": {
                "kind": "assert-posting-exists",
                "postingId": "posting-1"
              }
            },
            {
              "stepId": "assert-balance",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30",
                "netAmount": %s,
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            validRequestJson(false),
            validRequestJson(false),
            validAccountCursor(),
            validPostingCursor(),
            eurMoneyJson("1000"));
  }

  static String moneyJson(String currencyCode, String minorUnits) {
    return """
        {
          "currencyCode": "%s",
          "minorUnits": "%s"
        }
        """
        .formatted(currencyCode, minorUnits)
        .indent(18)
        .stripLeading();
  }

  static String eurMoneyJson(String minorUnits) {
    return moneyJson("EUR", minorUnits);
  }

  static String standardBalancedLinesJson() {
    return journalLinesJson(
        "1000", "DEBIT", eurMoneyJson("1000"), "2000", "CREDIT", eurMoneyJson("1000"));
  }

  static String journalLinesJson(
      String firstAccountCode,
      String firstSide,
      String firstMoneyJson,
      String secondAccountCode,
      String secondSide,
      String secondMoneyJson) {
    return """
        [
          {
            "accountCode": "%s",
            "side": "%s",
            "amount": %s
          },
          {
            "accountCode": "%s",
            "side": "%s",
            "amount": %s
          }
        ]
        """
        .formatted(
            firstAccountCode,
            firstSide,
            firstMoneyJson,
            secondAccountCode,
            secondSide,
            secondMoneyJson)
        .indent(18)
        .stripLeading();
  }

  static String singleJournalLineJson(String accountCode, String side, String moneyJson) {
    return """
        [
          {
            "accountCode": "%s",
            "side": "%s",
            "amount": %s
          }
        ]
        """
        .formatted(accountCode, side, moneyJson)
        .indent(18)
        .stripLeading();
  }

  static String validAccountCursor() {
    return new AccountPageCursor(new dev.erst.fingrind.core.AccountCode("1000")).wireValue();
  }

  static String validPostingCursor() {
    return new PostingPageCursor(
            java.time.LocalDate.parse("2026-04-15"),
            java.time.Instant.parse("2026-04-15T10:15:30Z"),
            new PostingId("posting-5"))
        .wireValue();
  }
}
