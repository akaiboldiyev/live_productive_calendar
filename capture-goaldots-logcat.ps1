param(
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$LogPath
)

# This helper is started by collect-goaldots-diagnostics.bat. Keeping stream
# redirection in PowerShell avoids CMD START/redirection parsing failures.
& $AdbPath logcat -v time 2>&1 | Out-File -FilePath $LogPath -Append -Encoding utf8
