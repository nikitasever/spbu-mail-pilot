param([switch]$Test, [string]$Output = (Join-Path $PSScriptRoot 'build/spbu-mail-pilot.apk'))
$ErrorActionPreference = 'Stop'
$arguments = @((Join-Path $PSScriptRoot 'build.py'), '--output', $Output)
if ($Test) { $arguments += '--test' }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
