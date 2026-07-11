package dev.erst.fingrind.jazzer.tool;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Describes the structured meaning of a replayed FinGrind Jazzer input. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CliRequestReplayDetails.class, name = "CLI_REQUEST"),
  @JsonSubTypes.Type(value = UnparsedCliRequestReplayDetails.class, name = "CLI_REQUEST_UNPARSED"),
  @JsonSubTypes.Type(value = LedgerPlanReplayDetails.class, name = "LEDGER_PLAN_REQUEST"),
  @JsonSubTypes.Type(
      value = ParsedLedgerPlanShapeReplayDetails.class,
      name = "LEDGER_PLAN_REQUEST_SHAPE_ONLY"),
  @JsonSubTypes.Type(
      value = UnparsedLedgerPlanReplayDetails.class,
      name = "LEDGER_PLAN_REQUEST_UNPARSED"),
  @JsonSubTypes.Type(value = PostingWorkflowReplayDetails.class, name = "POSTING_WORKFLOW"),
  @JsonSubTypes.Type(
      value = UnparsedPostingWorkflowReplayDetails.class,
      name = "POSTING_WORKFLOW_UNPARSED"),
  @JsonSubTypes.Type(
      value = InventoryCostingMathReplayDetails.class,
      name = "INVENTORY_COSTING_MATH"),
  @JsonSubTypes.Type(
      value = SqliteBookRoundTripReplayDetails.class,
      name = "SQLITE_BOOK_ROUND_TRIP"),
  @JsonSubTypes.Type(
      value = UnparsedSqliteBookRoundTripReplayDetails.class,
      name = "SQLITE_BOOK_ROUND_TRIP_UNPARSED")
})
public sealed interface ReplayDetails
    permits CliRequestReplayDetails,
        UnparsedCliRequestReplayDetails,
        LedgerPlanReplayDetails,
        ParsedLedgerPlanShapeReplayDetails,
        UnparsedLedgerPlanReplayDetails,
        PostingWorkflowReplayDetails,
        UnparsedPostingWorkflowReplayDetails,
        InventoryCostingMathReplayDetails,
        SqliteBookRoundTripReplayDetails,
        UnparsedSqliteBookRoundTripReplayDetails {}
