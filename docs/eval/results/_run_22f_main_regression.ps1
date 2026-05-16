# Task 22F — /api/chat main regression after model controls
$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080"
$GoldenPath = "e:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\RAG_GOLDEN_TEST_DOCUMENT.txt"
$OutFile = Join-Path $PSScriptRoot "22f_raw_eval_output.txt"
$GoldenFileName = "RAG_GOLDEN_TEST_DOCUMENT.txt"

function Mask-Key($k) {
    if ([string]::IsNullOrEmpty($k)) { return "(none)" }
    if ($k.Length -le 8) { return "***" }
    return $k.Substring(0, 8) + "..." + $k.Substring($k.Length - 4)
}

function Invoke-Chat($apiKey, $message, $bodyExtra) {
    $body = @{ sessionId = [guid]::NewGuid().ToString(); message = $message }
    if ($bodyExtra) {
        foreach ($k in $bodyExtra.Keys) { $body[$k] = $bodyExtra[$k] }
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress))
    $headers = @{ "X-Widget-Key" = $apiKey }
    return Invoke-RestMethod -Uri "$Base/api/chat" -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes
}

function Get-Logs($pattern, $sinceSec = 45) {
    @(docker logs chatbot-backend --since "${sinceSec}s" 2>&1 | Select-String -Pattern $pattern)
}

function Has-GoldenSource($sources) {
    if (-not $sources) { return $false }
    foreach ($s in $sources) {
        $fn = if ($s.fileName) { $s.fileName } else { $s.file_name }
        if ($fn -and $fn -like "*GOLDEN*") { return $true }
    }
    return $false
}

function Verdict-Case($id, $answer, $sourceCount, $sources) {
    $a = if ($answer) { $answer.ToLowerInvariant() } else { "" }
    $tags = @()
    switch ($id) {
        "GQ-F01" {
            if ($a -match "alphademo") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "FAIL"; tags = @("RETRIEVAL") }
        }
        "GQ-F02" {
            if ($a -match "golden-vn-2026-714") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "FAIL"; tags = @("RETRIEVAL", "GENERATION") }
        }
        "GQ-F03" {
            if ($a -match "\b500\b") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "PARTIAL"; tags = @("RETRIEVAL") }
        }
        "GQ-F04" {
            if ($a -match "billing" -and $a -match "technical" -and ($a -match "other|4 giờ|4 giờ làm")) {
                return @{ v = "PASS"; tags = $tags }
            }
            return @{ v = "FAIL"; tags = @("RETRIEVAL") }
        }
        "GQ-L01" {
            $ok = ($a -match "24" -or $a -match "email") -and ($a -match "15" -or $a -match "chat") -and ($a -match "bên thứ ba" -or $a -match "ủy quyền")
            if ($ok) { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "PARTIAL"; tags = @("GENERATION") }
        }
        "GQ-L02" {
            if ($a -match "basic" -and $a -match "pro" -and $a -match "business") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "FAIL"; tags = @("TABLE_PARSE", "RETRIEVAL") }
        }
        "GQ-T01" {
            if ($a -match "99000") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "FAIL"; tags = @("TABLE_PARSE", "HALLUCINATION") }
        }
        "GQ-T02" {
            if ($a -match "ưu tiên" -or $a -match "uu tien") {
                if ($a -match "không có thông tin kênh") { return @{ v = "PARTIAL"; tags = @("GENERATION") } }
                return @{ v = "PASS"; tags = $tags }
            }
            return @{ v = "FAIL"; tags = @("TABLE_PARSE") }
        }
        "GQ-C01" {
            if ($a -match "\b3\b" -or $a -match "ba gói" -or $a -match "3 gói") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "FAIL"; tags = @("GENERATION") }
        }
        "GQ-O01" {
            if ($a -match "\d{1,3}[,.]?\d*\s*(usd|vnd)" -or ($a -match "tỷ giá" -and $a -match "\d")) {
                return @{ v = "FAIL"; tags = @("HALLUCINATION", "OUT_OF_SCOPE_HANDLING") }
            }
            if ($a -match "không" -or $a -match "khong") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "PARTIAL"; tags = @("OUT_OF_SCOPE_HANDLING") }
        }
        "GQ-O02" {
            if ($a -match "ceo.*là [A-ZĐÀÁẢÃẠ]" -or ($a -match "tên.*là [A-Z]" -and $a -notmatch "không")) {
                return @{ v = "FAIL"; tags = @("HALLUCINATION") }
            }
            if ($a -match "không" -or $a -match "khong") { return @{ v = "PASS"; tags = $tags } }
            return @{ v = "PARTIAL"; tags = @("OUT_OF_SCOPE_HANDLING") }
        }
        "GQ-D01" {
            if (Has-GoldenSource $sources) { return @{ v = "FAIL"; tags = @("DELETE") } }
            if ($a -match "golden-vn-2026-714" -and $a -notmatch "không") {
                return @{ v = "FAIL"; tags = @("DELETE", "HALLUCINATION") }
            }
            return @{ v = "PASS"; tags = $tags }
        }
        default { return @{ v = "UNKNOWN"; tags = $tags } }
    }
}

