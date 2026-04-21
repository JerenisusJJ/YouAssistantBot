$token = "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"
$body = @{
    name = "YouAssistantBot"
    description = "Personal AI Assistant with skill system"
    private = $true
} | ConvertTo-Json

$result = Invoke-RestMethod -Uri "https://api.github.com/user/repos" -Method POST -ContentType "application/json" -Headers @{
    "Authorization" = "token $token"
    "Accept" = "application/vnd.github.v3+json"
} -Body $body

$result | ConvertTo-Json