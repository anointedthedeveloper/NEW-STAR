# Builds Drift and creates the Windows installer with jpackage.
# Requirements (Windows 10/11): JDK 21 (with jpackage) on PATH, and WiX Toolset 3.x or 4+ for -Type exe/msi.
# Usage:  .\scripts\package.ps1            -> installer (.exe)
#         .\scripts\package.ps1 -Type app-image   -> portable folder, no WiX needed
param([ValidateSet('exe','msi','app-image')][string]$Type = 'exe')
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

& .\mvnw.cmd -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }

Remove-Item -Recurse -Force target\jpackage-input, target\dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force target\jpackage-input\lib | Out-Null
Copy-Item target\drift.jar target\jpackage-input\
Copy-Item target\lib\*.jar target\jpackage-input\lib\
$version = (& .\mvnw.cmd -q -B help:evaluate '-Dexpression=project.version' -DforceStdout | Select-Object -Last 1).Trim()

$args = @(
  '--type', $Type, '--name', 'Drift', '--app-version', $version, '--vendor', 'Drift',
  '--description', 'A customizable desktop companion for Windows',
  '--input', 'target\jpackage-input', '--main-jar', 'drift.jar', '--main-class', 'com.drift.DriftLauncher',
  '--icon', 'packaging\drift.ico', '--dest', 'target\dist',
  '--add-modules', 'java.base,java.desktop,java.logging,java.management,java.naming,java.sql,java.xml,java.prefs,jdk.unsupported,jdk.crypto.ec',
  '--java-options', '-Dfile.encoding=UTF-8', '--java-options', '-Xmx512m'
)
if ($Type -ne 'app-image') {
  # per-user install (no administrator rights), Start Menu shortcut, user can choose the folder
  $args += @('--win-per-user-install', '--win-menu', '--win-menu-group', 'Drift', '--win-shortcut-prompt', '--win-dir-chooser',
             '--win-upgrade-uuid', '6f6b0f0e-3b1d-4e7f-9d55-1f0d6a2c8a11')
}
& jpackage @args
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
Write-Host "Done. Output in target\dist"
