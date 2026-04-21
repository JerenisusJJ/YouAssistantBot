[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$body = @{
    model = "just-ai/openai-proxy/gpt-4o"
    messages = @(
        @{
            role = "system"
            content = "Ty - Kotlin arhitektor. Day optimalniy build.gradle.kts dlya Ktor proekta."
        },
        @{
            role = "user"
            content = "Ktor project s testami:\n- Ktor server\n- Kotest dlya testov\n- MockK\n- JaCoCo coverage\n- PostgreSQL client\n- TelegramBots library (dlya future)\n\nDay gotoviy build.gradle.kts s optimalnimi versiyami. Kratko."
        }
    )
} | ConvertTo-Json -Depth 5 -Compress

$result = Invoke-RestMethod -Uri "https://caila.io/api/adapters/openai/chat/completions" -Method POST -ContentType "application/json; charset=utf-8" -Headers @{
    Authorization = "Bearer 1000214050.231583.IjQafPZvJsAsD9Z3N35G5SSpQKFzmYmdLlpoC79U"
} -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$result.choices.message.content