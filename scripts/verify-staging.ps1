# Keep this script UTF-8 with BOM for Windows PowerShell 5.1 compatibility.
# Run only with the Railway STAGING tunnel active in ANOTHER terminal.
# This script issues SELECT requests only and never uploads the DB dump.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateRange(1024, 65535)]
    [int]$StagingPort,
    [string]$Database = "railway",
    [string]$DbUser = "postgres",
    [string]$PgBin = "C:\Program Files\PostgreSQL\18\bin",
    [switch]$SqlOnly
)

$ErrorActionPreference = "Stop"
$confirmation = Read-Host "현재 연결된 Railway 터널이 staging 맞아? 맞으면 STAGING 입력"
if ($confirmation -cne "STAGING") {
    throw "staging 환경 확인이 되지 않아 중단했어."
}
if ($Database -notmatch '^[a-zA-Z0-9_-]+$' -or $DbUser -notmatch '^[a-zA-Z0-9_-]+$') {
    throw "DB 이름이나 사용자명 형식을 확인해 줘."
}

$psql = Join-Path $PgBin "psql.exe"
if (-not (Test-Path -LiteralPath $psql)) { throw "psql.exe를 찾을 수 없어: $psql" }

$secure = Read-Host "staging DB 비밀번호 (입력 내용 숨김)" -AsSecureString
$creds = New-Object System.Net.NetworkCredential("", $secure)
$password = $creds.Password

try {
    $env:PGPASSWORD = $password
    $env:DB_JDBC_URL = "jdbc:postgresql://127.0.0.1:$StagingPort/$Database"
    $env:DB_USER = $DbUser
    $env:DB_PASSWORD = $password

    Write-Host "1/2: staging의 4개 테이블 행 수 조회 중 (READ ONLY)"
    $sql = "BEGIN READ ONLY; SELECT count(*) AS admission_results FROM admission_results; SELECT count(*) AS admission_metrics FROM admission_metrics; SELECT count(*) AS admission_aggregates FROM admission_aggregates; SELECT count(*) AS universities FROM universities; ROLLBACK;"
    & $psql -X -h 127.0.0.1 -p $StagingPort -U $DbUser -d $Database -v ON_ERROR_STOP=1 -c $sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL read-only 점검 실패: $LASTEXITCODE" }

    if ($SqlOnly) {
        Write-Host "SQL 점검 성공. -SqlOnly이므로 Java 검증은 수행하지 않았어."
        return
    }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Host "Maven을 찾지 못했어. SQL 조회만 성공했고 Spring Boot 실데이터 검증은 아직이야."
        Write-Host "JDK 21과 Maven 3.9+를 설치한 뒤 동일한 명령으로 다시 실행하면 돼."
        return
    }

    $env:KUNIRANK_STAGING_SMOKE = "YES"
    Push-Location (Join-Path $PSScriptRoot "..\backend")
    try {
        Write-Host "2/2: Spring Boot Hibernate validate + staging 조회 API 검증"
        & mvn -B "-Dtest=StagingReadOnlySmokeTest" "-DfailIfNoTests=true" test
        if ($LASTEXITCODE -ne 0) { throw "Spring Boot staging 실데이터 테스트 실패: $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
    Write-Host "PASS: staging 스키마 및 Spring 입결 GET API 읽기 검증"
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_JDBC_URL -ErrorAction SilentlyContinue
    Remove-Item Env:DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:KUNIRANK_STAGING_SMOKE -ErrorAction SilentlyContinue
    $password = $null
    $creds = $null
    $secure = $null
}
