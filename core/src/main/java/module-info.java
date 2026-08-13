module dev.erst.fingrind.core {
  exports dev.erst.fingrind.core;
  exports dev.erst.fingrind.core.attestation;

  requires static org.jspecify;
  requires tools.jackson.core;
  requires tools.jackson.databind;
}
