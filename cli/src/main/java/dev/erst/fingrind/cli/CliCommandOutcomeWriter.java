package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Writes resolved CLI outcomes and deterministic failures with the canonical exit policy. */
final class CliCommandOutcomeWriter {
  private CliCommandOutcomeWriter() {}

  static <T> int writeResolvedResult(
      ContractDecision<T> decision,
      Consumer<T> successWriter,
      ToIntFunction<T> successExitCode,
      CliFailureResponseWriter failureWriter) {
    return decision.fold(
        result -> {
          successWriter.accept(result);
          return successExitCode.applyAsInt(result);
        },
        failure -> writeDeterministicFailure(failure, failureWriter));
  }

  static int writeDeterministicFailure(
      ContractFailure failure, CliFailureResponseWriter failureWriter) {
    failureWriter.writeDeterministicFailure(CliFailureMapper.contractFailure(failure));
    return CliExecutionPolicy.contractFailureExitCode(failure);
  }
}
