$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$goldenPath = "e:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\RAG_GOLDEN_TEST_DOCUMENT.txt"
$outFile = "e:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\results\21j_raw_eval_output.txt"

$cases = @(
    @{ id = "GQ-F01"; q = "Tên pháp lý đầy đủ của công ty trong tài liệu là gì?" },
    @{ id = "GQ-F02"; q = "Mã xác nhận baseline (golden) trong tài liệu là gì?" },
    @{ id = "GQ-F03"; q = "Gói Pro có bao nhiêu lượt hỏi AI mỗi tháng theo bảng giá?" },
    @{ id = "GQ-L01"; q = "Liệt kê đầy đủ ba chính sách hỗ trợ được nêu trong mục chính sách." },
    @{ id = "GQ-L02"; q = "Trong bảng gói dịch vụ, có những tên gói nào?" },
    @{ id = "GQ-T01"; q = "Giá gói Basic là bao nhiêu VNĐ theo bảng?" },
    @{ id = "GQ-T02"; q = "Gói Business được hỗ trợ theo kênh nào trong bảng?" },
    @{ id = "GQ-C01"; q = "Bảng gói dịch vụ có tất cả bao nhiêu hàng gói (số gói)?" },
    @{ id = "GQ-O01"; q = "Tỷ giá USD/VND hôm nay là bao nhiêu?" },
    @{ id = "GQ-O02"; q = "CEO của AlphaDemo tên đầy đủ là gì?" }
)

$created = Invoke-RestMethod -Uri "$base/api/chatbots" -Method Post -ContentType "application/json" -Body (@{ name = "21J Source Cap Eval"; description = "task 21J"; domain = "eval.local" } | ConvertTo-Json)
$chatbotId = $created.id
$apiKey = $created.apiKey
"META_JSON=" + (@{ chatbotId = $chatbotId; apiKeyMasked = ($apiKey.Substring(0,8) + "..." + $apiKey.Substring($apiKey.Length-4)) } | ConvertTo-Json -Compress) | Out-File $outFile -Encoding utf8

$boundary = [System.Guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($goldenPath)
$bodyLines = @(
    "--$boundary",
    'Content-Disposition: form-data; name="chatbotId"',
    "",
    $chatbotId,
    "--$boundary",
    'Content-Disposition: form-data; name="files"; filename="RAG_GOLDEN_TEST_DOCUMENT.txt"',
    "Content-Type: text/plain",
    "",
    [System.Text.Encoding]::UTF8.GetString($fileBytes),
    "--$boundary--"
) -join "`r`n"
$upload = Invoke-RestMethod -Uri "$base/api/documents/upload" -Method Post -ContentType "multipart/form-data; boundary=$boundary" -Body $bodyLines
$docId = $upload[0].id
"UPLOAD_JSON=" + ($upload[0] | ConvertTo-Json -Compress) | Add-Content $outFile -Encoding utf8

for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    $st = Invoke-RestMethod -Uri "$base/api/documents/$docId/status" -Method Get
    if ($st.status -eq "INDEXED") { break }
}
"STATUS_JSON=" + ($st | ConvertTo-Json -Compress) | Add-Content $outFile -Encoding utf8

$headers = @{ "X-Widget-Key" = $apiKey }
foreach ($c in $cases) {
    $sessionId = [guid]::NewGuid().ToString()
    $json = (@{ sessionId = $sessionId; message = $c.q } | ConvertTo-Json -Compress)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $resp = Invoke-RestMethod -Uri "$base/api/chat" -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes
    $row = @{
        caseId = $c.id
        question = $c.q
        sessionId = $sessionId
        answer = $resp.answer
        sourceCount = @($resp.sources).Count
        sources = $resp.sources
    }
    "CHAT_JSON=" + ($row | ConvertTo-Json -Compress -Depth 6) | Add-Content $outFile -Encoding utf8
    Write-Host "$($c.id) sourceCount=$($row.sourceCount)"
}

Invoke-RestMethod -Uri "$base/api/documents/$docId" -Method Delete | Out-Null
$dSession = [guid]::NewGuid().ToString()
$dJson = (@{ sessionId = $dSession; message = "Mã xác nhận baseline (golden) trong tài liệu là gì?" } | ConvertTo-Json -Compress)
$dBytes = [System.Text.Encoding]::UTF8.GetBytes($dJson)
$dResp = Invoke-RestMethod -Uri "$base/api/chat" -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body $dBytes
"D01_JSON=" + (@{ caseId = "GQ-D01"; sourceCount = @($dResp.sources).Count; answer = $dResp.answer } | ConvertTo-Json -Compress) | Add-Content $outFile -Encoding utf8
Write-Host "GQ-D01 sourceCount=$(@($dResp.sources).Count)"
"DONE" | Add-Content $outFile -Encoding utf8
