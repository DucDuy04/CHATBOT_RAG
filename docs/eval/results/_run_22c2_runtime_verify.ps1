# Task 22C2 runtime verify — temperature / maxTokens LLM params
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
    $body = @{ name = $name; description = "22C2 runtime verify"; domain = "eval" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$Base/api/chatbots" -Method Post -Body $body -ContentType "application/json"
}

function Set-ModelConfig([string]$id, [hashtable]$modelConfig) {
    $body = @{ modelConfig = $modelConfig } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Put -Body $body -ContentType "application/json" | Out-Null
}

function Get-Chatbot([string]$id) {
    Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Get
}

function Upload-Golden([string]$widgetId) {
    $json = curl.exe -s -X POST "$Base/api/documents/upload/$widgetId" -F "file=@$GoldenPath"
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

function Invoke-Chat([string]$apiKey, [string]$message, $temperature, $maxTokens, $topK) {
    $body = @{ sessionId = $SessionId; message = $message }
    if ($null -ne $temperature) { $body.temperature = $temperature }
    if ($null -ne $maxTokens) { $body.maxTokens = $maxTokens }
    if ($null -ne $topK) { $body.topK = $topK }
    $json = $body | ConvertTo-Json
    $headers = @{ "X-Widget-Key" = $apiKey }
    return Invoke-RestMethod -Uri "$Base/api/chat" -Method Post -Body $json -ContentType "application/json" -Headers $headers
}

function Get-LlmLogs([int]$sinceSec = 25) {
    docker logs chatbot-backend --since "${sinceSec}s" 2>&1 |
        Select-String -Pattern "\[LLM\] generation options"
}

function Get-TopKLogs([int]$sinceSec = 25) {
    docker logs chatbot-backend --since "${sinceSec}s" 2>&1 |
        Select-String -Pattern "\[RAG\] retrieval topK"
}

function Last-LogLine($matches) {
    if ($null -eq $matches -or $matches.Count -eq 0) { return "(no log)" }
    return ($matches[-1].Line.Trim())
}

Write-Host "=== Health ==="
$h = Invoke-RestMethod -Uri "$Base/api/chatbots?page=0&size=1" -Method Get
Write-Host "chatbots total: $($h.totalElements)"
$q = Invoke-RestMethod -Uri "http://localhost:6333/collections" -Method Get
Write-Host "qdrant collections: $($q.result.collections.Count)"

Write-Host "`n=== Create chatbot A (modelConfig temp/max/topK) ==="
$botA = New-Chatbot "22C2-Verify-A-$(Get-Date -Format 'HHmmss')"
$idA = $botA.id
$keyA = $botA.apiKey
Write-Host "CHATBOT_ID_A=$idA KEY_A=$(Mask-Key $keyA)"
Set-ModelConfig $idA @{ temperature = 0.2; maxTokens = 256; topK = 5 }
$cfgA = Get-Chatbot $idA
Write-Host "modelConfig A API: $($cfgA.modelConfig | ConvertTo-Json -Compress)"

Write-Host "`n=== Upload golden doc to A ==="
$up = Upload-Golden $idA
$docId = if ($up.documentId) { $up.documentId } else { $up.id }
Write-Host "DOC_ID=$docId"
Wait-Indexed $docId | Out-Null
Write-Host "Document INDEXED"

$msg = "Mã xác nhận golden là gì?"

Write-Host "`n=== Case 1: no temp/max (MODEL_CONFIG 0.2/256) ==="
Start-Sleep -Seconds 2
$r1 = Invoke-Chat $keyA $msg $null $null $null
$log1 = @(Get-LlmLogs 30)
$Results += [pscustomobject]@{ Case="1_modelConfig_fallback"; Sources=$r1.sources.Count; LlmLog=(Last-LogLine $log1) }

Write-Host "`n=== Case 2: temp=0.7 maxTokens=512 (REQUEST) ==="
Start-Sleep -Seconds 2
$r2 = Invoke-Chat $keyA $msg 0.7 512 $null
$log2 = @(Get-LlmLogs 30)
$Results += [pscustomobject]@{ Case="2_request_override"; Sources=$r2.sources.Count; LlmLog=(Last-LogLine $log2) }

Write-Host "`n=== Case 3: temp=999 maxTokens=999999 (clamp 1.0/4096) ==="
Start-Sleep -Seconds 2
$r3 = Invoke-Chat $keyA $msg 999 999999 $null
$log3 = @(Get-LlmLogs 30)
$Results += [pscustomobject]@{ Case="3_clamp"; Sources=$r3.sources.Count; LlmLog=(Last-LogLine $log3) }

Write-Host "`n=== TopK case 1: no topK (MODEL_CONFIG 5) ==="
Start-Sleep -Seconds 2
$rTk1 = Invoke-Chat $keyA $msg $null $null $null
$topKLog1 = @(Get-TopKLogs 30)
$Results += [pscustomobject]@{ Case="topK_no_request"; Sources=$rTk1.sources.Count; TopKLog=(Last-LogLine $topKLog1) }

Write-Host "`n=== TopK case 2: topK=10 (REQUEST) ==="
Start-Sleep -Seconds 2
$rTk2 = Invoke-Chat $keyA $msg $null $null 10
$topKLog2 = @(Get-TopKLogs 30)
$Results += [pscustomobject]@{ Case="topK_request_10"; Sources=$rTk2.sources.Count; TopKLog=(Last-LogLine $topKLog2) }

Write-Host "`n=== Create chatbot B (no modelConfig LLM params) ==="
$botB = New-Chatbot "22C2-Verify-B-$(Get-Date -Format 'HHmmss')"
$idB = $botB.id
$keyB = $botB.apiKey
$cfgB = Get-Chatbot $idB
Write-Host "CHATBOT_ID_B=$idB KEY_B=$(Mask-Key $keyB)"
Write-Host "modelConfig B API: $($cfgB.modelConfig | ConvertTo-Json -Compress)"

Write-Host "`n=== Upload golden doc to B ==="
$upB = Upload-Golden $idB
$docIdB = if ($upB.documentId) { $upB.documentId } else { $upB.id }
Wait-Indexed $docIdB | Out-Null

Write-Host "`n=== Case 4: B no temp/max (DEFAULT 0.1/1500) ==="
Start-Sleep -Seconds 2
$r4 = Invoke-Chat $keyB $msg $null $null $null
$log4 = @(Get-LlmLogs 30)
$Results += [pscustomobject]@{ Case="4_default_fallback"; Sources=$r4.sources.Count; LlmLog=(Last-LogLine $log4) }

Write-Host "`n=== Case 5: OOS source cap smoke ==="
Start-Sleep -Seconds 2
$r5 = Invoke-Chat $keyA "Giá Bitcoin hôm nay là bao nhiêu?" $null $null $null
$ansPreview = if ($r5.answer) { $r5.answer.Substring(0,[Math]::Min(120,$r5.answer.Length)) } else { "" }
$Results += [pscustomobject]@{ Case="5_OOS_bitcoin"; Sources=$r5.sources.Count; AnswerPreview=$ansPreview }

Write-Host "`n=== MySQL ui_config A ==="
docker exec ragchatbot-mysql mysql -uroot -proot ragchatbot -N -e "SELECT JSON_EXTRACT(ui_config, '$.modelConfig.temperature'), JSON_EXTRACT(ui_config, '$.modelConfig.maxTokens'), JSON_EXTRACT(ui_config, '$.modelConfig.topK') FROM widget_configs WHERE id='$idA';" 2>$null

Write-Host "`n=== MySQL ui_config B (temp/max/topK) ==="
docker exec ragchatbot-mysql mysql -uroot -proot ragchatbot -N -e "SELECT JSON_EXTRACT(ui_config, '$.modelConfig.temperature'), JSON_EXTRACT(ui_config, '$.modelConfig.maxTokens'), JSON_EXTRACT(ui_config, '$.modelConfig.topK') FROM widget_configs WHERE id='$idB';" 2>$null

Write-Host "`n=== SUMMARY ==="
$Results | Format-List
$out = @{
    chatbotA = @{ id = $idA; apiKeyMasked = (Mask-Key $keyA); docId = $docId }
    chatbotB = @{ id = $idB; apiKeyMasked = (Mask-Key $keyB); docId = $docIdB }
    results = $Results
    timestamp = (Get-Date).ToString("o")
}
$out | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $PSScriptRoot "_run_22c2_results.json")
Write-Host "Saved _run_22c2_results.json"
