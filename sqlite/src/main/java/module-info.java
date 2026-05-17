module dev.erst.fingrind.sqlite {
  exports dev.erst.fingrind.sqlite;

  requires transitive dev.erst.fingrind.contract;
  requires transitive dev.erst.fingrind.core;
  requires transitive dev.erst.fingrind.executor;
  requires tools.jackson.databind;
  requires static org.jspecify;
}
