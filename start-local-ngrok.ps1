param(
    [switch]$SkipFrontendBuild,
    [switch]$NoNgrok,
    [string]$EnvFile = ".env",
    [string]$FrontendUrl = "http://localhost:8088",
    [string]$WebSocketOrigins = "https://*.ngrok-free.dev,https://*.ngrok-free.app,https://*.ngrok.app,http://localhost:8088,http://localhost:5173,http://localhost:8080",
    [string]$NotificationEmailEnabled = "false",
    [string]$MailUsername = "",
    [string]$MailAppPassword = "",
    [string]$MailFrom = "",
    [string]$ChatopsEnabled = "false",
    [string]$ChatopsUrl = "",
    [string]$ChatopsToken = "",
    [string]$ChatopsAssistantId = "",
    [string]$ChatopsChannelId = "",
    [string]$ChatopsConfigEncryptionSecret = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$backendDir = Join-Path $repoRoot "du-management-backend"
$frontendDir = Join-Path $repoRoot "du-management-frontend"

$workspaceDrive = "X:"
$nginxPort = 8088
$backendPort = 8080

$logStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backendLog = Join-Path $repoRoot "backend-ngrok-$logStamp.log"
$ngrokOutLog = Join-Path $repoRoot "ngrok.out-$logStamp.log"
$ngrokErrLog = Join-Path $repoRoot "ngrok.err-$logStamp.log"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Wait-ForPort {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $listener = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -eq $Port } |
            Select-Object -First 1

        if ($listener) {
            return $true
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

function Stop-PortOwner {
    param([int]$Port)

    $pids = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -eq $Port } |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($processId in $pids) {
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
        } catch {
            Write-Warning "Could not stop PID $processId on port ${Port}: $($_.Exception.Message)"
        }
    }
}

function Require-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing required command: $Name"
    }
}

