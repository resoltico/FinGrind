package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.protocol.ProtocolForeignExchangeRequestFields;
import java.util.List;

/** Field specifications for owned foreign-exchange posting-request facts. */
final class MachineContractPostEntryForeignExchangeFieldSpecs {
  private MachineContractPostEntryForeignExchangeFieldSpecs() {}

  static List<MachineContractFieldSpec> foreignExchangeFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.ForeignExchange.TRANSACTION_AMOUNT,
            "Positive transaction-currency amount observed in the external economic event.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Positive transaction-currency amount observed in the external economic event.",
                true)),
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.ForeignExchange.FUNCTIONAL_AMOUNT,
            "Positive translated functional-currency amount recognized in the selected book.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Positive translated functional-currency amount recognized in the selected book.",
                true)),
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.ForeignExchange.QUOTED_RATE,
            "Owned exact quoted exchange-rate facts used for this translation.",
            MachineContractPostEntryComponentSchemas.quotedRateSchema()),
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND,
            "Owned foreign-exchange treatment kind that explains whether this request represents spot settlement, realized settlement, or unrealized remeasurement.",
            MachineContractScalarSchemas.enumStringSchema(
                "Owned foreign-exchange treatment kind.",
                ForeignExchangeTreatmentKind.wireValues())));
  }

  static List<MachineContractFieldSpec> quotedRateFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.QuotedRate.TRANSACTION_CURRENCY_AMOUNT,
            "Positive quoted transaction-currency amount used as the exact basis of the rate.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Positive quoted transaction-currency amount used as the exact basis of the rate.",
                true)),
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.QuotedRate.FUNCTIONAL_CURRENCY_AMOUNT,
            "Positive quoted functional-currency amount used as the exact basis of the rate.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Positive quoted functional-currency amount used as the exact basis of the rate.",
                true)),
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.QuotedRate.QUOTED_ON,
            "ISO-8601 local date on which the quoted exchange rate was observed.",
            MachineContractScalarSchemas.dateStringSchema(
                "ISO-8601 local date on which the quoted exchange rate was observed.")),
        MachineContractFieldSpec.required(
            ProtocolForeignExchangeRequestFields.QuotedRate.QUOTE_SOURCE,
            "Plain-language source for the quoted exchange rate evidence.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Plain-language source for the quoted exchange rate evidence.")));
  }
}
