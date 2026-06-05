<#
.SYNOPSIS
    Quick rebuild and restart of the Punishments service container.

.DESCRIPTION
    Fast mode builds the shadowJar locally, then builds a small Docker image
    from Dockerfile.fast. Full mode builds the JAR inside Docker.

.PARAMETER Full
    Force a full Docker build using Dockerfile.

.EXAMPLE
    .\deploy.ps1
    .\deploy.ps1 -Full
#>
param(
    [switch]$Full
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host ""
Write-Host "=============================="
Write-Host "  Punishments Deploy Script"
Write-Host "=============================="
Write-Host ""

function Resolve-JavaHome {
    $jdksRoot = Join-Path $env:USERPROFILE ".gradle\jdks"

    if (Test-Path $jdksRoot) {
        $javaExes = Get-ChildItem $jdksRoot -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue |
            Where-Object { $_.Directory.Name -eq "bin" }

        if ($javaExes) {
            $jdk21 = $javaExes | Where-Object { $_.FullName -match "-21-" } | Select-Object -First 1
            $pick = if ($jdk21) { $jdk21 } else { $javaExes | Select-Object -First 1 }
            return $pick.Directory.Parent.FullName
        }
    }

    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaExe) {
            return $env:JAVA_HOME
        }
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        return (Split-Path (Split-Path $javaCmd.Source))
    }

    return $null
}

Write-Host "[1/4] Starting infrastructure (Postgres + Redis)..."
docker compose up -d postgres redis

Write-Host "      Waiting for Postgres health..."
$retries = 0
do {
    Start-Sleep -Seconds 2
    $retries++
    $health = docker inspect --format="{{.State.Health.Status}}" punishments-postgres 2>$null
} while ($health -ne "healthy" -and $retries -lt 30)

if ($health -ne "healthy") {
    Write-Host "ERROR: Postgres failed to become healthy" -ForegroundColor Red
    exit 1
}

$resolvedJava = Resolve-JavaHome
if ($resolvedJava) {
    $env:JAVA_HOME = $resolvedJava
    Write-Host "      JAVA_HOME: $env:JAVA_HOME"
} else {
    Write-Host "WARNING: Could not find a valid JDK. Gradle may fail." -ForegroundColor Yellow
}

if ($Full) {
    Write-Host "[2/4] Full Docker build..."
    docker compose up -d --build --force-recreate punishments-service
    Write-Host "[3/4] Skipped in full mode."
    Write-Host "[4/4] Done."
} else {
    Write-Host "[2/4] Building shadowJar locally..."
    & cmd /c "gradlew.bat :service:shadowJar --no-daemon 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Gradle build failed" -ForegroundColor Red
        exit 1
    }

    $jarPath = "service\build\libs\service-1.0.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "ERROR: JAR not found at $jarPath" -ForegroundColor Red
        exit 1
    }

    $jarSize = [math]::Round((Get-Item $jarPath).Length / 1MB, 1)
    Write-Host "      JAR ready: $jarPath ($jarSize MB)"

    Write-Host "[3/4] Building Docker image (fast mode)..."
    docker compose -f docker-compose.yml -f docker-compose.fast.yml build punishments-service
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker build failed" -ForegroundColor Red
        exit 1
    }

    Write-Host "[4/4] Restarting Punishments service..."
    docker compose -f docker-compose.yml -f docker-compose.fast.yml up -d --force-recreate punishments-service
}

Write-Host ""
Write-Host "Punishments service is deployed."
Write-Host "  HTTP health: http://localhost:8080/health"
Write-Host "  gRPC:        localhost:9090"
Write-Host "  Logs:        docker compose logs -f punishments-service"
Write-Host ""
