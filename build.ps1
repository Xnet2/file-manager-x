# Builds FileManager.apk without Gradle, using the kit in %LOCALAPPDATA%\VODOS-build
# Usage:  powershell -ExecutionPolicy Bypass -File build.ps1 [-Variant full|lite]
#   lite = "ללא וידאו והתקנות": no video playback, APKs cannot be installed, separate package (installs side by side)
param([ValidateSet('full', 'lite')][string]$Variant = 'full')
$ErrorActionPreference = 'Continue' # native tools write warnings to stderr; we check exit codes

# Short 8.3 path: the Windows user name is in Hebrew and aapt2 can't open non-ASCII paths
$K    = (New-Object -ComObject Scripting.FileSystemObject).GetFolder((Join-Path $env:LOCALAPPDATA 'VODOS-build')).ShortPath
$JDK  = "$K\jdk-17.0.21+9\bin"
$AJAR = "$K\sdk\pf\android-34\android.jar"
$BT   = "$K\sdk\bt\android-14"
$OUT  = "$K\filemanager-out-$Variant"
$KS   = "$K\filemanager.keystore"
$PASS = 'filemanager123'

function Run($exe, [string[]]$a) {
    & $exe @a
    if ($LASTEXITCODE -ne 0) { throw "FAILED: $exe" }
}

if (Test-Path $OUT) { Remove-Item -Recurse -Force $OUT }
New-Item -ItemType Directory -Force "$OUT\gen", "$OUT\classes", "$OUT\dex" | Out-Null

# Stage sources next to the tools (external tools can't open the project folder path)
Copy-Item -Recurse (Join-Path $PSScriptRoot 'app\src\main') "$OUT\src"
$SRC = "$OUT\src"
$PKGARGS = @()
$APKNAME = 'FileManager.apk'
if ($Variant -eq 'lite') {
    $utf8 = New-Object Text.UTF8Encoding $false
    $flavor = "$SRC\java\com\custom\files\Flavor.java"
    $f = [IO.File]::ReadAllText($flavor)
    $f = $f.Replace('NO_VIDEO = false', 'NO_VIDEO = true').Replace('NO_INSTALL = false', 'NO_INSTALL = true')
    $f = $f.Replace('"com.custom.files.provider"', '"com.custom.files.lite.provider"').Replace('NAME = "מנהל הקבצים X"', 'NAME = "מנהל הקבצים X ללא וידאו"')
    [IO.File]::WriteAllText($flavor, $f, $utf8)
    $man = [IO.File]::ReadAllText("$SRC\AndroidManifest.xml")
    $man = $man.Replace('com.custom.files.provider', 'com.custom.files.lite.provider')
    $man = ($man -split "`n" | Where-Object { $_ -notmatch 'REQUEST_INSTALL_PACKAGES' }) -join "`n"
    [IO.File]::WriteAllText("$SRC\AndroidManifest.xml", $man, $utf8)
    $val = [IO.File]::ReadAllText("$SRC\res\values\values.xml")
    $val = $val.Replace('>מנהל הקבצים X<', '>מנהל הקבצים X ללא וידאו<')
    [IO.File]::WriteAllText("$SRC\res\values\values.xml", $val, $utf8)
    $PKGARGS = @('--rename-manifest-package', 'com.custom.files.lite')
    $APKNAME = 'FileManager-Lite.apk'
}

Write-Host '== aapt2'
Run "$BT\aapt2.exe" @('compile', '--dir', "$SRC\res", '-o', "$OUT\res.zip")
Run "$BT\aapt2.exe" (@('link', '-I', $AJAR, '--manifest', "$SRC\AndroidManifest.xml",
    '--min-sdk-version', '26', '--target-sdk-version', '33',
    '--java', "$OUT\gen") + $PKGARGS + @('-o', "$OUT\base.apk", "$OUT\res.zip"))

Write-Host '== javac'
$java = @(Get-ChildItem "$SRC\java", "$OUT\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Run "$JDK\javac.exe" (@('-encoding', 'UTF-8', '-Xlint:-options', '-source', '17', '-target', '17',
    '-cp', $AJAR, '-d', "$OUT\classes") + $java)

Write-Host '== d8'
$classes = @(Get-ChildItem "$OUT\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
Run "$JDK\java.exe" (@('-cp', "$BT\lib\d8.jar", 'com.android.tools.r8.D8', '--release', '--min-api', '26',
    '--lib', $AJAR, '--output', "$OUT\dex") + $classes)

Write-Host '== package'
Push-Location "$OUT\dex"
Run "$JDK\jar.exe" @('-uf', "$OUT\base.apk", 'classes.dex')
Pop-Location
Run "$BT\zipalign.exe" @('-f', '-p', '4', "$OUT\base.apk", "$OUT\aligned.apk")

if (-not (Test-Path $KS)) {
    Run "$JDK\keytool.exe" @('-genkeypair', '-keystore', $KS, '-alias', 'filemanager', '-keyalg', 'RSA',
        '-keysize', '2048', '-validity', '10000', '-storepass', $PASS, '-keypass', $PASS,
        '-dname', 'CN=FileManager')
}
Run "$JDK\java.exe" @('-jar', "$BT\lib\apksigner.jar", 'sign', '--ks', $KS, '--ks-pass', "pass:$PASS",
    '--out', "$OUT\FileManager.apk", "$OUT\aligned.apk")
Copy-Item -Force "$OUT\FileManager.apk" (Join-Path $PSScriptRoot $APKNAME)
Write-Host "== DONE"
