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
    String reversalBlock =
        includeReversal
            ? """
                ,
                "reversal": {
                  "priorPostingId": "posting-0",
                  "reason": "operator reversal"
                }
              """
            : "";
    return """
            {
              "postingKind": "STANDARD",
              "effectiveDate": "2026-04-07",
              "lines": [
                {
                  "accountCode": "1000",
                  "side": "DEBIT",
                  "amount": %s
                },
                {
                  "accountCode": "2000",
                  "side": "CREDIT",
                  "amount": %s
                }
              ],
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1",
                "correlationId": "corr-1"
              }
            %s
            }
            """
        .formatted(eurMoneyJson("1000"), eurMoneyJson("1000"), reversalBlock);
  }

  static String validLegacyCorrectionRequestJson() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": %s
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": %s
            }
          ],
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
        .formatted(eurMoneyJson("1000"), eurMoneyJson("1000"));
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
              "kind": "open-book",
              "openBook": {
                "entityName": "Acme Studio",
                "entityForm": "COMPANY",
                "ownerModel": "MULTI_OWNER",
                "reportingObligationStatus": "INTERNAL_MANAGEMENT_ONLY",
                "taxRegistrationStatus": "UNSPECIFIED",
                "businessActivityTags": ["translation-services"],
                "functionalCurrency": "EUR",
                "fiscalYearStart": "01-01",
                "accountingBasis": "ACCRUAL"
              }
            },
            {
              "stepId": "declare",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "accountType": "ASSET",
                "accountRole": "ORDINARY",
                "financialPositionLineClassification": "CURRENT_ASSET"
              }
            },
            {
              "stepId": "preflight",
              "kind": "preflight-entry",
              "posting": %s
            },
            {
              "stepId": "post",
              "kind": "post-entry",
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
