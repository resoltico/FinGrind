package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CliFuzzHarnessTestSupport {
  private CliFuzzHarnessTestSupport() {}

  public record RequestContext(
      String sourceDocumentId,
      String sourceDocumentType,
      String documentDate,
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId) {
    public RequestContext {
      Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
      Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
      Objects.requireNonNull(documentDate, "documentDate");
      Objects.requireNonNull(actorId, "actorId");
      Objects.requireNonNull(actorType, "actorType");
      Objects.requireNonNull(commandId, "commandId");
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
      Objects.requireNonNull(causationId, "causationId");
    }
  }

  public record CashRevenueRequestInput(
      String effectiveDate,
      String cashAccountCode,
      String revenueAccountCode,
      String currencyCode,
      String minorUnits,
      RequestContext context) {
    public CashRevenueRequestInput {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(revenueAccountCode, "revenueAccountCode");
      Objects.requireNonNull(currencyCode, "currencyCode");
      Objects.requireNonNull(minorUnits, "minorUnits");
      Objects.requireNonNull(context, "context");
    }
  }

  public record OpenAccountingPositionRequestInput(
      String effectiveDate, String openingBalancesJson, RequestContext context) {
    public OpenAccountingPositionRequestInput {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(openingBalancesJson, "openingBalancesJson");
      Objects.requireNonNull(context, "context");
    }
  }

  public record ReversalAdjustmentRequestInput(
      String effectiveDate,
      String linesJson,
      RequestContext context,
      String priorPostingId,
      @Nullable String reversalReason) {
    public ReversalAdjustmentRequestInput {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(linesJson, "linesJson");
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  public static String cashRevenueRequestJson(CashRevenueRequestInput request) {
    return """
        {
          "entryKind": "CASH_REVENUE",
          "effectiveDate": "%s",
          "cashAccountCode": "%s",
          "revenueAccountCode": "%s",
          "amount": {
            "currencyCode": "%s",
            "minorUnits": "%s"
          },
          "evidence": %s,
          "provenance": %s
        }
        """
        .formatted(
            request.effectiveDate(),
            request.cashAccountCode(),
            request.revenueAccountCode(),
            request.currencyCode(),
            request.minorUnits(),
            evidenceJson(
                    request.context().sourceDocumentId(),
                    request.context().sourceDocumentType(),
                    request.context().documentDate())
                .indent(10)
                .stripLeading(),
            provenanceJson(request.context()).indent(10).stripLeading());
  }

  public static String openAccountingPositionRequestJson(
      OpenAccountingPositionRequestInput request) {
    return """
        {
          "entryKind": "OPEN_ACCOUNTING_POSITION",
          "effectiveDate": "%s",
          "openingBalances": %s,
          "evidence": %s,
          "provenance": %s
        }
        """
        .formatted(
            request.effectiveDate(),
            request.openingBalancesJson().strip(),
            evidenceJson(
                    request.context().sourceDocumentId(),
                    request.context().sourceDocumentType(),
                    request.context().documentDate())
                .indent(10)
                .stripLeading(),
            provenanceJson(request.context()).indent(10).stripLeading());
  }

  public static String reversalAdjustmentRequestJson(ReversalAdjustmentRequestInput request) {
    String reversalJson =
        """
        {
          "priorPostingId": "%s"%s
        }
        """
            .formatted(
                request.priorPostingId(),
                request.reversalReason() == null
                    ? ""
                    : ",\n    \"reason\": \"" + request.reversalReason() + "\"");
    return """
        {
          "entryKind": "REVERSAL_ADJUSTMENT",
          "effectiveDate": "%s",
          "lines": %s,
          "reversal": %s,
          "evidence": %s,
          "provenance": %s
        }
        """
        .formatted(
            request.effectiveDate(),
            request.linesJson().strip(),
            reversalJson.indent(10).stripLeading(),
            evidenceJson(
                    request.context().sourceDocumentId(),
                    request.context().sourceDocumentType(),
                    request.context().documentDate())
                .indent(10)
                .stripLeading(),
            provenanceJson(request.context()).indent(10).stripLeading());
  }

  public static String evidenceJson(
      String sourceDocumentId, String sourceDocumentType, String documentDate) {
    return """
        {
          "sourceDocuments": [
            {
              "sourceDocumentId": "%s",
              "sourceDocumentType": "%s",
              "documentDate": "%s",
              "capturedAt": "%sT10:15:30Z",
              "storageLocator": "vault://fixtures/%s",
              "contentSha256": "%s"
            }
          ],
          "approvals": []
        }
        """
        .formatted(
            sourceDocumentId,
            sourceDocumentType,
            documentDate,
            documentDate,
            sourceDocumentId,
            sha256Hex(sourceDocumentId));
  }

  public static String provenanceJson(RequestContext context) {
    String correlationField =
        context.correlationId() == null
            ? ""
            : ",\n  \"correlationId\": \"" + context.correlationId() + "\"";
    return """
        {
          "actorId": "%s",
          "actorType": "%s",
          "commandId": "%s",
          "idempotencyKey": "%s",
          "causationId": "%s"%s
        }
        """
        .formatted(
            context.actorId(),
            context.actorType(),
            context.commandId(),
            context.idempotencyKey(),
            context.causationId(),
            correlationField);
  }

  private static String sha256Hex(String input) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JVM is missing SHA-256 support.", exception);
    }
  }
}
