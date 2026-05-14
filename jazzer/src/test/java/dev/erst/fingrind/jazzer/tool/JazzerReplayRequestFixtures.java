package dev.erst.fingrind.jazzer.tool;

final class JazzerReplayRequestFixtures {
  private JazzerReplayRequestFixtures() {}

  static String basicValidRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1"
          }
        }
        """;
  }

  static String invalidForbiddenRecordedAtRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-4",
            "actorType": "AGENT",
            "commandId": "command-4",
            "idempotencyKey": "idem-4",
            "causationId": "cause-4",
            "recordedAt": "2026-04-07T10:15:30Z"
          }
        }
        """;
  }

  static String invalidMissingProvenanceRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ]
        }
        """;
  }

  static String invalidExponentAmountRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1e1000000100"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "100"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1"
          }
        }
        """;
  }

  static String invalidDuplicateIdempotencyKeyRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-7",
            "actorType": "AGENT",
            "commandId": "command-7",
            "idempotencyKey": "idem-7-a",
            "idempotencyKey": "idem-7-b",
            "causationId": "cause-7"
          }
        }
        """;
  }

  static String invalidUnexpectedTopLevelFieldRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-8",
            "actorType": "AGENT",
            "commandId": "command-8",
            "idempotencyKey": "idem-8",
            "causationId": "cause-8"
          },
          "unexpectedField": "should-be-rejected"
        }
        """;
  }

  static String invalidForbiddenSourceChannelRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-7",
            "actorType": "AGENT",
            "commandId": "command-7",
            "idempotencyKey": "idem-7",
            "causationId": "cause-7",
            "sourceChannel": null
          }
        }
        """;
  }

  static String invalidBlankActorRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "   ",
            "actorType": "AGENT",
            "commandId": "command-3",
            "idempotencyKey": "idem-3",
            "causationId": "cause-3"
          }
        }
        """;
  }

  static String reversalTargetMissingRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-08",
          "lines": [
            {
              "accountCode": "5000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "GBP",
                "minorUnits": "12345"
              }
            },
            {
              "accountCode": "6000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "GBP",
                "minorUnits": "12345"
              }
            }
          ],
          "reversal": {
            "priorPostingId": "posting-missing",
            "reason": "operator reversal"
          },
          "provenance": {
            "actorId": "actor-5",
            "actorType": "HUMAN",
            "commandId": "command-5",
            "idempotencyKey": "idem-5",
            "causationId": "cause-5"
          }
        }
        """;
  }

  static String missingReversalReasonRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-08",
          "lines": [
            {
              "accountCode": "3000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "USD",
                "minorUnits": "9995"
              }
            },
            {
              "accountCode": "4000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "USD",
                "minorUnits": "9995"
              }
            }
          ],
          "reversal": {
            "priorPostingId": "posting-missing"
          },
          "provenance": {
            "actorId": "actor-6",
            "actorType": "SYSTEM",
            "commandId": "command-6",
            "idempotencyKey": "idem-6",
            "causationId": "cause-6"
          }
        }
        """;
  }

  static String invalidWrongTypeRequest() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": 1,
          "lines": [],
          "provenance": {}
        }
        """;
  }
}
