package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Machine request schemas owned by the Realized Foreign Exchange context. */
final class MachineContractRealizedForeignExchangePostEntryVariantSchemaBuilders {
  private MachineContractRealizedForeignExchangePostEntryVariantSchemaBuilders() {}

  static Map<String, Object> foreignCurrencyObligationSchema() {
    return schema(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        "Creates one foreign-currency receivable with its retained functional carrying amount.",
        List.of(
            obligationId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
                "Declared trade-receivable account debited at the functional carrying amount."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                "Declared revenue account credited at the functional carrying amount."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.REALIZED_GAIN_ACCOUNT_CODE,
                "Declared revenue account credited for a later realized foreign-exchange gain."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.REALIZED_LOSS_ACCOUNT_CODE,
                "Declared expense account debited for a later realized foreign-exchange loss."),
            requiredForeignExchange()));
  }

  static Map<String, Object> settlementSchema() {
    return schema(
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        "Settles one foreign-currency receivable and derives the realized foreign-exchange result.",
        List.of(
            obligationId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account debited at the settlement functional amount."),
            requiredForeignExchange()));
  }

  private static Map<String, Object> schema(
      BookkeepingEntryKind kind, String description, List<MachineContractFieldSpec> contextFields) {
    return MachineContractPostEntryContextSchemaSupport.typedEventSchema(
        kind,
        description,
        "This request records a typed realized foreign-exchange event.",
        contextFields);
  }

  private static MachineContractFieldSpec obligationId() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.FOREIGN_CURRENCY_OBLIGATION_ID,
        "Stable lowercase-kebab identifier for this foreign-currency obligation.",
        MachineContractScalarSchemas.tokenStringSchema(
            "Stable lowercase-kebab identifier for this foreign-currency obligation.",
            "[a-z0-9]+(?:-[a-z0-9]+)*",
            120));
  }

  private static MachineContractFieldSpec requiredForeignExchange() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
        "Required transaction, functional, and quoted-rate facts retained by this realized foreign-exchange event.",
        MachineContractPostEntryComponentSchemas.foreignExchangeSchema());
  }
}
