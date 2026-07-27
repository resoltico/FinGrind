package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for the book-local account registry contract. */
public record AccountRegistryDescriptor(
    InitializationRequirement initializationRequirement,
    String redeclarationBehavior,
    List<FieldDescriptor> declareAccountFields,
    List<FieldDescriptor> listFields,
    List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies)
    implements ResponseDescriptorType {
  /** Validates one account-registry descriptor payload. */
  public AccountRegistryDescriptor {
    initializationRequirement =
        ContractDescriptorValidation.requireValue(
            initializationRequirement, "initializationRequirement");
    redeclarationBehavior =
        ContractDescriptorValidation.requireText(redeclarationBehavior, "redeclarationBehavior");
    declareAccountFields =
        ContractDescriptorValidation.copyList(declareAccountFields, "declareAccountFields");
    listFields = ContractDescriptorValidation.copyList(listFields, "listFields");
    enumVocabularies = ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
  }
}
