#!/usr/bin/env python3
"""Resolve whether one FinGrind release tag should own the public latest pointer."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Resolve whether the target release tag should own the public latest pointer."
    )
    parser.add_argument(
        "--release-tag",
        required=True,
        help="Release tag with the v prefix, for example v0.49.0.",
    )
    parser.add_argument(
        "--repository",
        required=True,
        help="GitHub repository slug in owner/name form.",
    )
    return parser.parse_args()


def stable_semver_key(tag_name: str) -> tuple[int, int, int]:
    match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", tag_name.strip())
    if match is None:
        raise ValueError(f"release tag must be one stable vX.Y.Z tag: {tag_name}")
    return tuple(int(group) for group in match.groups())


def main() -> int:
    args = parse_args()
    target_key = stable_semver_key(args.release_tag)
    release_listing = subprocess.run(
        [
            "gh",
            "release",
            "list",
            "--repo",
            args.repository,
            "--exclude-drafts",
            "--exclude-pre-releases",
            "--limit",
            "200",
            "--json",
            "tagName",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    published_tags = json.loads(release_listing.stdout)
    stable_keys = {stable_semver_key(entry["tagName"]) for entry in published_tags}
    stable_keys.add(target_key)
    json.dump(
        {
            "latestPublicationPolicy": "newest-stable-release-only",
            "markLatest": target_key == max(stable_keys),
        },
        sys.stdout,
        sort_keys=True,
    )
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
