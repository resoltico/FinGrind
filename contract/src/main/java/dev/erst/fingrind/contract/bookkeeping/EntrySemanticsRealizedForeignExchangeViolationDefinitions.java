package dev.erst.fingrind.contract.bookkeeping;

import java.util.List;

/** Realized foreign-exchange lifecycle violation definitions. */
final class EntrySemanticsRealizedForeignExchangeViolationDefinitions {
  private EntrySemanticsRealizedForeignExchangeViolationDefinitions() {}

  static List<EntrySemanticsViolationDefinition> definitions() {
    return List.of(
        definition(
            "foreign-currency-obligation-id-already-exists",
            "realized-foreign-exchange-lifecycle",
            "The selected foreignCurrencyObligationId already identifies one durable foreign-currency obligation in this book.",
            "Choose a new foreignCurrencyObligationId for a distinct receivable, or settle the existing obligation."),
        definition(
            "foreign-currency-obligation-not-found",
            "realized-foreign-exchange-lifecycle",
            "The selected foreignCurrencyObligationId does not identify one active foreign-currency obligation in this book.",
            "Use a foreignCurrencyObligationId returned by a prior foreign-currency obligation posting."),
        definition(
            "foreign-currency-obligation-already-settled",
            "realized-foreign-exchange-lifecycle",
            "The selected foreign-currency obligation has already been settled.",
            "Do not settle it again; correct the prior settlement through its historical reversal when needed."),
        definition(
            "realized-foreign-exchange-settlement-precedes-lifecycle-horizon",
            "realized-foreign-exchange-ordering",
            "The requested settlement effective date precedes the foreign-currency obligation's retained lifecycle horizon.",
            "Use an effectiveDate on or after the originating obligation and its latest retained lifecycle event."),
        definition(
            "realized-foreign-exchange-settlement-transaction-amount-mismatch",
            "realized-foreign-exchange-settlement",
            "The settlement transaction amount does not exactly match the retained foreign-currency obligation amount.",
            "Use the exact retained transaction-currency amount for the selected foreignCurrencyObligationId."),
        definition(
            "realized-foreign-exchange-settlement-functional-currency-mismatch",
            "realized-foreign-exchange-currency",
            "The settlement functional amount does not use the obligation's retained functional currency.",
            "Use foreignExchange.functionalAmount in the functional currency retained by the selected foreign-currency obligation."),
        definition(
            "foreign-currency-obligation-reversal-requires-settlement-reversed",
            "realized-foreign-exchange-reversal",
            "A foreign-currency obligation cannot be reversed while its active settlement remains.",
            "Reverse the active settlement before reversing the foreign-currency obligation."));
  }

  private static EntrySemanticsViolationDefinition definition(
      String code, String category, String description, String repair) {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
