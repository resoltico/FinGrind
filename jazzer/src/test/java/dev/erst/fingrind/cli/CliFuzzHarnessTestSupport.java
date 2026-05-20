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
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CliFuzzHarnessTestSupport {
  private CliFuzzHarnessTestSupport() {}

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
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-06-01",
          "lines": [
            {
              "accountCode": "1100",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "JPY",
                "minorUnits": "100"
              }
            },
            {
              "accountCode": "2100",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "JPY",
                "minorUnits": "100"
              }
            }
          ],
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-idem-jpy-1",
                "sourceDocumentType": "invoice"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-jpy-1",
            "actorType": "AGENT",
            "commandId": "command-jpy-1",
            "idempotencyKey": "idem-jpy-1",
            "causationId": "cause-jpy-1"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] validBhdRequestBytes() {
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-06-02",
          "lines": [
            {
              "accountCode": "1200",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "BHD",
                "minorUnits": "1250"
              }
            },
            {
              "accountCode": "2200",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "BHD",
                "minorUnits": "1250"
              }
            }
          ],
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-idem-bhd-1",
                "sourceDocumentType": "invoice"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-bhd-1",
            "actorType": "AGENT",
            "commandId": "command-bhd-1",
            "idempotencyKey": "idem-bhd-1",
            "causationId": "cause-bhd-1"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] invalidExponentAmountRequestBytes() {
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
        """
        .getBytes(UTF_8);
  }

  static byte[] invalidBlankActorRequestBytes() {
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
        """
        .getBytes(UTF_8);
  }

  static byte[] missingReversalReasonRequestBytes() {
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
            "priorPostingId": "posting-old"
          },
          "provenance": {
            "actorId": "actor-2",
            "actorType": "HUMAN",
            "commandId": "command-2",
            "idempotencyKey": "idem-2",
            "causationId": "cause-2"
          }
        }
        """
        .getBytes(UTF_8);
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
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-idem-5",
                "sourceDocumentType": "credit-note"
              }
            ],
            "approvals": []
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
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-06-03",
                "lines": [
                  {
                    "accountCode": "1100",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "JPY",
                      "minorUnits": "100"
                    }
                  },
                  {
                    "accountCode": "2100",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "JPY",
                      "minorUnits": "100"
                    }
                  }
                ],
                "evidence": {
                  "sourceDocuments": [
                    {
                      "sourceDocumentId": "document-idem-jpy-plan-1",
                      "sourceDocumentType": "invoice"
                    }
                  ],
                  "approvals": []
                },
                "provenance": {
                  "actorId": "agent-jpy-plan-1",
                  "actorType": "AGENT",
                  "commandId": "command-jpy-plan-1",
                  "idempotencyKey": "idem-jpy-plan-1",
                  "causationId": "cause-jpy-plan-1"
                }
              }
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
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-06-04",
                "lines": [
                  {
                    "accountCode": "1200",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "BHD",
                      "minorUnits": "1250"
                    }
                  },
                  {
                    "accountCode": "2200",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "BHD",
                      "minorUnits": "1250"
                    }
                  }
                ],
                "evidence": {
                  "sourceDocuments": [
                    {
                      "sourceDocumentId": "document-idem-bhd-plan-1",
                      "sourceDocumentType": "invoice"
                    }
                  ],
                  "approvals": []
                },
                "provenance": {
                  "actorId": "agent-bhd-plan-1",
                  "actorType": "AGENT",
                  "commandId": "command-bhd-plan-1",
                  "idempotencyKey": "idem-bhd-plan-1",
                  "causationId": "cause-bhd-plan-1"
                }
              }
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
                .stripLeading())
        .getBytes(UTF_8);
  }

  public static String canonicalOpenBookJson(String functionalCurrency) {
    return """
        {
          "entityName": "Acme Studio",
          "entityForm": "COMPANY",
          "ownerModel": "MULTI_OWNER",
          "reportingObligationStatus": "INTERNAL_MANAGEMENT_ONLY",
          "businessActivityTags": ["translation-services"],
          "functionalCurrency": "%s",
          "fiscalYearStart": "01-01",
          "accountingBasis": "ACCRUAL"
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