$goldenCases = @(
    @{ id = "GQ-F01"; q = "Tên pháp lý đầy đủ của công ty trong tài liệu là gì?" },
    @{ id = "GQ-F02"; q = "Mã xác nhận baseline (golden) trong tài liệu là gì?" },
    @{ id = "GQ-F03"; q = "Gói Pro có bao nhiêu lượt hỏi AI mỗi tháng theo bảng giá?" },
    @{ id = "GQ-F04"; q = "Ở quy trình xử lý yêu cầu, bước 2 làm gì?" },
    @{ id = "GQ-L01"; q = "Liệt kê đầy đủ ba chính sách hỗ trợ được nêu trong mục chính sách." },
    @{ id = "GQ-L02"; q = "Trong bảng gói dịch vụ, có những tên gói nào?" },
    @{ id = "GQ-T01"; q = "Giá gói Basic là bao nhiêu VNĐ theo bảng?" },
    @{ id = "GQ-T02"; q = "Gói Business được hỗ trợ theo kênh nào trong bảng?" },
    @{ id = "GQ-C01"; q = "Bảng gói dịch vụ có tất cả bao nhiêu hàng gói (số gói)?" },
    @{ id = "GQ-O01"; q = "Tỷ giá USD/VND hôm nay là bao nhiêu?" },
    @{ id = "GQ-O02"; q = "CEO của AlphaDemo tên đầy đủ là gì?" }
)

"" | Out-File $OutFile -Encoding utf8

Write-Host "=== Setup ==="
$bot = Invoke-RestMethod -Uri "$Base/api/chatbots" -Method Post -ContentType "application/json" -Body (@{
    name = "22F Chat Main Regression"; description = "task 22F"; domain = "eval"
} | ConvertTo-Json)
$id = $bot.id
$key = $bot.apiKey
Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Put -ContentType "application/json" -Body (@{
    modelConfig = @{ topK = 5; temperature = 0.2; maxTokens = 256 }
} | ConvertTo-Json -Depth 5) | Out-Null

