param(
    [Parameter(Mandatory=$true)]
    [string]$Message,

    [string]$ChatId = "351153237"
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$BotToken = "493225373:AAFXJhOC-W3lca3BaG2FR61EevyxEXkzOfs"

$url = "https://api.telegram.org/bot$BotToken/sendMessage"
$body = @{
    chat_id = $ChatId
    text = $Message
} | ConvertTo-Json -Depth 3

try {
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    Invoke-RestMethod -Uri $url -Method POST -ContentType "application/json; charset=utf-8" -Body $bodyBytes
    Write-Host "Message sent successfully"
} catch {
    Write-Host "Error: $_"
}
