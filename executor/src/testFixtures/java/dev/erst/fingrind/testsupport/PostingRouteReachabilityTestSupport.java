package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Shared reachability scenarios reused by in-memory and SQLite-backed reachability contracts. */
public final class PostingRouteReachabilityTestSupport {
  public static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);
  public static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  public static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  public static final AccountCode COUNTER_ACCOUNT_CODE = new AccountCode("1000");
  public static final AccountCode CANDIDATE_ACCOUNT_CODE = new AccountCode("9000");

  private PostingRouteReachabilityTestSupport() {}

  /** Returns the published reachability matrix that every executable contract must honor. */
  public static List<RequestSurfaceFacts.ReachabilityCellFacts> reachabilityMatrix() {
    return ProtocolCatalog.domain().requestSurface().reachabilityMatrix();
  }

  /** Produces a stable scenario token for one matrix cell. */
  public static String cellToken(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return cell.classificationFamily()
        + "-"
        + cell.accountType().wireValue().toLowerCase(Locale.ROOT)
        + "-"
        + cell.classification().toLowerCase(Locale.ROOT);
  }

  /** Declares the scenario's candidate account using the matrix cell taxonomy. */
  public static RegisteredAccount candidateAccount(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return new RegisteredAccount(
        CANDIDATE_ACCOUNT_CODE,
        new AccountName("Candidate"),
        cell.accountType(),
        taxonomy(cell),
        true,
        DECLARED_AT);
  }

  /** Declares the balancing asset account shared by every reachability scenario. */
  public static RegisteredAccount counterAssetAccount() {
    return new RegisteredAccount(
        COUNTER_ACCOUNT_CODE,
        new AccountName("Cash"),
        dev.erst.fingrind.core.AccountType.ASSET,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
        true,
        DECLARED_AT);
  }

  /** Translates one matrix cell into the corresponding executable account taxonomy. */
  public static AccountTaxonomy taxonomy(RequestSurfaceFacts.ReachabilityCellFacts cell) {
    return switch (cell.classificationFamily()) {
      case "financial-position" -> financialPositionTaxonomy(cell.classification());
      case "profit-and-loss" -> profitAndLossTaxonomy(cell.classification());
      default ->
          throw new IllegalArgumentException(
              "Unsupported classification family " + cell.classificationFamily());
    };
  }

  private static AccountTaxonomy financialPositionTaxonomy(String classificationWireValue) {
    FinancialPositionLineClassification classification =
        FinancialPositionLineClassification.fromWireValue(classificationWireValue);
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(classification),
        Optional.empty(),
        assetCashFlowClassification(classification));
  }

  private static Optional<CashFlowAssetClassification> assetCashFlowClassification(
      FinancialPositionLineClassification classification) {
    return switch (classification) {
      case CURRENT_ASSET, NONCURRENT_ASSET -> Optional.of(CashFlowAssetClassification.NON_CASH);
      case CURRENT_LIABILITY,
          NONCURRENT_LIABILITY,
          EQUITY_CONTRIBUTION,
          EQUITY_WITHDRAWAL,
          RESULT_HOLDING,
          RETAINED_ACCUMULATED,
          RESERVE,
          OTHER_EQUITY ->
          Optional.empty();
    };
  }

  private static AccountTaxonomy profitAndLossTaxonomy(String classificationWireValue) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.fromWireValue(classificationWireValue)));
  }
}