function New-CmdSetStatement {
    param(
        [string]$Name,
        [string]$Value
    )

    $safeValue = $Value -replace '"', '\"'
    return "set `"$Name=$safeValue`""
}

function Read-DotEnvFile {
    param([string]$Path)

    $values = @{}

    if (-not (Test-Path $Path)) {
        return $values
    }

    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()

        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $key = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $values[$key] = $value
    }

    return $values
}

function Get-DotEnvValue {
    param(
        [hashtable]$Values,
        [string[]]$Keys
    )

    foreach ($key in $Keys) {
        if ($Values.ContainsKey($key)) {
            return $Values[$key]
        }
    }

    return $null
}

function Apply-DotEnvValue {
    param(
        [string]$ParameterName,
        [hashtable]$Values,
        [string[]]$Keys
    )

    if ($PSBoundParameters.ContainsKey($ParameterName)) {
        return
    }

    $value = Get-DotEnvValue -Values $Values -Keys $Keys
    if ($null -eq $value) {
        return
    }

    Set-Variable -Name $ParameterName -Value $value -Scope Script
}

Write-Step "Checking required tools"
Require-Command "java"
Require-Command "ngrok"
Require-Command "npm.cmd"

if (-not (Test-Path $backendDir)) {
    throw "Backend folder not found: $backendDir"
}

if (-not (Test-Path $frontendDir)) {
    throw "Frontend folder not found: $frontendDir"
}

$envFilePath = $EnvFile
if (-not [System.IO.Path]::IsPathRooted($envFilePath)) {
    $envFilePath = Join-Path $repoRoot $envFilePath
}

$dotenvValues = Read-DotEnvFile -Path $envFilePath

Apply-DotEnvValue -ParameterName "FrontendUrl" -Values $dotenvValues -Keys @("FRONTEND_URL", "APP_FRONTEND_URL")
Apply-DotEnvValue -ParameterName "WebSocketOrigins" -Values $dotenvValues -Keys @("WEB_SOCKET_ORIGINS", "APP_WEBSOCKET_ALLOWED_ORIGINS")
Apply-DotEnvValue -ParameterName "NotificationEmailEnabled" -Values $dotenvValues -Keys @("NOTIFICATION_EMAIL_ENABLED")
Apply-DotEnvValue -ParameterName "MailUsername" -Values $dotenvValues -Keys @("MAIL_USERNAME")
Apply-DotEnvValue -ParameterName "MailAppPassword" -Values $dotenvValues -Keys @("MAIL_APP_PASSWORD")
Apply-DotEnvValue -ParameterName "MailFrom" -Values $dotenvValues -Keys @("MAIL_FROM")
Apply-DotEnvValue -ParameterName "ChatopsEnabled" -Values $dotenvValues -Keys @("CHATOPS_ENABLED")
Apply-DotEnvValue -ParameterName "ChatopsUrl" -Values $dotenvValues -Keys @("CHATOPS_URL")
Apply-DotEnvValue -ParameterName "ChatopsToken" -Values $dotenvValues -Keys @("CHATOPS_TOKEN")
Apply-DotEnvValue -ParameterName "ChatopsAssistantId" -Values $dotenvValues -Keys @("CHATOPS_ASSISTANT_ID")
Apply-DotEnvValue -ParameterName "ChatopsChannelId" -Values $dotenvValues -Keys @("CHATOPS_CHANNEL_ID")
Apply-DotEnvValue -ParameterName "ChatopsConfigEncryptionSecret" -Values $dotenvValues -Keys @("CHATOPS_CONFIG_ENCRYPTION_SECRET")

if (-not $PSBoundParameters.ContainsKey("SkipFrontendBuild")) {
    $skipBuildValue = Get-DotEnvValue -Values $dotenvValues -Keys @("SKIP_FRONTEND_BUILD")
    if ($skipBuildValue -and $skipBuildValue.ToLowerInvariant() -eq "true") {
        $SkipFrontendBuild = $true
    }
}

if (-not $PSBoundParameters.ContainsKey("NoNgrok")) {
    $noNgrokValue = Get-DotEnvValue -Values $dotenvValues -Keys @("NO_NGROK")
    if ($noNgrokValue -and $noNgrokValue.ToLowerInvariant() -eq "true") {
        $NoNgrok = $true
    }
}

if (-not $SkipFrontendBuild) {
    Write-Step "Building frontend production bundle"
    Push-Location $frontendDir
    try {
        & npm.cmd run build
    } finally {
        Pop-Location
    }
}

Write-Step "Preparing X: workspace alias for nginx"
try {
    & "$env:SystemRoot\System32\subst.exe" $workspaceDrive /d | Out-Null
} catch {
}
& "$env:SystemRoot\System32\subst.exe" $workspaceDrive $repoRoot | Out-Null

$nginxExe = Join-Path $workspaceDrive ".tools\nginx-1.30.0\nginx.exe"
$nginxPrefix = Join-Path $workspaceDrive ".tools\nginx-1.30.0\"
$nginxConfig = Join-Path $workspaceDrive "nginx\du-management.local.conf"

if (-not (Test-Path $nginxExe)) {
    throw "nginx executable not found: $nginxExe"
}

if (-not (Test-Path $nginxConfig)) {
    throw "nginx config not found: $nginxConfig"
}

Write-Step "Stopping previous local listeners if needed"
Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-Process nginx -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Stop-PortOwner -Port $backendPort
Stop-PortOwner -Port $nginxPort

Write-Step "Starting backend on port $backendPort"

$backendEnvStatements = @(
    (New-CmdSetStatement -Name "APP_WEBSOCKET_ALLOWED_ORIGINS" -Value $WebSocketOrigins)
    (New-CmdSetStatement -Name "APP_FRONTEND_URL" -Value $FrontendUrl)
    (New-CmdSetStatement -Name "NOTIFICATION_EMAIL_ENABLED" -Value $NotificationEmailEnabled)
    (New-CmdSetStatement -Name "MAIL_USERNAME" -Value $MailUsername)
    (New-CmdSetStatement -Name "MAIL_APP_PASSWORD" -Value $MailAppPassword)
    (New-CmdSetStatement -Name "MAIL_FROM" -Value $MailFrom)
    (New-CmdSetStatement -Name "CHATOPS_ENABLED" -Value $ChatopsEnabled)
    (New-CmdSetStatement -Name "CHATOPS_URL" -Value $ChatopsUrl)
    (New-CmdSetStatement -Name "CHATOPS_TOKEN" -Value $ChatopsToken)
    (New-CmdSetStatement -Name "CHATOPS_ASSISTANT_ID" -Value $ChatopsAssistantId)
    (New-CmdSetStatement -Name "CHATOPS_CHANNEL_ID" -Value $ChatopsChannelId)
    (New-CmdSetStatement -Name "CHATOPS_CONFIG_ENCRYPTION_SECRET" -Value $ChatopsConfigEncryptionSecret)
)

$backendCommand = ($backendEnvStatements -join " && ") + " && mvnw.cmd spring-boot:run > `"$backendLog`" 2>&1"
Start-Process -WindowStyle Hidden -FilePath "cmd.exe" -ArgumentList "/c", $backendCommand -WorkingDirectory $backendDir | Out-Null

if (-not (Wait-ForPort -Port $backendPort -TimeoutSeconds 180)) {
    Write-Host ""
    Write-Host "Backend log tail:" -ForegroundColor Yellow
    if (Test-Path $backendLog) {
        Get-Content -Tail 80 $backendLog
    }
    throw "Backend did not start on port $backendPort in time."
}

Write-Step "Validating nginx config"
& $nginxExe -p $nginxPrefix -c $nginxConfig -t | Out-Host

Write-Step "Starting nginx on port $nginxPort"
Start-Process -WindowStyle Hidden -FilePath $nginxExe -ArgumentList "-p", $nginxPrefix, "-c", $nginxConfig -WorkingDirectory $nginxPrefix | Out-Null

if (-not (Wait-ForPort -Port $nginxPort -TimeoutSeconds 30)) {
    throw "nginx did not start on port $nginxPort in time."
}

$localHome = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$nginxPort"
$localApiDocs = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$nginxPort/api-docs"

Write-Host ""
Write-Host "Frontend local: http://127.0.0.1:$nginxPort" -ForegroundColor Green
Write-Host "Local status codes: / -> $($localHome.StatusCode), /api-docs -> $($localApiDocs.StatusCode)" -ForegroundColor Green
Write-Host "Mail enabled: $NotificationEmailEnabled | ChatOps enabled: $ChatopsEnabled" -ForegroundColor Green
if (Test-Path $envFilePath) {
    Write-Host "Loaded .env file: $envFilePath" -ForegroundColor Green
}

if ($NoNgrok) {
    Write-Host ""
    Write-Host "ngrok was skipped. Run this when you want a public URL:" -ForegroundColor Yellow
    Write-Host "ngrok http $nginxPort"
    exit 0
}

Write-Step "Starting ngrok"

$ngrokExe = (Get-Command ngrok).Source
Start-Process -WindowStyle Hidden -FilePath $ngrokExe -ArgumentList "http", "$nginxPort", "--log=stdout" -RedirectStandardOutput $ngrokOutLog -RedirectStandardError $ngrokErrLog | Out-Null

$publicUrl = $null
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline -and -not $publicUrl) {
    Start-Sleep -Seconds 2
    try {
        $tunnels = Invoke-RestMethod "http://127.0.0.1:4040/api/tunnels"
        $publicUrl = $tunnels.tunnels |
            Where-Object { $_.public_url -like "https://*" } |
            Select-Object -ExpandProperty public_url -First 1
    } catch {
    }
}

Write-Host ""
if ($publicUrl) {
    Write-Host "Public ngrok URL: $publicUrl" -ForegroundColor Green
} else {
    Write-Host "ngrok started, but public URL was not detected automatically." -ForegroundColor Yellow
    Write-Host "Check http://127.0.0.1:4040/api/tunnels or $ngrokOutLog"
}

Write-Host "Inspector: http://127.0.0.1:4040" -ForegroundColor Green
Write-Host "Backend log: $backendLog" -ForegroundColor Green
Write-Host "ngrok logs: $ngrokOutLog and $ngrokErrLog" -ForegroundColor Green
