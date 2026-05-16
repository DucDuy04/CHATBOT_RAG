# Task 23B3 — PDF sparse continuation table merge runtime verify
$ErrorActionPreference = "Stop"
$RepoRoot = "e:\chatbot-rag-workspace\CHATBOT_RAG"
$Base = "http://localhost:8080"
$PdfPath = Join-Path $RepoRoot "docs\eval\manual\HeThongQuanLyYeuCauPhucKhao.pdf"
$OutJson = Join-Path $PSScriptRoot "_run_23b3_results.json"
$Entities = @(
    "NguoiDung", "ThongBao", "SinhVien", "GiangVien", "MonHoc", "KetQuaHocTap",
    "PhongKhaoThi", "YeuCauPhucKhao", "LichSuPhucKhao", "Khoa", "TuiBaiThi", "BienBan"
)
$Page14Entities = @("LichSuPhucKhao", "Khoa", "TuiBaiThi", "BienBan")

function Mask-Key($k) {
    if ([string]::IsNullOrEmpty($k)) { return "(none)" }
    if ($k.Length -le 8) { return "***" }
    return $k.Substring(0, 4) + "..." + $k.Substring($k.Length - 4)
}

function Invoke-Chat($apiKey, $message) {
    $body = @{ sessionId = [guid]::NewGuid().ToString(); message = $message }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress))
    return Invoke-RestMethod -Uri "$Base/api/chat" -Method Post -Headers @{ "X-Widget-Key" = $apiKey } -ContentType "application/json; charset=utf-8" -Body $bytes
}

function Count-Entities($text, $list) {
    $found = @()
    $missing = @()
    foreach ($e in $list) {
        if ($text -and $text -match [regex]::Escape($e)) { $found += $e } else { $missing += $e }
    }
    return @{ found = $found; missing = $missing; count = $found.Count }
}

function Score-Target($answer) {
    $r = Count-Entities $answer $Entities
    $p14 = Count-Entities $answer $Page14Entities
    if ($r.count -ge 11 -and $p14.count -eq 4) { return "PASS" }
    if ($r.count -ge 9 -and $p14.count -ge 2) { return "PARTIAL" }
    if ($p14.count -eq 0 -and $r.count -le 8) { return "FAIL" }
    return "PARTIAL"
}

$results = @{
    timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    task = "23B3"
    pdfPath = $PdfPath
    pdfExists = (Test-Path $PdfPath)
}

if (-not $results.pdfExists) {
    $results.blocked = "BLOCKED_NO_PDF_ARTIFACT"
    $results | ConvertTo-Json -Depth 8 | Set-Content $OutJson -Encoding UTF8
    Write-Host "BLOCKED: PDF not found at $PdfPath"
    exit 2
}

# Wait backend healthy
for ($i = 0; $i -lt 40; $i++) {
    try {
        Invoke-RestMethod -Uri "$Base/api/chatbots?page=0&size=1" | Out-Null
        break
    } catch {
        Start-Sleep -Seconds 3
    }
}

$bot = Invoke-RestMethod -Uri "$Base/api/chatbots" -Method Post -ContentType "application/json" -Body (@{
    name = "23B3 PDF Sparse Continuation Runtime"
    description = "23B3 verify"
    domain = "eval.local"
} | ConvertTo-Json)
$id = $bot.id
$apiKey = $bot.apiKey
$results.chatbotId = $id
$results.widgetKeyMasked = Mask-Key $apiKey

Invoke-RestMethod -Uri "$Base/api/chatbots/$id" -Method Put -ContentType "application/json" -Body (@{
    modelConfig = @{ topK = 10; temperature = 0.2; maxTokens = 1024 }
} | ConvertTo-Json) | Out-Null

