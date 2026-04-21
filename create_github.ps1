$token = "glpat-w7WNtEOTsybhAoSilcdfN2M6MQpvOjEKdTptZDRxdA8.01.171oe471w"
$body = @{
    name = "ai-assistant"
    visibility = "private"
    description = "Personal AI Assistant with skill system"
} | ConvertTo-Json

$result = Invoke-RestMethod -Uri "https://gitlab.com/api/v4/projects" -Method POST -ContentType "application/json" -Headers @{
    "PRIVATE-TOKEN" = $token
} -Body $body

$result | ConvertTo-Json