$configPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.local-db\connection.json'
if (!(Test-Path $configPath)) { throw 'Сначала выполните .\scripts\local-db.ps1' }
$config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$env:GAMESTORE_DB_URL = "jdbc:postgresql://127.0.0.1:$($config.port)/gamestore"
$env:GAMESTORE_DB_USER = $config.user
$env:GAMESTORE_DB_PASSWORD = $config.password
$env:GAMESTORE_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$($config.port)/gamestore_test"
