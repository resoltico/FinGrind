package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.PostingPageCursor;
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
              "effectiveDate": "2026-04-07",
              "lines": [
                {
                  "accountCode": "1000",
                  "side": "DEBIT",
                  "currencyCode": "EUR",
                  "amount": "10.00"
                },
                {
                  "accountCode": "2000",
                  "side": "CREDIT",
                  "currencyCode": "EUR",
                  "amount": "10.00"
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
        .formatted(reversalBlock);
  }

  static String validLegacyCorrectionRequestJson() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
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
        """;
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
              "kind": "open-book"
            },
            {
              "stepId": "declare",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "normalBalance": "DEBIT"
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
                "currencyCode": "EUR",
                "netAmount": "10.00",
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
            validPostingCursor());
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
