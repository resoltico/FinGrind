package dev.erst.fingrind.cli.json;

import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;

/** Marker for CLI-owned success payload records emitted inside success envelopes. */
public interface CliSuccessPayload extends ProtocolSuccessPayload {}
