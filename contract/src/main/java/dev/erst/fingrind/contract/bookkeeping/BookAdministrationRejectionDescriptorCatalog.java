package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Published descriptor catalog for book-administration rejection types. */
final class BookAdministrationRejectionDescriptorCatalog {
  private static final Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      DEFINITIONS_BY_DESCRIPTOR = definitions();

  private BookAdministrationRejectionDescriptorCatalog() {}

  static String code(BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    return definition(descriptor).code();
  }

  static List<RejectionDescriptor> descriptors() {
    return List.of(BookAdministrationRejectionDescriptors.Descriptor.values()).stream()
        .map(BookAdministrationRejectionDescriptorCatalog::descriptor)
        .toList();
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      definitions() {
    return BookAdministrationRejectionDescriptorDefinitionSupport.merge(
        bookDefinitions(),
        AccountRegistryRejectionDescriptorDefinitions.definitions(),
        BookPeriodAdministrationRejectionDescriptorDefinitions.definitions(),
        Map.of(
            BookAdministrationRejectionDescriptors.Descriptor.CONTRA_ACCOUNT_INVALID,
            ContraAccountRejectionDescriptor.definition()));
  }

  private static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      bookDefinitions() {
    return Map.ofEntries(
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.BOOK_ALREADY_INITIALIZED,
            preconditionDefinition(
                "book-already-initialized",
                "Book initialization refused because the selected book is already initialized.",
                List.of())),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.BOOK_NOT_INITIALIZED,
            preconditionDefinition(
                "administration-book-not-initialized",
                "Administration command refused because the selected book does not exist or has not been initialized with "
                    + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                    + ".",
                List.of())),
        Map.entry(
            BookAdministrationRejectionDescriptors.Descriptor.BOOK_CONTAINS_SCHEMA,
            preconditionDefinition(
                "book-contains-schema",
                "Book initialization refused because the selected SQLite file already contains schema objects.",
                List.of())));
  }

  private static RejectionDescriptor descriptor(
      BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    BookAdministrationRejectionDescriptorDefinition definition = definition(descriptor);
    return new RejectionDescriptor(
        definition.code(),
        definition.category(),
        definition.description(),
        definition.detailFields(),
        List.of());
  }

  private static BookAdministrationRejectionDescriptorDefinition definition(
      BookAdministrationRejectionDescriptors.Descriptor descriptor) {
    return Objects.requireNonNull(DEFINITIONS_BY_DESCRIPTOR.get(descriptor), "definition");
  }

  private static BookAdministrationRejectionDescriptorDefinition preconditionDefinition(
      String code, String description, List<FieldDescriptor> detailFields) {
    return new BookAdministrationRejectionDescriptorDefinition(
        FailureCategory.PRECONDITION, code, description, List.copyOf(detailFields));
  }
}
