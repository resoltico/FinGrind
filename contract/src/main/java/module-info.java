module dev.erst.fingrind.contract {
  exports dev.erst.fingrind.contract.bookkeeping;
  exports dev.erst.fingrind.contract.discovery;
  exports dev.erst.fingrind.contract.protocol;
  exports dev.erst.fingrind.contract.runtime;
  exports dev.erst.fingrind.contract.workflow;

  requires transitive dev.erst.fingrind.core;
  requires static org.jspecify;
  requires tools.jackson.databind;
}
