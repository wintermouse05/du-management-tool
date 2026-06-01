$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Stop-ProcessByNameSafe {
    param([string]$Name)

    $processes = Get-Process -Name $Name -ErrorAction SilentlyContinue
    if (-not $processes) {
        Write-Host "No process found for $Name"
        return
    }

    foreach ($process in $processes) {
        try {
            Stop-Process -Id $process.Id -Force -ErrorAction Stop
            Write-Host "Stopped $Name (PID $($process.Id))" -ForegroundColor Green
        } catch {
            Write-Warning "Could not stop $Name (PID $($process.Id)): $($_.Exception.Message)"
        }
    }
}

function Stop-PortOwner {
    param([int]$Port)

    $processIds = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -eq $Port } |
        Select-Object -ExpandProperty OwningProcess -Unique

    if (-not $processIds) {
        Write-Host "No listener found on port $Port"
        return
    }

    foreach ($processId in $processIds) {
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
            Write-Host "Stopped process on port ${Port} (PID $processId)" -ForegroundColor Green
        } catch {
            Write-Warning "Could not stop process on port ${Port} (PID $processId): $($_.Exception.Message)"
        }
    }
}

Write-Step "Stopping ngrok"
Stop-ProcessByNameSafe -Name "ngrok"

Write-Step "Stopping nginx"
Stop-ProcessByNameSafe -Name "nginx"

Write-Step "Stopping backend on port 8080"
Stop-PortOwner -Port 8080

Write-Step "Removing workspace alias X:"
try {
    & "$env:SystemRoot\System32\subst.exe" X: /d | Out-Null
    Write-Host "Removed X: alias" -ForegroundColor Green
} catch {
    Write-Warning "Could not remove X: alias: $($_.Exception.Message)"
}
