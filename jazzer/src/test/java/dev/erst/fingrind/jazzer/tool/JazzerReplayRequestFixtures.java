package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzHarnessTestSupport;

final class JazzerReplayRequestFixtures {
  private JazzerReplayRequestFixtures() {}

  static String basicValidRequest() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
        new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
            "2026-04-07",
            "1000",
            "2000",
            "EUR",
            "1000",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-1",
                "cash-receipt",
                "2026-04-07",
                "actor-1",
                "AGENT",
                "command-1",
                "idem-1",
                "cause-1",
                null)));
  }

  static String invalidForbiddenRecordedAtRequest() {
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
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "idem-4",
            "causationId": "cause-4",
            "recordedAt": "2026-04-07T10:15:30Z"
          }
        }
        """
        .formatted(
            CliFuzzHarnessTestSupport.evidenceJson("document-idem-4", "cash-receipt", "2026-04-07")
                .indent(10)
                .stripLeading());
  }

  static String invalidMissingProvenanceRequest() {
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
          "evidence": %s
        }
        """
        .formatted(
            CliFuzzHarnessTestSupport.evidenceJson(
                    "document-idem-missing-provenance", "cash-receipt", "2026-04-07")
                .indent(10)
                .stripLeading());
  }

  static String invalidExponentAmountRequest() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
        new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
            "2026-04-07",
            "1000",
            "2000",
            "EUR",
            "1e1000000100",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-1",
                "cash-receipt",
                "2026-04-07",
                "actor-1",
                "AGENT",
                "command-1",
                "idem-1",
                "cause-1",
                null)));
  }

  static String invalidDuplicateIdempotencyKeyRequest() {
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
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "idem-7-a",
            "idempotencyKey": "idem-7-b",
            "causationId": "cause-7"
          }
        }
        """
        .formatted(
            CliFuzzHarnessTestSupport.evidenceJson("document-idem-7", "cash-receipt", "2026-04-07")
                .indent(10)
                .stripLeading());
  }

  static String invalidUnexpectedTopLevelFieldRequest() {
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
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "idem-8",
            "causationId": "cause-8"
          },
          "unexpectedField": "should-be-rejected"
        }
        """
        .formatted(
            CliFuzzHarnessTestSupport.evidenceJson("document-idem-8", "cash-receipt", "2026-04-07")
                .indent(10)
                .stripLeading());
  }

  static String invalidForbiddenSourceChannelRequest() {
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
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "idem-7",
            "causationId": "cause-7",
            "sourceChannel": null
          }
        }
        """
        .formatted(
            CliFuzzHarnessTestSupport.evidenceJson("document-idem-7", "cash-receipt", "2026-04-07")
                .indent(10)
                .stripLeading());
  }

  static String invalidBlankActorRequest() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
        new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
            "2026-04-07",
            "1000",
            "2000",
            "EUR",
            "1000",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-3",
                "cash-receipt",
                "2026-04-07",
                "   ",
                "AGENT",
                "command-3",
                "idem-3",
                "cause-3",
                null)));
  }

  static String reversalTargetMissingRequest() {
    return CliFuzzHarnessTestSupport.reversalAdjustmentRequestJson(
        new CliFuzzHarnessTestSupport.ReversalAdjustmentRequestInput(
            "2026-04-07",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-5",
                "credit-note",
                "2026-04-07",
                "actor-5",
                "PERSON",
                "command-5",
                "idem-5",
                "cause-5",
                null),
            "posting-missing",
            "operator reversal"));
  }

  static String missingReversalReasonRequest() {
    return CliFuzzHarnessTestSupport.reversalAdjustmentRequestJson(
        new CliFuzzHarnessTestSupport.ReversalAdjustmentRequestInput(
            "2026-04-07",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-6",
                "credit-note",
                "2026-04-07",
                "actor-6",
                "SYSTEM",
                "command-6",
                "idem-6",
                "cause-6",
                null),
            "posting-missing",
            null));
  }

  static String invalidWrongTypeRequest() {
    return """
        {
          "entryKind": "SALE_SETTLED",
          "effectiveDate": 1,
          "cashAccountCode": "1000",
          "revenueAccountCode": "2000",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "1000"
          },
          "evidence": %s,
          "provenance": {}
        }
        """
        .formatted(
            CliFuzzHarnessTestSupport.evidenceJson(
                    "document-invalid-wrong-type", "cash-receipt", "2026-04-07")
                .indent(10)
                .stripLeading());
  }
}
