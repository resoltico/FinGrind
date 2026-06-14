from __future__ import annotations

import argparse
import io
import json
import os
import sys
import tarfile
import zipfile
from hashlib import sha256
from pathlib import Path


def extract_job_surface(args: argparse.Namespace) -> int:
    workflow = Path(args.workflow).read_text(encoding="utf-8")
    start = workflow.index(f"  {args.job}:\n")
    end = workflow.index(f"\n  {args.next_job}:\n", start)
    sys.stdout.write(workflow[start:end])
    return 0


def build_release_fixtures(args: argparse.Namespace) -> int:
    fixture_root = Path(args.fixture_root)
    asset_names = json.loads(args.release_assets_json)
    release_assets_root = fixture_root / "release-assets"
    release_assets_root.mkdir(parents=True, exist_ok=True)
    archive_digests: dict[str, str] = {}

    for asset_name in asset_names:
        asset_path = release_assets_root / asset_name
        if asset_name.endswith(".sha256"):
            continue
        if asset_name.endswith(".zip"):
            with zipfile.ZipFile(asset_path, "w") as archive:
                archive.writestr("payload.txt", f"{asset_name}\n")
        else:
            asset_path.write_text(f"{asset_name}\n", encoding="utf-8")
        archive_digests[asset_name] = sha256(asset_path.read_bytes()).hexdigest()

    for asset_name in asset_names:
        asset_path = release_assets_root / asset_name
        if not asset_name.endswith(".sha256"):
            continue
        archive_name = asset_name[: -len(".sha256")]
        asset_path.write_text(
            f"{archive_digests[archive_name]}  {archive_name}\n",
            encoding="utf-8",
        )

    good_zip = fixture_root / "good-source.zip"
    good_tar = fixture_root / "good-source.tar.gz"
    bad_zip = fixture_root / "bad-source.zip"
    bad_checksum_root = fixture_root / "bad-checksum-assets"
    bad_checksum_root.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(good_zip, "w") as archive:
        archive.writestr("owner-repo-123456/README.md", "public archive\n")

    with tarfile.open(good_tar, "w:gz") as archive:
        data = b"public archive\n"
        member = tarfile.TarInfo("owner-repo-123456/README.md")
        member.size = len(data)
        archive.addfile(member, io.BytesIO(data))

    with zipfile.ZipFile(bad_zip, "w") as archive:
        archive.writestr("owner-repo-123456/AGENTS.md", "should not ship\n")

    for asset_name in asset_names:
        source = release_assets_root / asset_name
        target = bad_checksum_root / asset_name
        target.write_bytes(source.read_bytes())
    if asset_names:
        first_archive = next(name for name in asset_names if not name.endswith(".sha256"))
        checksum_path = bad_checksum_root / f"{first_archive}.sha256"
        checksum_path.write_text(f"{'0' * 64}  {first_archive}\n", encoding="utf-8")
    return 0


