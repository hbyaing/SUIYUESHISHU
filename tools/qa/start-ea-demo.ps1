$ErrorActionPreference = "Stop"
$runtime = Get-AppxPackage -Name "OpenAI.CodexPrimaryRuntime*" |
    Sort-Object { [version]$_.Version } -Descending |
    ForEach-Object { Join-Path $_.InstallLocation "dependencies/python/python.exe" } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
    Select-Object -First 1
if (-not $runtime) {
    $runtime = (Get-Command python.exe -ErrorAction Stop).Source
}
& $runtime (Join-Path $PSScriptRoot "start-ea-demo.py")
exit $LASTEXITCODE
