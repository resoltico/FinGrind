package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import java.util.List;

/** One published failure descriptor definition before projection into the protocol catalog. */
record BookAdministrationRejectionDescriptorDefinition(
    FailureCategory category,
    String code,
    String description,
    List<FieldDescriptor> detailFields) {}
