package dev.erst.fingrind.cli;

/** Workflow stub that throws the same runtime failure for every CLI workflow operation. */
class CliExplodingWorkflow extends CliThrowingWorkflow {
  private final RuntimeException failure;

  CliExplodingWorkflow(RuntimeException failure) {
    this.failure = failure;
  }

  @Override
  protected RuntimeException failure(String operationName) {
    return failure;
  }
}
