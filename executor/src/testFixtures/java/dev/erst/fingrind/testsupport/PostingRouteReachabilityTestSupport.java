package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
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

  /** Returns the accrual-basis book identity used to probe basis-neutral reachability doctrine. */
  public static BookIdentity accrualBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
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

  /** Declares the auxiliary counter-account shared by every reachability scenario. */
  public static RegisteredAccount counterAuxiliaryAccount() {
    return new RegisteredAccount(
        COUNTER_ACCOUNT_CODE,
        new AccountName("Counterparty"),
        dev.erst.fingrind.core.AccountType.ASSET,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.NON_CASH)),
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
    return classification.accountType() == dev.erst.fingrind.core.AccountType.ASSET
        ? Optional.of(CashFlowAssetClassification.NON_CASH)
        : Optional.empty();
  }

  private static AccountTaxonomy profitAndLossTaxonomy(String classificationWireValue) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.fromWireValue(classificationWireValue)));
  }
}
