package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** One typed example or operator-note step attached to a public protocol operation. */
public sealed interface ProtocolExampleStep
    permits ProtocolExampleStep.Command, ProtocolExampleStep.Note {
  /** Returns the canonical text carried by this example step. */
  String text();

  /** Builds one executable command example step. */
  static Command command(String text) {
    return new Command(text);
  }

  /** Builds one operator guidance note that is not itself a command example. */
  static Note note(String text) {
    return new Note(text);
  }

  /** One executable command example step. */
  record Command(String text) implements ProtocolExampleStep {
    public Command {
      text = ContractDescriptorValidation.requireText(text, "text");
    }
  }

  /** One operator guidance note that accompanies command examples. */
  record Note(String text) implements ProtocolExampleStep {
    public Note {
      text = ContractDescriptorValidation.requireText(text, "text");
    }
  }
}
