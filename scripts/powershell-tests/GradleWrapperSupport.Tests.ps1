$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($env:FINGRIND_REPOSITORY_ROOT)) {
    throw "FINGRIND_REPOSITORY_ROOT is required for PowerShell quality tests"
}

Describe "Windows Gradle wrapper policy" {
    BeforeAll {
        . (Join-Path $env:FINGRIND_REPOSITORY_ROOT "scripts/gradle-wrapper-support.ps1")
    }

    It "derives cache and build policy from supplied Windows environment facts" {
        $plan = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
            -RepositoryRoot "C:\work dir\FinGrind" `
            -Environment @{
                RUNNER_TEMP = "D:\runner temp"
                TEMP = "E:\temp"
                LOCALAPPDATA = "C:\Users\runner\AppData\Local"
            }

        $plan.ProjectCacheKey | Should -Be "C__work_dir_FinGrind"
        $plan.ProjectCacheRoot | Should -Be "D:\runner temp\fingrind-gradle-project-cache"
        $plan.ProjectCacheDir | Should -Be "D:\runner temp\fingrind-gradle-project-cache\C__work_dir_FinGrind"
        $plan.BuildLogicDir | Should -Be "D:\runner temp\fingrind-gradle-project-cache\C__work_dir_FinGrind\build-logic"
        $plan.JacocoRoot | Should -Be "D:\runner temp\fingrind-gradle-project-cache\C__work_dir_FinGrind\jacoco"
        $plan.ProjectBuildRoot | Should -Be "D:\runner temp\fingrind-gradle-project-cache\C__work_dir_FinGrind\project-build"
        $plan.ShouldExternalizeProjectBuildRoot | Should -BeFalse
        $plan.InvocationLeaseRoot | Should -Be "D:\runner temp\fingrind-gradle-invocation-leases"
        $plan.InvocationLeaseFile | Should -Be "D:\runner temp\fingrind-gradle-invocation-leases\C__work_dir_FinGrind.lease"
    }

    It "preserves explicit policy overrides and externalizes UNC project build roots" {
        $overridden = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
            -RepositoryRoot "C:\work dir\FinGrind" `
            -Environment @{
                FINGRIND_PROJECT_CACHE_KEY = "chosen key"
                FINGRIND_GRADLE_PROJECT_CACHE_ROOT = "Z:\root"
                FINGRIND_GRADLE_PROJECT_CACHE_DIR = "Z:\cache-dir"
                FINGRIND_GRADLE_BUILD_LOGIC_DIR = "Z:\logic"
                FINGRIND_GRADLE_JACOCO_ROOT = "Z:\jacoco"
                FINGRIND_GRADLE_PROJECT_BUILD_ROOT = "Z:\build"
                FINGRIND_GRADLE_INVOCATION_LEASE_ROOT = "Y:\lease"
            }
        $unc = Get-FinGrindWindowsGradleWrapperPlanForEnvironment -RepositoryRoot "\\server\share\repo" -Environment @{}

        $overridden.ProjectCacheKey | Should -Be "chosen_key"
        $overridden.ProjectBuildRoot | Should -Be "Z:\build"
        $overridden.ShouldExternalizeProjectBuildRoot | Should -BeTrue
        $overridden.InvocationLeaseFile | Should -Be "Y:\lease\chosen_key.lease"
        $unc.ProjectCacheKey | Should -Be "__server_share_repo"
        $unc.ShouldExternalizeProjectBuildRoot | Should -BeTrue
        $unc.InvocationLeaseFile | Should -Be "\\server\share\repo\.gradle-invocation-leases\__server_share_repo.lease"
        (Join-FinGrindWindowsPath -ParentPath "C:\cache\\" -ChildPath "\\nested\leaf") | Should -Be "C:\cache\nested\leaf"
    }
}
