$body = @{
    model = "just-ai/openai-proxy/gpt-4o"
    messages = @(
        @{
            role = "user"
            content = "Привет, ответь коротко"
        }
    )
} | ConvertTo-Json -Depth 3

$result = Invoke-RestMethod -Uri "https://caila.io/api/adapters/openai/chat/completions" -Method POST -ContentType "application/json" -Headers @{
    Authorization = "Bearer 1000214050.231583.IjQafPZvJsAsD9Z3N35G5SSpQKFzmYmdLlpoC79U"
} -Body $body

$result.choices.message.content