$boundary = [guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($GoldenPath)
$bodyLines = @(
    "--$boundary", 'Content-Disposition: form-data; name="chatbotId"', "", $id,
    "--$boundary", 'Content-Disposition: form-data; name="files"; filename="RAG_GOLDEN_TEST_DOCUMENT.txt"',
    "Content-Type: text/plain", "", [System.Text.Encoding]::UTF8.GetString($fileBytes), "--$boundary--"
) -join "`r`n"
$upload = Invoke-RestMethod -Uri "$Base/api/documents/upload" -Method Post -ContentType "multipart/form-data; boundary=$boundary" -Body $bodyLines
$docId = $upload[0].id
$chunkCount = $upload[0].chunkCount
for ($i = 0; $i -lt 80; $i++) {
    Start-Sleep -Seconds 3
    $st = Invoke-RestMethod -Uri "$Base/api/documents/$docId/status" -Method Get
    if ($st.status -eq "INDEXED") { break }
    if ($st.status -eq "FAILED") { throw "INDEX FAILED" }
}
"META_JSON=" + (@{ chatbotId = $id; apiKeyMasked = (Mask-Key $key); docId = $docId; chunkCount = $chunkCount; status = $st.status } | ConvertTo-Json -Compress) | Out-File $OutFile -Encoding utf8

$smokeMsg = "Mã xác nhận golden là gì?"
$smokeResults = @()

Write-Host "=== Model params smoke (before golden) ==="
Start-Sleep -Seconds 2
$rMc = Invoke-Chat $key $smokeMsg $null
$logsMc = Get-Logs "generation options|retrieval topK source=MODEL_CONFIG"
$smokeResults += @{ case = "smoke_modelConfig"; logs = ($logsMc | ForEach-Object { $_.Line.Trim() }) -join " || " }

Start-Sleep -Seconds 3
$rOv = Invoke-Chat $key $smokeMsg @{ topK = 10; temperature = 0.7; maxTokens = 512 }
$logsOv = Get-Logs "generation options tempSource=REQUEST|retrieval topK source=REQUEST"
$smokeResults += @{ case = "smoke_request_override"; logs = ($logsOv | ForEach-Object { $_.Line.Trim() }) -join " || " }

Start-Sleep -Seconds 3
$rCl = Invoke-Chat $key $smokeMsg @{ topK = 999; temperature = 999; maxTokens = 999999 }
$logsCl = Get-Logs "effectiveMaxTokens=4096|effective=30|effectiveTemperature=1"
$smokeResults += @{ case = "smoke_clamp"; logs = ($logsCl | ForEach-Object { $_.Line.Trim() }) -join " || " }

"SMOKE_JSON=" + ($smokeResults | ConvertTo-Json -Compress -Depth 5) | Add-Content $OutFile -Encoding utf8

Write-Host "=== Golden 11 cases ==="
$caseRows = @()
foreach ($c in $goldenCases) {
    Start-Sleep -Seconds 2
    $resp = Invoke-Chat $key $c.q $null
    $sc = @($resp.sources).Count
    $vd = Verdict-Case $c.id $resp.answer $sc $resp.sources
    $row = @{
        caseId = $c.id
        question = $c.q
        answerPreview = if ($resp.answer.Length -gt 200) { $resp.answer.Substring(0, 200) + "..." } else { $resp.answer }
        sourceCount = $sc
        verdict22F = $vd.v
        tags = $vd.tags -join ","
    }
    $caseRows += $row
    "CHAT_JSON=" + ($row | ConvertTo-Json -Compress -Depth 4) | Add-Content $OutFile -Encoding utf8
    Write-Host "$($c.id) sc=$sc $($vd.v)"
}

Write-Host "=== Delete + GQ-D01 ==="
Invoke-RestMethod -Uri "$Base/api/documents/$docId" -Method Delete | Out-Null
Start-Sleep -Seconds 3
$dResp = Invoke-Chat $key "Mã xác nhận baseline (golden) trong tài liệu là gì?" $null
$dSc = @($dResp.sources).Count
$dVd = Verdict-Case "GQ-D01" $dResp.answer $dSc $dResp.sources
$dRow = @{ caseId = "GQ-D01"; sourceCount = $dSc; verdict22F = $dVd.v; answerPreview = $dResp.answer.Substring(0, [Math]::Min(200, $dResp.answer.Length)) }
"D01_JSON=" + ($dRow | ConvertTo-Json -Compress) | Add-Content $OutFile -Encoding utf8
$caseRows += $dRow
Write-Host "GQ-D01 sc=$dSc $($dVd.v)"

$pass = @($caseRows | Where-Object { $_.verdict22F -eq "PASS" }).Count
$partial = @($caseRows | Where-Object { $_.verdict22F -eq "PARTIAL" }).Count
$fail = @($caseRows | Where-Object { $_.verdict22F -eq "FAIL" }).Count
$maxSrc = ($caseRows | Measure-Object -Property sourceCount -Maximum).Maximum
$oosFail = @($caseRows | Where-Object { $_.caseId -like "GQ-O*" -and $_.verdict22F -eq "FAIL" }).Count
$delFail = if ($dVd.v -eq "FAIL") { 1 } else { 0 }

"SUMMARY_JSON=" + (@{
    pass = $pass; partial = $partial; fail = $fail; total = 12
    oosFail = $oosFail; deleteLeak = $delFail; maxSourceCount = $maxSrc
    sourceCapFactOk = ($caseRows | Where-Object { $_.caseId -notlike "GQ-O*" -and $_.caseId -ne "GQ-D01" -and $_.sourceCount -gt 5 }).Count -eq 0
    sourceCapOosOk = ($caseRows | Where-Object { $_.caseId -like "GQ-O*" -and $_.sourceCount -gt 2 }).Count -eq 0
} | ConvertTo-Json -Compress) | Add-Content $OutFile -Encoding utf8

Write-Host "DONE PASS=$pass PARTIAL=$partial FAIL=$fail"
