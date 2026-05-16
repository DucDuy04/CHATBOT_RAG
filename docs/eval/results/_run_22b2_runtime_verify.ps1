# Task 22B2 runtime verify — modelConfig.topK fallback
$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080"
$SessionId = [guid]::NewGuid().ToString()
$GoldenPath = Join-Path $PSScriptRoot "..\RAG_GOLDEN_TEST_DOCUMENT.txt"
$Results = @()

function Mask-Key([string]$k) {
    if ([string]::IsNullOrEmpty($k)) { return "(none)" }
    if ($k.Length -le 8) { return "***" }
    return $k.Substring(0,4) + "..." + $k.Substring($k.Length-4)
}

function New-Chatbot([string]$name) {
    $body = @{ name = $name; description = "22B2 runtime verify"; domain = "eval" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$Base/api/chatbots" -Method Post -Body $body -ContentType "application/json"
    return $r
}

function Set-ModelTopK([string]$id, [int]$topK) {
    $body = @{ modelConfig = @{ topK = $topK } } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Put -Body $body -ContentType "application/json" | Out-Null
}

function Get-Chatbot([string]$id) {
    Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Get
}

function Upload-Golden([string]$widgetId) {
    $uri = "$Base/api/documents/upload/$widgetId"
    $json = curl.exe -s -X POST $uri -F "file=@$GoldenPath"
    return $json | ConvertFrom-Json
}

function Wait-Indexed([string]$docId, [int]$maxSec = 300) {
    $deadline = (Get-Date).AddSeconds($maxSec)
    while ((Get-Date) -lt $deadline) {
        $st = Invoke-RestMethod -Uri "$Base/api/documents/$docId/status" -Method Get
        if ($st.status -eq "INDEXED") { return $st }
        if ($st.status -eq "FAILED") { throw "Document failed: $($st | ConvertTo-Json -Compress)" }
        Start-Sleep -Seconds 5
    }
    throw "Timeout waiting INDEXED for $docId"
}

function Invoke-Chat([string]$apiKey, [string]$message, $topK) {
    $body = @{ sessionId = $SessionId; message = $message }
    if ($null -ne $topK) { $body.topK = $topK }
    $json = $body | ConvertTo-Json
    $headers = @{ "X-Widget-Key" = $apiKey }
    return Invoke-RestMethod -Uri "$Base/api/chat" -Method Post -Body $json -ContentType "application/json" -Headers $headers
}

function Get-TopKLogs([string]$sinceSec = 30) {
    docker logs chatbot-backend --since "${sinceSec}s" 2>&1 |
        Select-String -Pattern "\[RAG\] retrieval topK"
}

Write-Host "=== Create chatbot A (modelConfig.topK=5) ==="
$botA = New-Chatbot "22B2-Verify-A-$(Get-Date -Format 'HHmmss')"
$idA = $botA.id
$keyA = $botA.apiKey
Write-Host "CHATBOT_ID_A=$idA KEY_A=$(Mask-Key $keyA)"
Set-ModelTopK $idA 5
$cfgA = Get-Chatbot $idA
Write-Host "modelConfig A: $($cfgA.modelConfig | ConvertTo-Json -Compress)"

Write-Host "=== Upload golden doc to A ==="
$up = Upload-Golden $idA
$docId = $up.documentId
if (-not $docId) { $docId = $up.id }
Write-Host "DOC_ID=$docId status=$($up.status)"
Wait-Indexed $docId | Out-Null
Write-Host "Document INDEXED"

$msg = "Mã xác nhận golden là gì?"

Write-Host "`n=== Case 1: no topK (expect MODEL_CONFIG effective=5) ==="
docker logs chatbot-backend --since 1s 2>&1 | Out-Null
Start-Sleep -Seconds 1
$r1 = Invoke-Chat $keyA $msg $null
$log1 = @(Get-TopKLogs 20)
$Results += [pscustomobject]@{ Case="1_no_topK_config5"; Sources=$r1.sources.Count; Logs=($log1 -join " | ") }

Write-Host "`n=== Case 2: topK=10 (expect REQUEST effective=10) ==="
Start-Sleep -Seconds 2
$r2 = Invoke-Chat $keyA $msg 10
$log2 = @(Get-TopKLogs 20)
$Results += [pscustomobject]@{ Case="2_topK10"; Sources=$r2.sources.Count; Logs=($log2 -join " | ") }

Write-Host "`n=== Case 3: topK=999 (expect REQUEST effective=30) ==="
Start-Sleep -Seconds 2
$r3 = Invoke-Chat $keyA $msg 999
$log3 = @(Get-TopKLogs 20)
$Results += [pscustomobject]@{ Case="3_topK999"; Sources=$r3.sources.Count; Logs=($log3 -join " | ") }

Write-Host "`n=== Create chatbot B (no modelConfig update) ==="
$botB = New-Chatbot "22B2-Verify-B-$(Get-Date -Format 'HHmmss')"
$idB = $botB.id
$keyB = $botB.apiKey
$cfgB = Get-Chatbot $idB
Write-Host "CHATBOT_ID_B=$idB KEY_B=$(Mask-Key $keyB)"
Write-Host "modelConfig B (API response): $($cfgB.modelConfig | ConvertTo-Json -Compress)"

Write-Host "=== Upload golden doc to B ==="
$upB = Upload-Golden $idB
$docIdB = $upB.documentId
if (-not $docIdB) { $docIdB = $upB.id }
Wait-Indexed $docIdB | Out-Null

Write-Host "`n=== Case 4: B no topK (expect DEFAULT effective=30) ==="
Start-Sleep -Seconds 2
$r4 = Invoke-Chat $keyB $msg $null
$log4 = @(Get-TopKLogs 20)
$Results += [pscustomobject]@{ Case="4_B_no_topK"; Sources=$r4.sources.Count; Logs=($log4 -join " | ") }

Write-Host "`n=== Case 5: OOS smoke ==="
Start-Sleep -Seconds 2
$r5 = Invoke-Chat $keyA "Giá Bitcoin hôm nay bao nhiêu?" $null
$log5 = @(Get-TopKLogs 20)
$Results += [pscustomobject]@{ Case="5_OOS"; Sources=$r5.sources.Count; AnswerPreview=$r5.answer.Substring(0,[Math]::Min(80,$r5.answer.Length)); Logs=($log5 -join " | ") }

Write-Host "`n=== SUMMARY ==="
$Results | Format-Table -AutoSize
$Results | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $PSScriptRoot "_run_22b2_results.json")
Write-Host "Saved _run_22b2_results.json"

# DB inspect B ui_config for modelConfig.topK (optional)
Write-Host "`n=== MySQL ui_config B (modelConfig only) ==="
docker exec ragchatbot-mysql mysql -uroot -proot ragchatbot -N -e "SELECT JSON_EXTRACT(ui_config, '$.modelConfig.topK') FROM widget_configs WHERE id='$idB';" 2>$null

Write-Host "`n=== MySQL ui_config A topK ==="
docker exec ragchatbot-mysql mysql -uroot -proot ragchatbot -N -e "SELECT JSON_EXTRACT(ui_config, '$.modelConfig.topK') FROM widget_configs WHERE id='$idA';" 2>$null
