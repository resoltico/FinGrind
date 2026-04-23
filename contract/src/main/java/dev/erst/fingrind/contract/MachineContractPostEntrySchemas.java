package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for posting request shapes. */
final class MachineContractPostEntrySchemas {
  private MachineContractPostEntrySchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical posting request JSON document.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            MachineContractSchemaSupport.dateStringSchema("ISO-8601 local effective date."),
            ProtocolPostEntryFields.TopLevel.LINES,
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty journal lines.", lineSchema(), 2),
            ProtocolPostEntryFields.TopLevel.PROVENANCE,
            provenanceSchema(),
            ProtocolPostEntryFields.TopLevel.REVERSAL,
            reversalSchema()),
        List.of(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            ProtocolPostEntryFields.TopLevel.LINES,
            ProtocolPostEntryFields.TopLevel.PROVENANCE));
  }

  static Map<String, Object> postEntrySchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(postEntrySchema());
  }

  private static Map<String, Object> lineSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One balanced journal line.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
            MachineContractSchemaSupport.nonBlankStringSchema("Declared book-local account code."),
            ProtocolPostEntryFields.JournalLine.SIDE,
            MachineContractSchemaSupport.enumStringSchema(
                "Journal side carried by this line.", JournalLine.EntrySide.wireValues()),
            ProtocolPostEntryFields.JournalLine.CURRENCY_CODE,
            MachineContractSchemaSupport.nonBlankStringSchema("Three-letter ISO currency code."),
            ProtocolPostEntryFields.JournalLine.AMOUNT,
            MachineContractSchemaSupport.decimalAmountStringSchema(
                "Plain decimal string greater than zero without exponent notation.")),
        List.of(
            ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
            ProtocolPostEntryFields.JournalLine.SIDE,
            ProtocolPostEntryFields.JournalLine.CURRENCY_CODE,
            ProtocolPostEntryFields.JournalLine.AMOUNT));
  }

  private static Map<String, Object> provenanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Caller-supplied provenance captured before commit.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Stable actor identifier."),
            ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
            MachineContractSchemaSupport.enumStringSchema(
                "Live actor type.", ActorType.wireValues()),
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-generated command identity."),
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            MachineContractSchemaSupport.nonBlankStringSchema("Book-local idempotency key."),
            ProtocolPostEntryFields.Provenance.CAUSATION_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Upstream causation identifier."),
            ProtocolPostEntryFields.Provenance.CORRELATION_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Optional correlation identifier.")),
        List.of(
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            ProtocolPostEntryFields.Provenance.CAUSATION_ID));
  }

  private static Map<String, Object> reversalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional reversal target descriptor.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID,
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Committed posting identifier to reverse."),
            ProtocolPostEntryFields.Reversal.REASON,
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Human-readable operator explanation.")),
        List.of(
            ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID,
            ProtocolPostEntryFields.Reversal.REASON));
  }
}
