<#
.SYNOPSIS
    同時啟動書籍比價入口網站的後端（Spring Boot）與前端（Next.js）開發伺服器。

.DESCRIPTION
    各開一個新的 PowerShell 視窗：一個在 backend\ 跑 .\mvnw.cmd spring-boot:run，
    一個在 frontend\ 跑 npm run dev。兩邊的 log 因此分開，各自 Ctrl+C 就能單獨停掉。

    新視窗用 -NoExit 開啟，是為了萬一啟動失敗，錯誤訊息還留在畫面上，
    而不是視窗一閃就沒了。

    這支 script 刻意不做的事：
      - 不啟動也不檢查 SQL Server。那是服務層級的事，而且需要管理員權限。
      - 不自動 kill 佔用連接埠的程序。佔著 8080 的往往就是上一次沒關掉的後端，
        但也可能是別的東西，該不該殺由人決定。
      - 不做 production build。這是開發用的。

.EXAMPLE
    .\start-dev.ps1
#>

$ErrorActionPreference = 'Stop'

# 以 script 自己的所在位置定位，不依賴呼叫者的當前目錄。
$backendPath = Join-Path $PSScriptRoot 'backend'
$frontendPath = Join-Path $PSScriptRoot 'frontend'

# ── 前置檢查：先擋掉會讓人白等一個視窗的兩件事 ─────────────────

if (-not (Test-Path -LiteralPath (Join-Path $frontendPath 'node_modules'))) {
    Write-Host '前端的相依套件還沒安裝。請先執行：' -ForegroundColor Red
    Write-Host '    cd frontend; npm install' -ForegroundColor Yellow
    exit 1
}

<#
    回報誰正在聽這個連接埠，沒人聽就回 $null。

    自動變數 $PID 是目前這個 PowerShell 的行程編號，不可覆寫，所以這裡用 $ownerId。
#>
function Get-PortListener {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }

    $ownerId = $connection.OwningProcess
    $process = Get-Process -Id $ownerId -ErrorAction SilentlyContinue
    $name = if ($process) { $process.ProcessName } else { '未知程序' }

    return [pscustomobject]@{ Port = $Port; ProcessId = $ownerId; Name = $name }
}

$busy = @(
    (Get-PortListener -Port 8080),
    (Get-PortListener -Port 3000)
) | Where-Object { $null -ne $_ }

if ($busy.Count -gt 0) {
    Write-Host '連接埠已經被佔用，沒有啟動任何東西：' -ForegroundColor Red
    foreach ($listener in $busy) {
        Write-Host ("    {0} ← {1} (PID {2})" -f $listener.Port, $listener.Name, $listener.ProcessId) -ForegroundColor Yellow
    }
    Write-Host ''
    Write-Host '若確定那是上一次沒關掉的伺服器，停掉它再重跑這支 script：' -ForegroundColor Gray
    Write-Host ("    Stop-Process -Id {0}" -f $busy[0].ProcessId) -ForegroundColor Gray
    exit 1
}

# ── 啟動 ───────────────────────────────────────────────────

Start-Process -FilePath 'powershell' `
    -WorkingDirectory $backendPath `
    -ArgumentList '-NoExit', '-Command', '.\mvnw.cmd spring-boot:run'

Start-Process -FilePath 'powershell' `
    -WorkingDirectory $frontendPath `
    -ArgumentList '-NoExit', '-Command', 'npm run dev'

Write-Host ''
Write-Host '已在兩個新視窗啟動：' -ForegroundColor Green
Write-Host '    前端    http://localhost:3000'
Write-Host '    後端 API http://localhost:8080/api/works'
Write-Host '    管理後台 http://localhost:8080/admin'
Write-Host ''
Write-Host 'Spring Boot 要花十來秒才會開始聽 8080；前端連得上之前會先看到錯誤，屬正常。' -ForegroundColor Gray
