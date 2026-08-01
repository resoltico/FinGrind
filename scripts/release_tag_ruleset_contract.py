#!/usr/bin/env python3
"""Validate the repository rulesets that authorize and preserve FinGrind release tags."""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any

CREATION_RULESET_NAME = "Authorize FinGrind release tag creation"
IMMUTABILITY_RULESET_NAME = "Protect FinGrind release tag immutability"
RELEASE_TAG_PATTERN = "refs/tags/v*"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate the active FinGrind release-tag authorization and immutability rulesets."
    )
    parser.add_argument(
        "--release-owner-id",
        required=True,
        type=int,
        help="Numeric GitHub repository-owner user ID authorized to create release tags.",
    )
    action = parser.add_mutually_exclusive_group()
    action.add_argument(
        "--configuration-plan",
        action="store_true",
        help="Read existing tag-ruleset details and print the safe canonical rulesets still to create.",
    )
    action.add_argument(
        "--print-creation-request",
        action="store_true",
        help="Print the canonical owner-authorized creation-ruleset request JSON.",
    )
    action.add_argument(
        "--print-immutability-request",
        action="store_true",
        help="Print the canonical no-bypass immutability-ruleset request JSON.",
    )
    return parser.parse_args()


def creation_ruleset_request(release_owner_id: int) -> dict[str, Any]:
    return {
        "name": CREATION_RULESET_NAME,
        "target": "tag",
        "enforcement": "active",
        "bypass_actors": [
            {
                "actor_id": release_owner_id,
                "actor_type": "User",
                "bypass_mode": "always",
            }
        ],
        "conditions": {
            "ref_name": {
                "include": [RELEASE_TAG_PATTERN],
                "exclude": [],
            }
        },
        "rules": [{"type": "creation"}],
    }


def immutability_ruleset_request() -> dict[str, Any]:
    return {
        "name": IMMUTABILITY_RULESET_NAME,
        "target": "tag",
        "enforcement": "active",
        "bypass_actors": [],
        "conditions": {
            "ref_name": {
                "include": [RELEASE_TAG_PATTERN],
                "exclude": [],
            }
        },
        "rules": [
            {"type": "update"},
            {"type": "deletion"},
        ],
    }


def require_object(value: Any, label: str, errors: list[str]) -> dict[str, Any] | None:
    if isinstance(value, dict):
        return value
    errors.append(f"{label} must be an object")
    return None


def ruleset_matches_name(rulesets: list[dict[str, Any]], name: str) -> list[dict[str, Any]]:
    return [ruleset for ruleset in rulesets if ruleset.get("name") == name]


def validate_common_ruleset(
    ruleset: dict[str, Any],
    name: str,
    errors: list[str],
) -> None:
    if ruleset.get("source_type") != "Repository":
        errors.append(f"{name} must be repository-owned, not inherited or organization-owned")
    if ruleset.get("target") != "tag":
        errors.append(f"{name} must target tags")
    if ruleset.get("enforcement") != "active":
        errors.append(f"{name} must be active")

    conditions = require_object(ruleset.get("conditions"), f"{name} conditions", errors)
    if conditions is None:
        return
    if set(conditions) != {"ref_name"}:
        errors.append(f"{name} must expose only its release-tag ref_name condition")
    ref_name = require_object(conditions.get("ref_name"), f"{name} ref_name condition", errors)
    if ref_name is None:
        return
    if ref_name != {"include": [RELEASE_TAG_PATTERN], "exclude": []}:
        errors.append(f"{name} must use exactly the {RELEASE_TAG_PATTERN} include and no exclusion")


def rule_types(ruleset: dict[str, Any], name: str, errors: list[str]) -> list[str] | None:
    rules = ruleset.get("rules")
    if not isinstance(rules, list) or not all(isinstance(rule, dict) for rule in rules):
        errors.append(f"{name} must expose one rules array")
        return None
    types: list[str] = []
    for rule in rules:
        rule_type = rule.get("type")
        if not isinstance(rule_type, str) or not rule_type:
            errors.append(f"{name} must not expose a rule without one nonempty type")
            continue
        types.append(rule_type)
    if len(types) != len(set(types)):
        errors.append(f"{name} must not repeat one rule type")
    return types


def validate_creation_ruleset(
    ruleset: dict[str, Any], release_owner_id: int, errors: list[str]
) -> None:
    name = CREATION_RULESET_NAME
    validate_common_ruleset(ruleset, name, errors)
    types = rule_types(ruleset, name, errors)
    if types is not None and set(types) != {"creation"}:
        errors.append(f"{name} must contain exactly the creation rule")
    if ruleset.get("rules") != [{"type": "creation"}]:
        errors.append(f"{name} creation rule must not carry extra parameters or rules")

    bypass_actors = ruleset.get("bypass_actors")
    if not isinstance(bypass_actors, list) or len(bypass_actors) != 1:
        errors.append(
            f"{name} must authorize exactly the repository-owner GitHub user and no other bypass actor"
        )
        return
    actor = bypass_actors[0]
    if not isinstance(actor, dict):
        errors.append(f"{name} bypass actor must be an object")
        return
    actor_id = actor.get("actor_id")
    if (
        actor.get("actor_type") != "User"
        or actor.get("bypass_mode") != "always"
        or isinstance(actor_id, bool)
        or not isinstance(actor_id, int)
        or actor_id != release_owner_id
    ):
        errors.append(
            f"{name} must authorize only repository-owner User ID {release_owner_id} with always bypass"
        )


