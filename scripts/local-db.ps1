param([ValidateSet('start','stop')][string]$Action = 'start', [string]$PgBin = 'C:\Program Files\PostgreSQL\17\bin')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectRoot '.local-db'
$dataPath = Join-Path $localRoot 'data'
$configPath = Join-Path $localRoot 'connection.json'
if (!(Test-Path (Join-Path $PgBin 'initdb.exe'))) { throw "Укажите -PgBin с каталогом bin PostgreSQL." }
if ($Action -eq 'stop') {
    if (Test-Path $dataPath) { & (Join-Path $PgBin 'pg_ctl.exe') -D $dataPath -m fast -w stop }
    exit $LASTEXITCODE
}
New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
if (!(Test-Path $configPath)) {
    if (Test-Path $dataPath) { throw 'Каталог data уже существует без конфигурации; проверьте его вручную.' }
    $randomBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    $config = @{ user = 'gamestore_owner'; password = [Convert]::ToBase64String($randomBytes); port = 55432 }
    $passwordPath = Join-Path $localRoot 'init-password.tmp'
    try {
        [IO.File]::WriteAllText($passwordPath, $config.password, [Text.Encoding]::ASCII)
        & (Join-Path $PgBin 'initdb.exe') -D $dataPath -U $config.user --auth=scram-sha-256 --encoding=UTF8 --locale=C --pwfile=$passwordPath
        if ($LASTEXITCODE -ne 0) { throw 'initdb завершился с ошибкой.' }
        $config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding UTF8
    } finally { if (Test-Path $passwordPath) { Remove-Item -LiteralPath $passwordPath } }
}
$config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
& (Join-Path $PgBin 'pg_ctl.exe') -D $dataPath status | Out-Null
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $PgBin 'pg_ctl.exe') -D $dataPath -l (Join-Path $localRoot 'postgres.log') -o "-p $($config.port) -h 127.0.0.1" -w start
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось запустить отдельный PostgreSQL; проверьте порт 55432 и .local-db/postgres.log.' }
}
$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $config.password
    foreach ($databaseName in @('gamestore','gamestore_test')) {
        $exists = & (Join-Path $PgBin 'psql.exe') -h 127.0.0.1 -p $config.port -U $config.user -d postgres -w -At -c "SELECT 1 FROM pg_database WHERE datname='$databaseName'"
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось подключиться к отдельному PostgreSQL.' }
        if ($exists -ne '1') {
            & (Join-Path $PgBin 'createdb.exe') -h 127.0.0.1 -p $config.port -U $config.user -w $databaseName
            if ($LASTEXITCODE -ne 0) { throw "Не удалось создать $databaseName." }
        }
    }
} finally { $env:PGPASSWORD = $previousPassword }
Write-Host 'Локальная БД проекта работает на 127.0.0.1:55432. Загрузите переменные: . .\scripts\use-local-db.ps1'
