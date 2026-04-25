package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Focused invariant tests for canonical request-field specs. */
class MachineContractFieldSpecTest {
  @Test
  void helperFactories_publishExpectedPresenceAndDescriptorState() {
    MachineContractFieldSpec required =
        MachineContractFieldSpec.required("field", "Required field.", Map.of("type", "string"));
    MachineContractFieldSpec conditional =
        MachineContractFieldSpec.conditional(
            "field", "Conditional field.", Map.of("type", "string"));
    MachineContractFieldSpec forbidden =
        MachineContractFieldSpec.forbidden("field", "Forbidden field.");

    assertTrue(required.acceptsInput());
    assertTrue(required.requiredInSchema());
    assertFalse(conditional.requiredInSchema());
    assertTrue(conditional.acceptsInput());
    assertFalse(forbidden.acceptsInput());
    assertFalse(forbidden.requiredInSchema());
    assertEquals(
        new ContractRequestShapes.RequestFieldDescriptor(
            "field", RequestFieldPresence.CONDITIONAL, "Conditional field."),
        conditional.descriptor());
  }

  @Test
  void constructor_rejectsForbiddenSchemasAndMissingAcceptedSchemas() {
    IllegalArgumentException forbiddenSchema =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new MachineContractFieldSpec(
                    "field",
                    RequestFieldPresence.FORBIDDEN,
                    "Forbidden field.",
                    Map.of("type", "string")));
    IllegalArgumentException missingSchema =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new MachineContractFieldSpec(
                    "field", RequestFieldPresence.CONDITIONAL, "Conditional field.", null));

    assertEquals(
        "Forbidden field specs must not publish an accepted schema.", forbiddenSchema.getMessage());
    assertEquals("Accepted field specs must publish a schema.", missingSchema.getMessage());
  }
}
