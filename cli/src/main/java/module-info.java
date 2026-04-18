module dev.erst.fingrind.cli {
  opens dev.erst.fingrind.cli to
      tools.jackson.databind;

  requires dev.erst.fingrind.contract;
  requires dev.erst.fingrind.core;
  requires dev.erst.fingrind.executor;
  requires dev.erst.fingrind.sqlite;
  requires tools.jackson.databind;
  requires static org.jspecify;
}
