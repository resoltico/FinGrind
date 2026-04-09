package dev.erst.fingrind.jazzer.tool;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Describes the structured meaning of a replayed FinGrind Jazzer input. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CliRequestReplayDetails.class, name = "CLI_REQUEST"),
  @JsonSubTypes.Type(value = PostingWorkflowReplayDetails.class, name = "POSTING_WORKFLOW"),
  @JsonSubTypes.Type(value = SqliteBookRoundTripReplayDetails.class, name = "SQLITE_BOOK_ROUND_TRIP")
})
public sealed interface ReplayDetails
    permits CliRequestReplayDetails, PostingWorkflowReplayDetails, SqliteBookRoundTripReplayDetails {}