$boundary = [guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($PdfPath)
$fileName = [System.IO.Path]::GetFileName($PdfPath)
$bodyLines = @(
    "--$boundary",
    "Content-Disposition: form-data; name=`"chatbotId`"",
    "",
    $id,
    "--$boundary",
    "Content-Disposition: form-data; name=`"files`"; filename=`"$fileName`"",
    "Content-Type: application/pdf",
    ""
)
$bodyStart = [System.Text.Encoding]::UTF8.GetBytes(($bodyLines -join "`r`n") + "`r`n")
$bodyEnd = [System.Text.Encoding]::UTF8.GetBytes("`r`n--$boundary--`r`n")
$bodyAll = New-Object byte[] ($bodyStart.Length + $fileBytes.Length + $bodyEnd.Length)
[Buffer]::BlockCopy($bodyStart, 0, $bodyAll, 0, $bodyStart.Length)
[Buffer]::BlockCopy($fileBytes, 0, $bodyAll, $bodyStart.Length, $fileBytes.Length)
[Buffer]::BlockCopy($bodyEnd, 0, $bodyAll, $bodyStart.Length + $fileBytes.Length, $bodyEnd.Length)

$upload = Invoke-RestMethod -Uri "$Base/api/documents/upload" -Method Post -ContentType "multipart/form-data; boundary=$boundary" -Body $bodyAll
$docId = $upload[0].id
if (-not $docId) { $docId = $upload.id }
$results.documentId = $docId

$status = $null
for ($i = 0; $i -lt 120; $i++) {
    Start-Sleep -Seconds 3
    $status = Invoke-RestMethod -Uri "$Base/api/documents/$docId/status"
    if ($status.status -eq "INDEXED") { break }
    if ($status.status -eq "FAILED") { break }
}
$results.uploadStatus = $status.status
$results.chunkCount = $status.chunkCount

Start-Sleep -Seconds 2
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$mergeRaw = cmd /c "docker logs chatbot-backend --since 600s 2>&1"
$ErrorActionPreference = $prevEap
$merge1314 = @($mergeRaw | Select-String -Pattern "page=14.*page=13|page=14 → page=13|SPARSE_CONTINUATION")
$mergeAny = @($mergeRaw | Select-String -Pattern "Table MERGED continuation")
$accept13 = @($mergeRaw | Select-String -Pattern "Table ACCEPTED page=13")
$reject14 = @($mergeRaw | Select-String -Pattern "Table REJECTED page=14")
$results.parserMerge1314Lines = @($merge1314 | ForEach-Object { $_.Line })
$results.parserMerge1314Seen = ($merge1314.Count -gt 0)
$results.parserMergeAnyLines = @($mergeAny | Select-Object -First 5 | ForEach-Object { $_.Line })
$results.parserPage13Accepted = ($accept13.Count -gt 0)
$results.parserPage14Rejected = ($reject14.Count -gt 0)

$docBin = $docId.Replace("-", "")
$sqlChunks = @(cmd /c "docker exec ragchatbot-mysql mysql -uroot -proot ragchatbot -N -e `"SELECT chunk_type, LEFT(content, 120) FROM document_chunks WHERE document_id = UNHEX('$docBin') AND deleted_at IS NULL ORDER BY chunk_index LIMIT 80;`" 2>&1")
$chunkText = ($sqlChunks -join "`n")
$results.sqlChunkEntityCheck = @{
    all12 = (Count-Entities $chunkText $Entities)
    page14 = (Count-Entities $chunkText $Page14Entities)
}

$scrollBody = @{
    filter = @{
        must = @(
            @{ key = "document_id"; match = @{ value = $docId } }
            @{ key = "widgetId"; match = @{ value = $id } }
        )
    }
    limit = 100
    with_payload = $true
} | ConvertTo-Json -Depth 6
$scroll = Invoke-RestMethod -Uri "http://localhost:6333/collections/documents/points/scroll" -Method Post -ContentType "application/json" -Body $scrollBody
$payloadText = ($scroll.result.points | ForEach-Object { $_.payload | ConvertTo-Json -Compress }) -join " "
$results.qdrantPointCount = $scroll.result.points.Count
$results.qdrantEntityCheck = @{
    all12 = (Count-Entities $payloadText $Entities)
    page14 = (Count-Entities $payloadText $Page14Entities)
}

$targetMsg = [System.Text.Encoding]::UTF8.GetString(@(
    0x42,0xE1,0xBA,0xA3,0x6E,0x67,0x20,0x63,0xC3,0xA1,0x63,0x20,0x74,0x68,0xE1,0xBB,0xB1,0x63,0x20,
    0x76,0xC3,0xA0,0x20,0x74,0x68,0x75,0xE1,0xBB,0x99,0x63,0x20,0x74,0xC3,0xAD,0x6E,0x68,0x20,0x67,
    0xE1,0xBB,0x93,0x6D,0x20,0x6E,0x68,0xE1,0xBB,0xAF,0x6E,0x67,0x20,0x74,0x68,0xE1,0xBB,0xB1,0x63,0x20,
    0x74,0x68,0xE1,0xBB,0x83,0x20,0x6E,0xC3,0xA0,0x6F,0x3F
))
$chatTarget = Invoke-Chat $apiKey $targetMsg
$ans = $chatTarget.answer
$results.targetQuestion = @{
    message = $targetMsg
    answerPreview = if ($ans.Length -gt 500) { $ans.Substring(0, 500) + "..." } else { $ans }
    sourceCount = $chatTarget.sources.Count
    entityCheck = (Count-Entities $ans $Entities)
    page14Check = (Count-Entities $ans $Page14Entities)
    verdict = (Score-Target $ans)
}

$smoke = @()
$factMsg = [System.Text.Encoding]::UTF8.GetString(@(
    0x4D,0xC3,0xA3,0x20,0x73,0x69,0x6E,0x68,0x20,0x76,0x69,0xC3,0xAA,0x6E,0x20,0x63,0xE1,0xBB,0xA7,0x61,
    0x20,0x4C,0xC3,0x8A,0x20,0xC4,0x90,0xE1,0xBB,0xA8,0x43,0x20,0x44,0x55,0x59,0x20,0x6C,0xC3,0xA0,0x20,
    0x67,0xC3,0xAC,0x3F
))
$fact = Invoke-Chat $apiKey $factMsg
$smoke += @{ id = "fact"; pass = ($fact.answer -match "22T1020585"); sourceCount = $fact.sources.Count }

$tableMsg = [System.Text.Encoding]::UTF8.GetString(@(
    0x42,0xE1,0xBA,0xA3,0x6E,0x67,0x20,0x4E,0x67,0x75,0x6F,0x69,0x44,0x75,0x6E,0x67,0x20,0x63,0xC3,0xB3,
    0x20,0x6E,0x68,0xE1,0xBB,0xAF,0x6E,0x67,0x20,0x63,0xE1,0xBB,0x99,0x74,0x20,0x6E,0xC3,0xA0,0x6F,0x3F
))
$table = Invoke-Chat $apiKey $tableMsg
$cols = @("MaND", "TenDangNhap", "MatKhau", "Email", "SoDienThoai", "MaSV", "MaGV", "MaPKT", "MaKhoa")
$colHit = ($cols | Where-Object { $table.answer -match [regex]::Escape($_) }).Count
$smoke += @{ id = "table"; colHits = $colHit; pass = ($colHit -ge 6); sourceCount = $table.sources.Count }

$oosMsg = [System.Text.Encoding]::UTF8.GetString(@(
    0x54,0xE1,0xBB,0xB7,0x20,0x67,0x69,0xC3,0xA1,0x20,0x55,0x53,0x44,0x2F,0x56,0x4E,0x44,0x20,0x68,
    0xC3,0xB4,0x6D,0x20,0x6E,0x61,0x79,0x20,0x6C,0xC3,0xA0,0x20,0x62,0x61,0x6F,0x20,0x6E,0x68,0x69,
    0xC3,0xAA,0x75,0x3F
))
$oos = Invoke-Chat $apiKey $oosMsg
$oosPass = ($oos.answer -match "khong" -or $oos.answer -match "kh\u00f4ng") -and ($oos.answer -notmatch '\d{4,}')
$smoke += @{ id = "oos"; pass = $oosPass; sourceCount = $oos.sources.Count }
$results.regressionSmoke = $smoke

$results | ConvertTo-Json -Depth 10 | Set-Content $OutJson -Encoding UTF8
Write-Host "Done. Results: $OutJson"
Write-Host "Merge 14->13: $($results.parserMerge1314Seen)"
Write-Host "Target verdict: $($results.targetQuestion.verdict)"
