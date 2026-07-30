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
            "api",
            "--paginate",
            "--slurp",
            f"/repos/{args.repository}/releases?per_page=100",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    release_pages = json.loads(release_listing.stdout)
    if not isinstance(release_pages, list) or not all(
        isinstance(page, list) for page in release_pages
    ):
        raise ValueError("GitHub Releases API pagination response must be one array of pages")

    stable_keys: set[tuple[int, int, int]] = set()
    for page in release_pages:
        for release in page:
            if not isinstance(release, dict):
                raise TypeError("GitHub Releases API response must contain release objects")
            draft = release.get("draft")
            prerelease = release.get("prerelease")
            tag_name = release.get("tag_name")
            if not isinstance(draft, bool) or not isinstance(prerelease, bool):
                raise TypeError(
                    "GitHub Releases API response must classify every release as draft and prerelease"
                )
            if draft or prerelease:
                continue
            if not isinstance(tag_name, str):
                raise TypeError("published GitHub release is missing its tag_name")
            stable_keys.add(stable_semver_key(tag_name))
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
