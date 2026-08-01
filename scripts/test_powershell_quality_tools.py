"""Run the complete regression suite for PowerShell quality-tool provisioning."""

from __future__ import annotations

import unittest

from test_powershell_quality_tools_metadata import PowerShellQualityToolsMetadataTest
from test_powershell_quality_tools_provisioning import PowerShellQualityToolsProvisioningTest

__all__ = ["PowerShellQualityToolsMetadataTest", "PowerShellQualityToolsProvisioningTest"]


if __name__ == "__main__":
    unittest.main(verbosity=2)