def validate_immutability_ruleset(ruleset: dict[str, Any], errors: list[str]) -> None:
    name = IMMUTABILITY_RULESET_NAME
    validate_common_ruleset(ruleset, name, errors)
    types = rule_types(ruleset, name, errors)
    if types is not None and set(types) != {"update", "deletion"}:
        errors.append(f"{name} must contain exactly update and deletion rules")
    expected_rules = [
        {"type": "deletion"},
        {"type": "update"},
    ]
    rules = ruleset.get("rules")
    if not isinstance(rules, list) or sorted(
        json.dumps(rule, sort_keys=True) for rule in rules
    ) != sorted(json.dumps(rule, sort_keys=True) for rule in expected_rules):
        errors.append(f"{name} must contain exactly the no-bypass update and deletion tag rules")

    if ruleset.get("bypass_actors") != []:
        errors.append(f"{name} must not grant any bypass actor")


def validate_rulesets(payload: Any, release_owner_id: int) -> list[str]:
    errors: list[str] = []
    if not isinstance(payload, list) or not all(isinstance(item, dict) for item in payload):
        return ["tag-ruleset detail payload must be one JSON array of objects"]
    rulesets = list(payload)
    expected_names = {CREATION_RULESET_NAME, IMMUTABILITY_RULESET_NAME}
    ruleset_names = [ruleset.get("name") for ruleset in rulesets]
    if not all(isinstance(name, str) for name in ruleset_names):
        errors.append("every tag ruleset must expose one string name")
        return errors
    if len(rulesets) != 2 or set(ruleset_names) != expected_names:
        errors.append(
            "must expose exactly the two canonical release-tag rulesets and no other tag ruleset"
        )

    for name in (CREATION_RULESET_NAME, IMMUTABILITY_RULESET_NAME):
        matches = ruleset_matches_name(rulesets, name)
        if len(matches) != 1:
            errors.append(f"must expose exactly one ruleset named {name!r}, found {len(matches)}")
            continue
        if name == CREATION_RULESET_NAME:
            validate_creation_ruleset(matches[0], release_owner_id, errors)
        else:
            validate_immutability_ruleset(matches[0], errors)
    return errors


def configuration_plan(payload: Any, release_owner_id: int) -> tuple[list[str] | None, list[str]]:
    if not isinstance(payload, list) or not all(isinstance(item, dict) for item in payload):
        return None, ["tag-ruleset detail payload must be one JSON array of objects"]

    rulesets = list(payload)
    expected_names = {CREATION_RULESET_NAME, IMMUTABILITY_RULESET_NAME}
    names = [ruleset.get("name") for ruleset in rulesets]
    if not all(isinstance(name, str) for name in names):
        return None, ["tag-ruleset inventory entries must expose string names"]
    unknown_names = [name for name in names if name not in expected_names]
    if unknown_names:
        return None, ["tag-ruleset inventory contains an unexpected ruleset"]
    if len(names) != len(set(names)):
        return None, ["tag-ruleset inventory repeats one canonical ruleset name"]
    if len(rulesets) > 2:
        return None, ["tag-ruleset inventory contains too many rulesets"]

    existing_by_name = {ruleset["name"]: ruleset for ruleset in rulesets}
    errors: list[str] = []
    creation_ruleset = existing_by_name.get(CREATION_RULESET_NAME)
    if creation_ruleset is not None:
        validate_creation_ruleset(creation_ruleset, release_owner_id, errors)
    immutability_ruleset = existing_by_name.get(IMMUTABILITY_RULESET_NAME)
    if immutability_ruleset is not None:
        validate_immutability_ruleset(immutability_ruleset, errors)
    if errors:
        return None, errors

    create: list[str] = []
    if creation_ruleset is None:
        create.append("creation")
    if immutability_ruleset is None:
        create.append("immutability")
    return create, []


def main() -> int:
    args = parse_args()
    if args.release_owner_id <= 0:
        raise SystemExit("release owner ID must be one positive integer")
    if args.print_creation_request:
        print(json.dumps(creation_ruleset_request(args.release_owner_id), sort_keys=True))
        return 0
    if args.print_immutability_request:
        print(json.dumps(immutability_ruleset_request(), sort_keys=True))
        return 0
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"tag-ruleset detail payload must be valid JSON: {exc}") from exc

    if args.configuration_plan:
        create, errors = configuration_plan(payload, args.release_owner_id)
        if errors:
            for error in errors:
                print(error, file=sys.stderr)
            return 1
        assert create is not None
        print(json.dumps({"create": create}, sort_keys=True))
        return 0

    errors = validate_rulesets(payload, args.release_owner_id)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(
        "Verified release-tag governance: repository-owner creation authorization plus no-bypass "
        "update/deletion immutability"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
