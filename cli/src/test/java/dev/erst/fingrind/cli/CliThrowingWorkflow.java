package dev.erst.fingrind.cli;

/** Base workflow that maps every CLI workflow operation to one deterministic runtime failure. */
abstract class CliThrowingWorkflow extends CliBookWorkflowAdapter {
  @Override
  protected RuntimeException unexpectedInvocation(String operationName) {
    return failure(operationName);
  }

  protected abstract RuntimeException failure(String operationName);
}
