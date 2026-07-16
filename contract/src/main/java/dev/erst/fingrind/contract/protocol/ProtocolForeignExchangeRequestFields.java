package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical request grammar owned by the Foreign Exchange context. */
public final class ProtocolForeignExchangeRequestFields {
  private ProtocolForeignExchangeRequestFields() {}

  /** Returns foreign-exchange facts in stable wire order. */
  public static List<String> foreignExchangeFields() {
    return List.of(
        ForeignExchange.TRANSACTION_AMOUNT,
        ForeignExchange.FUNCTIONAL_AMOUNT,
        ForeignExchange.QUOTED_RATE,
        ForeignExchange.TREATMENT_KIND);
  }

  /** Returns quoted-rate facts in stable wire order. */
  public static List<String> quotedRateFields() {
    return List.of(
        QuotedRate.TRANSACTION_CURRENCY_AMOUNT,
        QuotedRate.FUNCTIONAL_CURRENCY_AMOUNT,
        QuotedRate.QUOTED_ON,
        QuotedRate.QUOTE_SOURCE);
  }

  /** Request-side foreign-exchange facts. */
  public static final class ForeignExchange {
    public static final String TRANSACTION_AMOUNT = "transactionAmount";
    public static final String FUNCTIONAL_AMOUNT = "functionalAmount";
    public static final String QUOTED_RATE = "quotedRate";
    public static final String TREATMENT_KIND = "treatmentKind";

    private ForeignExchange() {}
  }

  /** Request-side quoted exchange-rate facts. */
  public static final class QuotedRate {
    public static final String TRANSACTION_CURRENCY_AMOUNT = "transactionCurrencyAmount";
    public static final String FUNCTIONAL_CURRENCY_AMOUNT = "functionalCurrencyAmount";
    public static final String QUOTED_ON = "quotedOn";
    public static final String QUOTE_SOURCE = "quoteSource";

    private QuotedRate() {}
  }
}
