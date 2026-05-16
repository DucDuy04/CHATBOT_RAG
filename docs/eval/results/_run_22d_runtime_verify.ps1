# Task 22D — Playground Compare A/B runtime verify
$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080"
$GoldenPath = Join-Path $PSScriptRoot "..\RAG_GOLDEN_TEST_DOCUMENT.txt"
$Msg = "Mã xác nhận golden là gì?"
$Results = @()

function Mask-Key([string]$k) {
    if ([string]::IsNullOrEmpty($k)) { return "(none)" }
    if ($k.Length -le 8) { return "***" }
    return $k.Substring(0,4) + "..." + $k.Substring($k.Length-4)
}

function New-Chatbot([string]$name) {
    $body = @{ name = $name; description = "22D compare verify"; domain = "eval" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$Base/api/chatbots" -Method Post -Body $body -ContentType "application/json"
}

function Set-ModelConfig([string]$id, [hashtable]$mc) {
    $body = @{ modelConfig = $mc } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Put -Body $body -ContentType "application/json" | Out-Null
}

function Upload-Golden([string]$widgetId) {
    ($json = curl.exe -s -X POST "$Base/api/documents/upload/$widgetId" -F "file=@$GoldenPath") | Out-Null
    return $json | ConvertFrom-Json
}

function Wait-Indexed([string]$docId) {
    $deadline = (Get-Date).AddSeconds(300)
    while ((Get-Date) -lt $deadline) {
        $st = Invoke-RestMethod -Uri "$Base/api/documents/$docId/status" -Method Get
        if ($st.status -eq "INDEXED") { return }
        if ($st.status -eq "FAILED") { throw "INDEX failed" }
        Start-Sleep -Seconds 5
    }
    throw "INDEX timeout"
}

function Invoke-Compare([string]$chatbotId, [hashtable]$configA, [hashtable]$configB) {
    $body = @{
        chatbotId = $chatbotId
        message   = $Msg
        configA   = $configA
        configB   = $configB
    } | ConvertTo-Json -Depth 6
    return Invoke-RestMethod -Uri "$Base/api/playground/compare" -Method Post -Body $body -ContentType "application/json"
}

function Get-RagTopKLogs([int]$sinceSec = 40) {
    docker logs chatbot-backend --since "${sinceSec}s" 2>&1 |
        Select-String -Pattern "\[RAG\] retrieval topK requested="
}

function Get-ChatLlmLogs([int]$sinceSec = 40) {
    docker logs chatbot-backend --since "${sinceSec}s" 2>&1 |
        Select-String -Pattern "\[LLM\] generation options"
}

function Invoke-Chat([string]$apiKey, $temp, $maxTok, $topK) {
    $body = @{ sessionId = [guid]::NewGuid().ToString(); message = $Msg }
    if ($null -ne $temp) { $body.temperature = $temp }
    if ($null -ne $maxTok) { $body.maxTokens = $maxTok }
    if ($null -ne $topK) { $body.topK = $topK }
    $headers = @{ "X-Widget-Key" = $apiKey }
    return Invoke-RestMethod -Uri "$Base/api/chat" -Method Post -Body ($body | ConvertTo-Json) -ContentType "application/json" -Headers $headers
}

function Last-Lines($matches, [int]$n = 4) {
    if ($null -eq $matches -or $matches.Count -eq 0) { return @("(no log)") }
    $start = [Math]::Max(0, $matches.Count - $n)
    $out = @()
    for ($i = $start; $i -lt $matches.Count; $i++) { $out += $matches[$i].Line.Trim() }
    return $out
}

Write-Host "=== Health ==="
Invoke-RestMethod -Uri "$Base/api/chatbots?page=0&size=1" | Out-Null
$qc = Invoke-RestMethod -Uri "http://localhost:6333/collections"
Write-Host "Qdrant collections: $($qc.result.collections.Count)"

Write-Host "`n=== Setup chatbot ==="
$bot = New-Chatbot "22D Playground Compare Verify $(Get-Date -Format 'HHmmss')"
$id = $bot.id
$key = $bot.apiKey
Write-Host "CHATBOT_ID=$id KEY=$(Mask-Key $key)"
Set-ModelConfig $id @{ temperature = 0.2; maxTokens = 256; topK = 5 }
$up = Upload-Golden $id
$docId = if ($up.documentId) { $up.documentId } else { $up.id }
Write-Host "DOC_ID=$docId"
Wait-Indexed $docId
Write-Host "INDEXED"

Write-Host "`n=== Case 1: explicit A/B params ==="
Start-Sleep -Seconds 2
docker logs chatbot-backend --since 1s 2>&1 | Out-Null
$r1 = Invoke-Compare $id @{ topK = 5; temperature = 0.2; maxTokens = 256 } @{ topK = 10; temperature = 0.7; maxTokens = 512 }
$logs1 = @(Get-RagTopKLogs 60)
$Results += [pscustomobject]@{
    Case = "1_explicit_AB"
    Http = 200
    SourcesA = $r1.configA.sources.Count
    SourcesB = $r1.configB.sources.Count
    LatencyA = $r1.configA.latency
    LatencyB = $r1.configB.latency
    RagTopKLogs = (Last-Lines $logs1 6) -join " || "
}

Write-Host "`n=== Case 2: clamp A only ==="
Start-Sleep -Seconds 3
$r2 = Invoke-Compare $id @{ topK = 999; temperature = 999; maxTokens = 999999 } @{ topK = 5; temperature = 0.2; maxTokens = 256 }
$logs2 = @(Get-RagTopKLogs 60)
$Results += [pscustomobject]@{
    Case = "2_clamp_A"
    SourcesA = $r2.configA.sources.Count
    SourcesB = $r2.configB.sources.Count
    RagTopKLogs = (Last-Lines $logs2 4) -join " || "
}

Write-Host "`n=== Case 3: missing partial params ==="
Start-Sleep -Seconds 3
$r3 = Invoke-Compare $id @{ topK = 5 } @{ temperature = 0.7 }
$logs3 = @(Get-RagTopKLogs 60)
$Results += [pscustomobject]@{
    Case = "3_missing_partial"
    SourcesA = $r3.configA.sources.Count
    SourcesB = $r3.configB.sources.Count
    RagTopKLogs = (Last-Lines $logs3 4) -join " || "
}

Write-Host "`n=== Case 4: /api/chat regression ==="
Start-Sleep -Seconds 2
$cr1 = Invoke-Chat $key $null $null $null
$chatLogs1 = @(Get-ChatLlmLogs 30)
Start-Sleep -Seconds 2
$cr2 = Invoke-Chat $key 0.7 512 $null
$chatLogs2 = @(Get-ChatLlmLogs 30)
$Results += [pscustomobject]@{
    Case = "4_chat_modelConfig_fallback"
    Sources = $cr1.sources.Count
    LlmLog = (Last-Lines $chatLogs1 1) -join " || "
}
$Results += [pscustomobject]@{
    Case = "4_chat_request_override"
    Sources = $cr2.sources.Count
    LlmLog = (Last-Lines $chatLogs2 1) -join " || "
}

Write-Host "`n=== SUMMARY ==="
$Results | Format-List
@{
    chatbotId = $id
    apiKeyMasked = (Mask-Key $key)
    docId = $docId
    results = $Results
    note = "Compare path has no [LLM] generation options log; topK via RagRetrievalService only"
    timestamp = (Get-Date).ToString("o")
} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $PSScriptRoot "_run_22d_results.json")

Write-Host "Saved _run_22d_results.json"
