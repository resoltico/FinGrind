module dev.erst.fingrind.executor {
  exports dev.erst.fingrind.executor;

  requires transitive dev.erst.fingrind.contract;
  requires transitive dev.erst.fingrind.core;
  requires static org.jspecify;
}
