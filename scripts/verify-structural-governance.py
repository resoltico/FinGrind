#!/usr/bin/env python3
"""Verify non-Java structural-governance surfaces for FinGrind."""

from __future__ import annotations

import sys

from structural_governance.cli import main

if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
