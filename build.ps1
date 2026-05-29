# Build script for lunalib-search-fullmod workspace.
# Uses Gradle to compile and package, then hot-deploys to the game mods folder.

$MOD   = "C:\my\lunalib-search-fullmod\LunaLib-Search-2.0.5"
$GRADLE = "C:\my\gradle-9.5.1\bin\gradle.bat"
$DEPLOY_MOD = "C:\my\Starsector\mods\LunaLib-Search-2.0.5"

Write-Host "==> Building with Gradle..." -ForegroundColor Cyan
Push-Location $MOD
& $GRADLE jar --no-daemon
$exitCode = $LASTEXITCODE
Pop-Location

if ($exitCode -ne 0) {
    Write-Host "Build FAILED (exit code $exitCode)" -ForegroundColor Red
    exit $exitCode
}

$JAR = "$MOD\jars\LunaLib.jar"
if (!(Test-Path $JAR)) {
    Write-Host "Jar not found after build: $JAR" -ForegroundColor Red
    exit 1
}

Write-Host "==> Deploying to $DEPLOY_MOD..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force "$DEPLOY_MOD\jars\libs" | Out-Null
Copy-Item "$MOD\jars\LunaLib.jar" "$DEPLOY_MOD\jars\LunaLib.jar" -Force
Copy-Item "$MOD\jars\libs\fuzzywuzzy-1.3.0.jar" "$DEPLOY_MOD\jars\libs\fuzzywuzzy-1.3.0.jar" -Force
Copy-Item "$MOD\mod_info.json" "$DEPLOY_MOD\mod_info.json" -Force

if (Test-Path "$MOD\data") {
    if (!(Test-Path "$DEPLOY_MOD\data")) { New-Item -ItemType Directory -Force "$DEPLOY_MOD\data" | Out-Null }
    Copy-Item "$MOD\data\*" "$DEPLOY_MOD\data\" -Recurse -Force
}
if (Test-Path "$MOD\graphics") {
    if (!(Test-Path "$DEPLOY_MOD\graphics")) { New-Item -ItemType Directory -Force "$DEPLOY_MOD\graphics" | Out-Null }
    Copy-Item "$MOD\graphics\*" "$DEPLOY_MOD\graphics\" -Recurse -Force
}

$size = [math]::Round((Get-Item "$DEPLOY_MOD\jars\LunaLib.jar").Length / 1KB, 1)
Write-Host "==> Done! LunaLib.jar deployed: $size KB" -ForegroundColor Green
