package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

  public record ManualAdjustmentRequestInput(
      String effectiveDate,
      String postingKind,
      String linesJson,
      RequestContext context,
      @Nullable String priorPostingId,
      @Nullable String reversalReason) {
    public ManualAdjustmentRequestInput {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(postingKind, "postingKind");
      Objects.requireNonNull(linesJson, "linesJson");
      Objects.requireNonNull(context, "context");
    }
  }

  static FuzzedDataProvider fuzzedBytes(byte[] input) {
    InvocationHandler handler = new FuzzedBytesDataProviderHandler(input);
    return (FuzzedDataProvider)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {FuzzedDataProvider.class},
            handler);
  }

  static void invokeFuzzHarness(String className, String methodName, byte[] input) {
    try {
      Class<?> harnessClass =
          Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      Object harness = harnessClass.getDeclaredConstructor().newInstance();
      Method method = harnessClass.getDeclaredMethod(methodName, FuzzedDataProvider.class);
      method.setAccessible(true);
      method.invoke(harness, fuzzedBytes(input));
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Fuzz harness invocation failed.", cause);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to invoke compiled fuzz harness.", exception);
    }
  }

  static byte[] basicValidRequestBytes() {
    return SqliteRoundTripWorkflowTestSupport.basicValidRequest().getBytes(UTF_8);
  }

  static byte[] validJpyRequestBytes() {
    return cashRevenueRequestJson(
            new CashRevenueRequestInput(
                "2026-06-01",
                "1100",
                "2100",
                "JPY",
                "100",
                new RequestContext(
                    "document-idem-jpy-1",
                    "invoice",
                    "2026-06-01",
                    "actor-jpy-1",
                    "AGENT",
                    "command-jpy-1",
                    "idem-jpy-1",
                    "cause-jpy-1",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] validBhdRequestBytes() {
    return cashRevenueRequestJson(
            new CashRevenueRequestInput(
                "2026-06-02",
                "1200",
                "2200",
                "BHD",
                "1250",
                new RequestContext(
                    "document-idem-bhd-1",
                    "invoice",
                    "2026-06-02",
                    "actor-bhd-1",
                    "AGENT",
                    "command-bhd-1",
                    "idem-bhd-1",
                    "cause-bhd-1",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] invalidExponentAmountRequestBytes() {
    return cashRevenueRequestJson(
            new CashRevenueRequestInput(
                "2026-04-07",
                "1000",
                "2000",
                "EUR",
                "1e1000000100",
                new RequestContext(
                    "document-idem-1",
                    "invoice",
                    "2026-04-07",
                    "actor-1",
                    "AGENT",
                    "command-1",
                    "idem-1",
                    "cause-1",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] invalidBlankActorRequestBytes() {
    return cashRevenueRequestJson(
            new CashRevenueRequestInput(
                "2026-04-07",
                "1000",
                "2000",
                "EUR",
                "1000",
                new RequestContext(
                    "document-idem-3",
                    "invoice",
                    "2026-04-07",
                    "   ",
                    "AGENT",
                    "command-3",
                    "idem-3",
                    "cause-3",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] missingReversalReasonRequestBytes() {
    return manualAdjustmentRequestJson(
            new ManualAdjustmentRequestInput(
                "2026-04-08",
                "STANDARD",
                """
                [
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
                ]
                """,
                new RequestContext(
                    "document-idem-2",
                    "credit-note",
                    "2026-04-08",
                    "actor-2",
                    "HUMAN",
                    "command-2",
                    "idem-2",
                    "cause-2",
                    null),
                "posting-old",
                null))
        .getBytes(UTF_8);
  }

  static String reversalTargetMissingRequest() {
    return manualAdjustmentRequestJson(
        new ManualAdjustmentRequestInput(
            "2026-04-08",
            "STANDARD",
            """
            [
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
            ]
            """,
            new RequestContext(
                "document-idem-5",
                "credit-note",
                "2026-04-08",
                "actor-5",
                "HUMAN",
                "command-5",
                "idem-5",
                "cause-5",
                null),
            "posting-missing",
            "operator reversal"));
  }

  static byte[] reversalTargetMissingRequestBytes() {
    return reversalTargetMissingRequest().getBytes(UTF_8);
  }

  static byte[] invalidWrongTypeRequestBytes() {
    return """
        {
          "effectiveDate": 1,
          "lines": [],
          "provenance": {}
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] basicValidLedgerPlanBytes() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading())
        .getBytes(UTF_8);
  }

  static byte[] validJpyLedgerPlanBytes() {
    return """
        {
          "planId": "plan-jpy-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-jpy",
              "kind": "post-entry",
              "posting": %s
            },
            {
              "stepId": "assert-jpy",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1100",
                "netAmount": {
                  "currencyCode": "JPY",
                  "minorUnits": "100"
                },
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson(
                    "declare-cash-jpy", "1100", "Cash JPY", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-sales-jpy", "2100", "Sales JPY", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            cashRevenueRequestJson(
                    new CashRevenueRequestInput(
                        "2026-06-03",
                        "1100",
                        "2100",
                        "JPY",
                        "100",
                        new RequestContext(
                            "document-idem-jpy-plan-1",
                            "invoice",
                            "2026-06-03",
                            "agent-jpy-plan-1",
                            "AGENT",
                            "command-jpy-plan-1",
                            "idem-jpy-plan-1",
                            "cause-jpy-plan-1",
                            null)))
                .indent(16)
                .stripLeading())
        .getBytes(UTF_8);
  }

  static byte[] validBhdLedgerPlanBytes() {
    return """
        {
          "planId": "plan-bhd-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-bhd",
              "kind": "post-entry",
              "posting": %s
            },
            {
              "stepId": "assert-bhd",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1200",
                "netAmount": {
                  "currencyCode": "BHD",
                  "minorUnits": "1250"
                },
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson(
                    "declare-cash-bhd", "1200", "Cash BHD", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-sales-bhd", "2200", "Sales BHD", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            cashRevenueRequestJson(
                    new CashRevenueRequestInput(
                        "2026-06-04",
                        "1200",
                        "2200",
                        "BHD",
                        "1250",
                        new RequestContext(
                            "document-idem-bhd-plan-1",
                            "invoice",
                            "2026-06-04",
                            "agent-bhd-plan-1",
                            "AGENT",
                            "command-bhd-plan-1",
                            "idem-bhd-plan-1",
                            "cause-bhd-plan-1",
                            null)))
                .indent(16)
                .stripLeading())
        .getBytes(UTF_8);
  }

  public static String canonicalOpenBookJson(String functionalCurrency) {
    return """
        {
          "entityName": "Acme Studio",
          "entityForm": "COMPANY",
          "ownerModel": "MULTI_OWNER",
                    "businessActivityTags": ["translation-services"],
          "functionalCurrency": "%s",
          "fiscalYearStart": "01-01",
          "policyProfile": "INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1"
        }
        """
        .formatted(functionalCurrency)
        .indent(14)
        .stripLeading();
  }

  public static String declareOrdinaryAccountStepJson(
      String stepId, String accountCode, String accountName, AccountType accountType) {
    return declareAccountStepJson(
        stepId, accountCode, accountName, accountType, AccountRole.ORDINARY);
  }

  public static String declareAccountStepJson(
      String stepId,
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole) {
    return """
        {
          "stepId": "%s",
          "kind": "declare-account",
          "declareAccount": %s
        }
        """
        .formatted(
            stepId,
            declareAccountJson(accountCode, accountName, accountType, accountRole)
                .indent(4)
                .stripLeading());
  }

  public static String declareAccountJson(
      String accountCode, String accountName, AccountType accountType, AccountRole accountRole) {
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
            accountType.name(),
            accountRole.name(),
            quotedOrNull(financialPositionLineClassificationWireValue(accountType)),
            quotedOrNull(profitAndLossLineClassificationWireValue(accountType)));
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

  public static String manualAdjustmentRequestJson(ManualAdjustmentRequestInput request) {
    String reversalJson =
        request.priorPostingId() == null
            ? ""
            : """
              ,
              "reversal": {
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
          "entryKind": "MANUAL_ADJUSTMENT",
          "postingKind": "%s",
          "effectiveDate": "%s",
          "lines": %s%s,
          "evidence": %s,
          "provenance": %s
        }
        """
        .formatted(
            request.postingKind(),
            request.effectiveDate(),
            request.linesJson().strip(),
            reversalJson,
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

  private static String quotedOrNull(@Nullable String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value + "\"";
  }

  private static @Nullable String financialPositionLineClassificationWireValue(
      AccountType accountType) {
    return switch (accountType) {
      case ASSET -> FinancialPositionLineClassification.CURRENT_ASSET.name();
      case LIABILITY -> FinancialPositionLineClassification.CURRENT_LIABILITY.name();
      case EQUITY -> FinancialPositionLineClassification.OTHER_EQUITY.name();
      case REVENUE, EXPENSE -> null;
    };
  }

  private static @Nullable String profitAndLossLineClassificationWireValue(
      AccountType accountType) {
    return switch (accountType) {
      case REVENUE -> ProfitAndLossLineClassification.OPERATING_REVENUE.name();
      case EXPENSE -> ProfitAndLossLineClassification.OPERATING_EXPENSE.name();
      case ASSET, LIABILITY, EQUITY -> null;
    };
  }

  static byte[] rejectedMissingBookListPostingsLedgerPlanBytes() {
    return """
        {
          "planId": "missing-book-list-postings",
          "steps": [
            {
              "stepId": "list-postings",
              "kind": "list-postings",
              "listPostings": {
                "limit": 10
              }
            }
          ]
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] invalidLedgerPlanBytes() {
    return """
        {
          "planId": "bad-plan",
          "steps": []
        }
        """
        .getBytes(UTF_8);
  }

  private static final class FuzzedBytesDataProviderHandler implements InvocationHandler {
    private final byte[] input;

    private FuzzedBytesDataProviderHandler(byte[] input) {
      this.input = input.clone();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "consumeRemainingAsBytes" -> input.clone();
        case "toString" -> "FuzzedBytesDataProvider";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" ->
            args != null
                && args.length == 1
                && args[0] != null
                && Proxy.isProxyClass(args[0].getClass())
                && Objects.equals(Proxy.getInvocationHandler(args[0]), this);
        default ->
            throw new UnsupportedOperationException(
                "Unsupported FuzzedDataProvider method: " + method.getName());
      };
    }
  }
}
