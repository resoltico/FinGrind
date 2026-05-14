package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for posting request shapes. */
final class MachineContractPostEntrySchemas {
  private MachineContractPostEntrySchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical posting request JSON document.", topLevelFields());
  }

  static Map<String, Object> postEntrySchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(postEntrySchema());
  }

  static ContractRequestShapes.PostEntryRequestShapeDescriptor descriptor() {
    return new ContractRequestShapes.PostEntryRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(lineFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(provenanceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(reversalFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "postingKind", PostingKind.callerSelectableWireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "lineSide", JournalLine.EntrySide.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "actorType", ActorType.wireValues())),
        postEntrySchema());
  }

  private static Map<String, Object> lineSchema() {
    return MachineContractSchemaSupport.objectSchema("One balanced journal line.", lineFields());
  }

  private static Map<String, Object> provenanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Caller-supplied provenance captured before commit.", provenanceFields());
  }

  private static Map<String, Object> reversalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional reversal target descriptor.", reversalFields());
  }

  private static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.POSTING_KIND,
            "Caller-authored posting family. Accepted values are STANDARD and OPENING_BALANCE.",
            MachineContractSchemaSupport.enumStringSchema(
                "Caller-authored posting family.", PostingKind.callerSelectableWireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            "ISO-8601 local date that makes the journal entry effective.",
            MachineContractSchemaSupport.dateStringSchema(
                "ISO-8601 local date that makes the journal entry effective.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.LINES,
            "Balanced non-empty array of journal lines for one currency.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of journal lines for one currency.", lineSchema(), 2)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.PROVENANCE,
            "Caller-supplied request provenance captured before commit.",
            provenanceSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVERSAL,
            "Optional reversal target descriptor for additive reversal postings.",
            reversalSchema()),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.TopLevel.CORRECTION,
            "Legacy correction payloads are hard-broken and no longer accepted."));
  }

  private static List<MachineContractFieldSpec> lineFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
            "Declared book-local account code for this journal line. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Declared book-local account code for this journal line.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.SIDE,
            "Journal side that carries the line amount.",
            MachineContractSchemaSupport.enumStringSchema(
                "Journal side that carries the line amount.", JournalLine.EntrySide.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.AMOUNT,
            "Exact positive money object for this journal line. Every line in one entry must resolve to the same currency unit.",
            MachineContractSchemaSupport.moneyObjectSchema(
                "Exact positive money object for this journal line.", true)));
  }

  private static List<MachineContractFieldSpec> provenanceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            "Stable identifier of the actor that initiated the request.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Stable identifier of the actor that initiated the request.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
            "Actor classification from the live actorType enum vocabulary.",
            MachineContractSchemaSupport.enumStringSchema(
                "Actor classification from the live actorType enum vocabulary.",
                ActorType.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            "Caller-generated command identity for this request.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Caller-generated command identity for this request.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            "Book-local idempotency key used to detect duplicate commit attempts. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Book-local idempotency key used to detect duplicate commit attempts.",
                IdempotencyKey.pattern(),
                IdempotencyKey.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.CAUSATION_ID,
            "Caller-supplied causation identifier for upstream traceability.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Caller-supplied causation identifier for upstream traceability.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.Provenance.CORRELATION_ID,
            "Optional correlation identifier for joining related calls.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Optional correlation identifier for joining related calls.")),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Provenance.RECORDED_AT,
            "Committed audit timestamps are generated by FinGrind, not caller input."),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL,
            "Committed source channel is generated by FinGrind, not caller input."));
  }

  private static List<MachineContractFieldSpec> reversalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID,
            "Existing committed posting identifier that this request reverses.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Existing committed posting identifier that this request reverses.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Reversal.REASON,
            "Human-readable operator explanation attached to this reversal.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Human-readable operator explanation attached to this reversal.")),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Reversal.KIND,
            "Legacy reversal-kind routing is removed; FinGrind is reversal-only."));
  }
}
