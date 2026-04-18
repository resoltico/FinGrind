module dev.erst.fingrind.contract {
  exports dev.erst.fingrind.contract;
  exports dev.erst.fingrind.contract.protocol;

  requires transitive dev.erst.fingrind.core;
  requires static org.jspecify;
}