def fake_gh(args: argparse.Namespace) -> int:
    argv = args.gh_args
    mode = os.environ.get("FAKE_GH_MODE", "success")
    tag = os.environ.get("FAKE_GH_TAG", "v0.0.0")
    repo = os.environ.get("FAKE_GH_REPOSITORY", "resoltico/FinGrind")
    release_url = os.environ.get("FAKE_GH_RELEASE_URL", "https://example.invalid/release")
    good_zip = Path(os.environ.get("FAKE_GH_GOOD_ZIP", ""))
    good_tar = Path(os.environ.get("FAKE_GH_GOOD_TAR", ""))
    bad_zip = Path(os.environ.get("FAKE_GH_BAD_ZIP", ""))
    asset_root = Path(os.environ.get("FAKE_GH_ASSET_ROOT", ""))
    bad_checksum_root = Path(os.environ.get("FAKE_GH_BAD_CHECKSUM_ROOT", ""))
    asset_names = json.loads(os.environ.get("FAKE_GH_ASSETS_JSON", "[]"))
    private_reporting_enabled = os.environ.get("FAKE_GH_PRIVATE_REPORTING_ENABLED", "true")

    if argv[:2] == ["repo", "view"]:
        if argv[2:] != ["--json", "nameWithOwner", "--jq", ".nameWithOwner"]:
            return 1
        print(repo)
        return 0

    if argv[:2] == ["release", "view"]:
        if len(argv) < 5 or argv[2] != tag:
            return 1
        remaining = argv[3:]
        requested_repo = ""
        if remaining[:1] == ["--repo"]:
            requested_repo = remaining[1]
            remaining = remaining[2:]
        if requested_repo and requested_repo != repo:
            return 1
        if remaining[:1] != ["--json"]:
            return 1
        json_field = remaining[1]
        if len(remaining) == 2:
            if json_field != "assets":
                return 1
            assets = [
                {
                    "name": name,
                    "apiUrl": f"https://api.github.com/repos/resoltico/FinGrind/releases/assets/{index + 1}",
                }
                for index, name in enumerate(asset_names)
            ]
            print(json.dumps({"assets": assets}))
            return 0
        if remaining[2:3] != ["--jq"]:
            return 1
        jq_query = remaining[3]
        if (json_field, jq_query) == ("tagName", ".tagName"):
            print(tag)
            return 0
        if (json_field, jq_query) == ("isDraft", ".isDraft"):
            print("false")
            return 0
        if (json_field, jq_query) == ("isPrerelease", ".isPrerelease"):
            print("false")
            return 0
        if (json_field, jq_query) == ("url", ".url"):
            print(release_url)
            return 0
        if json_field == "assets" and "index(" in jq_query:
            asset_name = jq_query.split('index("', 1)[1].split('")', 1)[0]
            print("true" if asset_name in asset_names else "false")
            return 0
        return 1

    if argv[:2] == ["release", "download"]:
        if len(argv) < 4 or argv[2] != tag:
            return 1
        remaining = argv[3:]
        requested_asset = ""
        destination_dir = ""
        index = 0
        while index < len(remaining):
            argument = remaining[index]
            if argument == "--pattern":
                requested_asset = remaining[index + 1]
                index += 2
            elif argument == "--dir":
                destination_dir = remaining[index + 1]
                index += 2
            elif argument == "--repo":
                index += 2
            elif argument == "--clobber":
                index += 1
            else:
                return 1
        if not requested_asset or not destination_dir:
            return 1
        Path(destination_dir).mkdir(parents=True, exist_ok=True)
        source_root = (
            bad_checksum_root
            if mode == "bad-checksum" and requested_asset.endswith(".sha256")
            else asset_root
        )
        (Path(destination_dir) / requested_asset).write_bytes(
            (source_root / requested_asset).read_bytes()
        )
        return 0

    if argv[:2] == ["attestation", "verify"]:
        return 1 if mode == "bad-attestation" else 0

    if argv[:1] == ["api"]:
        remaining = argv[1:]
        if remaining[:2] == ["--method", "GET"]:
            remaining = remaining[2:]
        if remaining[:2] == ["-H", "Accept: application/octet-stream"]:
            remaining = remaining[2:]
        endpoint = remaining[0]
        if endpoint == f"/repos/{repo}/private-vulnerability-reporting":
            if remaining[1:] != ["--jq", ".enabled"]:
                return 1
            print(private_reporting_enabled)
            return 0
        if endpoint == f"/repos/{repo}/zipball/{tag}":
            sys.stdout.buffer.write((bad_zip if mode == "bad-archive" else good_zip).read_bytes())
            return 0
        if endpoint == f"/repos/{repo}/tarball/{tag}":
            sys.stdout.buffer.write(good_tar.read_bytes())
            return 0
        if endpoint.startswith(f"/repos/{repo}/releases/assets/"):
            asset_id = int(endpoint.rsplit("/", 1)[1])
            asset_name = asset_names[asset_id - 1]
            source_root = (
                bad_checksum_root
                if mode == "bad-checksum" and asset_name.endswith(".sha256")
                else asset_root
            )
            sys.stdout.buffer.write((source_root / asset_name).read_bytes())
            return 0
        return 1

    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Shared Python helpers for GitHub release shell regressions."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    extract = subparsers.add_parser("extract-job-surface")
    extract.add_argument("--workflow", required=True)
    extract.add_argument("--job", required=True)
    extract.add_argument("--next-job", required=True)
    extract.set_defaults(handler=extract_job_surface)

    fixtures = subparsers.add_parser("build-release-fixtures")
    fixtures.add_argument("--fixture-root", required=True)
    fixtures.add_argument("--release-assets-json", required=True)
    fixtures.set_defaults(handler=build_release_fixtures)

    fake = subparsers.add_parser("fake-gh")
    fake.add_argument("gh_args", nargs=argparse.REMAINDER)
    fake.set_defaults(handler=fake_gh)

    return parser


def main(argv: list[str]) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
