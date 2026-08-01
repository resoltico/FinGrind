package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolForeignExchangeRequestFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for the owned foreign-exchange discovery surface and request templates. */
class ForeignExchangeDiscoveryTemplateCoverageTest {
  @Test
  void foreignExchangeTemplatesAndRequestShapes_publishCanonicalFxSurface() {
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor openingPositionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION));

    assertNull(saleTemplate.foreignExchange());
    assertNull(openingPositionTemplate.foreignExchange());

    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor saleDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.SALE_SETTLED);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor openingPositionDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.OPENING_POSITION);

    assertEquals(
        RequestFieldPresence.REQUIRED,
        fieldNamed(
                saleDescriptor.foreignExchangeFields(),
                ProtocolForeignExchangeRequestFields.ForeignExchange.TRANSACTION_AMOUNT)
            .presence());
    assertEquals(
        RequestFieldPresence.REQUIRED,
        fieldNamed(
                saleDescriptor.quotedRateFields(),
                ProtocolForeignExchangeRequestFields.QuotedRate.QUOTE_SOURCE)
            .presence());
    assertEquals(
        List.of("SPOT_TRANSACTION", "UNREALIZED_REMEASUREMENT"),
        vocabularyNamed(
                saleDescriptor, ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND)
            .values());
    assertEquals(
        Optional.empty(),
        vocabularyNamedOptional(
            openingPositionDescriptor,
            ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND));
    assertEquals(
        RequestFieldPresence.FORBIDDEN,
        fieldNamed(
                openingPositionDescriptor.foreignExchangeFields(),
                ProtocolForeignExchangeRequestFields.ForeignExchange.TRANSACTION_AMOUNT)
            .presence());
    assertEquals(
        RequestFieldPresence.FORBIDDEN,
        fieldNamed(
                openingPositionDescriptor.quotedRateFields(),
                ProtocolForeignExchangeRequestFields.QuotedRate.QUOTE_SOURCE)
            .presence());
  }

  private static ContractRequestShapes.RequestFieldDescriptor fieldNamed(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String name) {
    return fields.stream().filter(field -> name.equals(field.name())).findFirst().orElseThrow();
  }

  private static ContractRequestShapes.EnumVocabularyDescriptor vocabularyNamed(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor descriptor, String name) {
    return descriptor.enumVocabularies().stream()
        .filter(vocabulary -> name.equals(vocabulary.name()))
        .findFirst()
        .orElseThrow();
  }

  private static Optional<ContractRequestShapes.EnumVocabularyDescriptor> vocabularyNamedOptional(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor descriptor, String name) {
    return descriptor.enumVocabularies().stream()
        .filter(vocabulary -> name.equals(vocabulary.name()))
        .findFirst();
  }
}
