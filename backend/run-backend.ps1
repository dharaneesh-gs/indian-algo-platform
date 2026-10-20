$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Java -version writes to stderr on some Windows installations. Handle that safely.
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Install JDK 21 and add java to PATH."
}
$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    $javaOutput = (& java -version 2>&1 | Out-String)
    $javaExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousPreference
}
if ($javaExitCode -ne 0 -or $javaOutput -notmatch 'version "?(21)\.') {
    throw "A working Java 21 installation is required. Output: $javaOutput"
}
Write-Host "Java 21 detected." -ForegroundColor Green

# Startup intentionally uses an already-packaged JAR; no Maven invocation here.
$targetDir = Join-Path $PSScriptRoot "target"
if (-not (Test-Path $targetDir -PathType Container)) {
    throw "Packaged JAR not found. Build once from backend with: mvn clean package"
}
$jar = Get-ChildItem -Path $targetDir -Filter "*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '(-sources|-javadoc|\.original|\.plain)\.jar$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    throw "No executable JAR found in target. Build once with: mvn clean package"
}

if ([string]::IsNullOrWhiteSpace($env:APP_BIND_ADDRESS)) { $env:APP_BIND_ADDRESS = "127.0.0.1" }

Write-Host "Starting packaged application: $($jar.Name)" -ForegroundColor Green
Write-Host "Live trading is disabled. Create the first login account from localhost. Backend bind address: $env:APP_BIND_ADDRESS" -ForegroundColor Yellow
& java -jar $jar.FullName
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) { throw "Spring Boot exited with code $exitCode." }
