package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.OutputMode;

/** Default stdout selection policy for one command with selectable output modes. */
public record SelectableOutputDefaultsDescriptor(
    OutputMode interactiveTerminal, OutputMode redirectedStdout)
    implements ContractDiscoveryDescriptor {
  /** Validates one selectable-output default descriptor payload. */
  public SelectableOutputDefaultsDescriptor {
    interactiveTerminal =
        ContractDescriptorValidation.requireValue(interactiveTerminal, "interactiveTerminal");
    redirectedStdout =
        ContractDescriptorValidation.requireValue(redirectedStdout, "redirectedStdout");
  }
}
