# OpenAI 兼容 Mock LLM（用于 Copilot 黑盒 RequireLlm，无需安装 Ollama）
param(
    [int]$Port = 18765
)

$ErrorActionPreference = "Stop"
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "Mock LLM listening http://127.0.0.1:$Port/v1/chat/completions"

function Get-MockContent([string]$body) {
    if ($body -notmatch 'json_object') {
        return "E2E mock: this chain validates user via validateUser component."
    }
    if ($body -match 'expression|Aviator|表达式|chainCtx') {
        return (@{
            expression  = 'chainCtx.get("userId")'
            explanation = 'E2E mock expression'
        } | ConvertTo-Json -Compress)
    }
    $chainObj = @{
        code    = 'CHN_TEST'
        version = 1
        nodes   = @(@{
            id = 'n1'; label = 'validate'; type = 'TASK'; component = 'validateUser'
        })
        edges   = @()
    }
    $chainStr = ($chainObj | ConvertTo-Json -Compress -Depth 8)
    return (@{ chainData = $chainStr; summary = 'E2E mock chain proposal' } | ConvertTo-Json -Compress)
}

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $reader = New-Object System.IO.StreamReader($ctx.Request.InputStream)
        $body = $reader.ReadToEnd()
        $content = Get-MockContent $body
        $payload = @{
            id      = "mock-e2e"
            object  = "chat.completion"
            choices = @(
                @{
                    index         = 0
                    message       = @{ role = "assistant"; content = $content }
                    finish_reason = "stop"
                }
            )
        } | ConvertTo-Json -Depth 6 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
        $ctx.Response.StatusCode = 200
        $ctx.Response.ContentType = "application/json"
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $ctx.Response.Close()
    }
} finally {
    $listener.Stop()
}
