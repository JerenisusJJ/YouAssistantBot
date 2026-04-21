$body = @{
    name = "ai-assistant"
    visibility = "private"
    description = "Personal AI Assistant with skill system"
} | ConvertTo-Json

$result = Invoke-RestMethod -Uri "https://gitlab.just-ai.com/api/v4/projects" -Method POST -ContentType "application/json" -Headers @{
    "PRIVATE-TOKEN" = "glpat-FBPcoxVNWecco3DSvJxM5286MQp1Ojh6CA.01.0y0dx5h69"
} -Body $body

$result | ConvertTo-Json
