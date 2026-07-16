package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared semantic report primitives and resolved-query contracts for machine CLI output. */
public interface CliReportJsonModels {
  /** Shared marker for report payloads that carry one resolved query and result. */
  sealed interface ReportPayload extends ProtocolSuccessPayload
      permits CliAccountReportJsonModels.AccountReportPayload,
          CliStatementReportJsonModels.StatementReportPayload,
          CliInventoryReportJsonModels.InventoryValuationPayload,
          CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload,
          CliFixedAssetReportJsonModels.FixedAssetRegisterPayload,
          CliLifecycleContextReportJsonModels.LifecycleContextReportPayload,
          CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterPayload,
          CliTaxReportJsonModels.TaxObligationPayload {}

  /** Shared marker for the exact accepted-and-resolved scope of one report family. */
  sealed interface ResolvedQuery
      permits AccountBalanceResolvedQuery,
          TrialBalanceResolvedQuery,
          AccountLedgerResolvedQuery,
          PeriodResolvedQuery,
          AsOfResolvedQuery,
          InventoryValuationResolvedQuery,
          AccrualCutoffScheduleResolvedQuery,
          FixedAssetRegisterResolvedQuery,
          FinancingRegisterResolvedQuery,
          RealizedForeignExchangeRegisterResolvedQuery,
          LatvianPayrollRegisterResolvedQuery,
          TaxObligationResolvedQuery {}

  record AccountBalanceResolvedQuery(
      String accountCode,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String effectiveDateFrom,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String effectiveDateTo,
      String postingCoverage)
      implements ResolvedQuery {
    public AccountBalanceResolvedQuery {
      accountCode = requireText(accountCode, "accountCode");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
    }
  }

  record TrialBalanceResolvedQuery(
      @Nullable String asOf, String postingCoverage, @Nullable ComparativeRangePayload comparative)
      implements ResolvedQuery {
    public TrialBalanceResolvedQuery {
      asOf = requireOptionalText(asOf, "asOf");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
    }
  }

  record AccountLedgerResolvedQuery(
      String accountCode,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String effectiveDateFrom,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String effectiveDateTo,
      String postingCoverage,
      PaginationPayload pagination)
      implements ResolvedQuery {
    public AccountLedgerResolvedQuery {
      accountCode = requireText(accountCode, "accountCode");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
      Objects.requireNonNull(pagination, "pagination");
    }
  }

  /** The accepted cursor window for a paginated report query. */
  record PaginationPayload(
      int limit, @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String cursor) {
    public PaginationPayload {
      if (limit < 1) {
        throw new IllegalArgumentException("limit must be positive.");
      }
      cursor = requireOptionalText(cursor, "cursor");
    }
  }

  record PeriodResolvedQuery(
      String periodStart,
      String periodEnd,
      String postingCoverage,
      @Nullable ComparativeRangePayload comparative)
      implements ResolvedQuery {
    public PeriodResolvedQuery {
      periodStart = requireText(periodStart, "periodStart");
      periodEnd = requireText(periodEnd, "periodEnd");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
    }
  }

  record AsOfResolvedQuery(
      @Nullable String asOf, String postingCoverage, @Nullable ComparativeRangePayload comparative)
      implements ResolvedQuery {
    public AsOfResolvedQuery {
      asOf = requireOptionalText(asOf, "asOf");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
    }
  }

  record InventoryValuationResolvedQuery(@Nullable String asOf, boolean movements)
      implements ResolvedQuery {
    public InventoryValuationResolvedQuery {
      asOf = requireOptionalText(asOf, "asOf");
    }
  }

  record AccrualCutoffScheduleResolvedQuery(@Nullable String asOf) implements ResolvedQuery {
    public AccrualCutoffScheduleResolvedQuery {
      asOf = requireOptionalText(asOf, "asOf");
    }
  }

  record FixedAssetRegisterResolvedQuery(@Nullable String asOf) implements ResolvedQuery {
    public FixedAssetRegisterResolvedQuery {
      asOf = requireOptionalText(asOf, "asOf");
    }
  }

  record FinancingRegisterResolvedQuery() implements ResolvedQuery {}

  record RealizedForeignExchangeRegisterResolvedQuery() implements ResolvedQuery {}

  /** The payroll register is always a complete book-wide lifecycle view. */
  record LatvianPayrollRegisterResolvedQuery() implements ResolvedQuery {}

  record TaxObligationResolvedQuery(String taxRegistrationId, String periodStart, String periodEnd)
      implements ResolvedQuery {
    public TaxObligationResolvedQuery {
      taxRegistrationId = requireText(taxRegistrationId, "taxRegistrationId");
      periodStart = requireText(periodStart, "periodStart");
      periodEnd = requireText(periodEnd, "periodEnd");
    }
  }

  record ComparativeRangePayload(@Nullable String periodStart, @Nullable String periodEnd) {
    public ComparativeRangePayload {
      periodStart = requireOptionalText(periodStart, "periodStart");
      periodEnd = requireOptionalText(periodEnd, "periodEnd");
    }
  }
}
