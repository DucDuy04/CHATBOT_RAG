$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

$created = Invoke-RestMethod -Uri "$base/api/chatbots" -Method Post -ContentType "application/json" -Body (@{ name = "22A TopK Smoke"; description = "topk"; domain = "eval.local" } | ConvertTo-Json)
$apiKey = $created.apiKey
$headers = @{ "X-Widget-Key" = $apiKey; "Content-Type" = "application/json; charset=utf-8" }

function Invoke-Chat($topK) {
    $body = @{ sessionId = [guid]::NewGuid().ToString(); message = "Ten cong ty la gi?" }
    if ($null -ne $topK) { $body.topK = $topK }
    $json = $body | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri "$base/api/chat" -Method Post -Headers $headers -Body $bytes
}

"TOPK3=" + (Invoke-Chat 3).answer.Substring(0, [Math]::Min(80, (Invoke-Chat 3).answer.Length)) | Out-File "e:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\results\22a_topk_smoke.txt" -Encoding utf8
Invoke-Chat 10 | Out-Null
Invoke-Chat 999 | Out-Null
Invoke-Chat $null | Out-Null
"DONE" | Add-Content "e:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\results\22a_topk_smoke.txt" -Encoding utf8
