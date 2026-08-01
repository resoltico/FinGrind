"""Aggregate every PowerShell runtime regression module for the shell gate."""

from __future__ import annotations

import unittest


def load_tests(
    loader: unittest.TestLoader,
    _tests: unittest.TestSuite,
    _pattern: str | None,
) -> unittest.TestSuite:
    """Load the split regression modules when this suite is executed directly."""

    return loader.loadTestsFromNames(
        [
            "test_powershell_runtime_metadata",
            "test_powershell_runtime_provisioning",
        ]
    )


if __name__ == "__main__":
    unittest.main(verbosity=2)
