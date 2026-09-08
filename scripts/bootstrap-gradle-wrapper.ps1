$ErrorActionPreference = "Stop"

$Version = "9.5.0"
$DistributionSha256 = "553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
$WrapperJarSha256 = "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
$Archive = "gradle-$Version-bin.zip"
$Url = "https://services.gradle.org/distributions/$Archive"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$CacheDir = Join-Path $env:TEMP "mola-gradle-bootstrap"
$ArchivePath = Join-Path $CacheDir $Archive
$ExtractedDir = Join-Path $CacheDir "gradle-$Version"
$BootstrapProject = Join-Path $CacheDir "wrapper-project"

New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null

if (-not (Test-Path $ArchivePath)) {
    Invoke-WebRequest -Uri $Url -OutFile $ArchivePath
}

$ActualDistributionSha256 = (Get-FileHash -Path $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualDistributionSha256 -ne $DistributionSha256) {
    Remove-Item -Force $ArchivePath
    throw "Gradle distribution checksum mismatch. The downloaded archive was removed."
}

if (-not (Test-Path (Join-Path $ExtractedDir "bin\gradle.bat"))) {
    Expand-Archive -Path $ArchivePath -DestinationPath $CacheDir -Force
}

if (Test-Path $BootstrapProject) {
    Remove-Item -Recurse -Force $BootstrapProject
}
New-Item -ItemType Directory -Force -Path $BootstrapProject | Out-Null
Set-Content -Path (Join-Path $BootstrapProject "settings.gradle") -Value 'rootProject.name = "wrapper-bootstrap"'

Push-Location $BootstrapProject
try {
    & (Join-Path $ExtractedDir "bin\gradle.bat") --no-daemon wrapper --gradle-version $Version --distribution-type bin
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle wrapper generation failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$RepoWrapperDir = Join-Path $RepoRoot "gradle\wrapper"
New-Item -ItemType Directory -Force -Path $RepoWrapperDir | Out-Null
Copy-Item -Force (Join-Path $BootstrapProject "gradlew") (Join-Path $RepoRoot "gradlew")
Copy-Item -Force (Join-Path $BootstrapProject "gradlew.bat") (Join-Path $RepoRoot "gradlew.bat")
Copy-Item -Force (Join-Path $BootstrapProject "gradle\wrapper\gradle-wrapper.jar") (Join-Path $RepoWrapperDir "gradle-wrapper.jar")
Copy-Item -Force (Join-Path $BootstrapProject "gradle\wrapper\gradle-wrapper.properties") (Join-Path $RepoWrapperDir "gradle-wrapper.properties")

$WrapperJarPath = Join-Path $RepoWrapperDir "gradle-wrapper.jar"
$ActualWrapperJarSha256 = (Get-FileHash -Path $WrapperJarPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualWrapperJarSha256 -ne $WrapperJarSha256) {
    $GeneratedFiles = @(
        (Join-Path $RepoRoot "gradlew"),
        (Join-Path $RepoRoot "gradlew.bat"),
        $WrapperJarPath,
        (Join-Path $RepoWrapperDir "gradle-wrapper.properties")
    )
    Remove-Item -Force -ErrorAction SilentlyContinue -Path $GeneratedFiles
    throw "Gradle Wrapper JAR checksum mismatch. Generated wrapper files were removed."
}

$Properties = Join-Path $RepoWrapperDir "gradle-wrapper.properties"
$Content = Get-Content -Raw $Properties
if ($Content -match "(?m)^distributionSha256Sum=") {
    $Content = [regex]::Replace(
        $Content,
        "(?m)^distributionSha256Sum=.*$",
        "distributionSha256Sum=$DistributionSha256"
    )
    Set-Content -Path $Properties -Value $Content -NoNewline
}
else {
    Add-Content -Path $Properties -Value "`ndistributionSha256Sum=$DistributionSha256"
}

Write-Host "Standard Gradle $Version wrapper generated; distribution and wrapper JAR checksums verified."
