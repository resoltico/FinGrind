package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;

/** One published failure descriptor definition before projection into the protocol catalog. */
record BookAdministrationRejectionDescriptorDefinition(
    ContractResponse.FailureCategory category,
    String code,
    String description,
    List<ContractResponse.FieldDescriptor> detailFields) {}
