# Builds FileManager.apk without Gradle, using the kit in %LOCALAPPDATA%\VODOS-build
# Usage:  powershell -ExecutionPolicy Bypass -File build.ps1
$ErrorActionPreference = 'Continue' # native tools write warnings to stderr; we check exit codes

# Short 8.3 path: the Windows user name is in Hebrew and aapt2 can't open non-ASCII paths
$K    = (New-Object -ComObject Scripting.FileSystemObject).GetFolder((Join-Path $env:LOCALAPPDATA 'VODOS-build')).ShortPath
$JDK  = "$K\jdk-17.0.21+9\bin"
$AJAR = "$K\sdk\pf\android-34\android.jar"
$BT   = "$K\sdk\bt\android-14"
$OUT  = "$K\filemanager-out"
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

Write-Host '== aapt2'
Run "$BT\aapt2.exe" @('compile', '--dir', "$SRC\res", '-o', "$OUT\res.zip")
Run "$BT\aapt2.exe" @('link', '-I', $AJAR, '--manifest', "$SRC\AndroidManifest.xml",
    '--min-sdk-version', '26', '--target-sdk-version', '33',
    '--java', "$OUT\gen", '-o', "$OUT\base.apk", "$OUT\res.zip")

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
Copy-Item -Force "$OUT\FileManager.apk" (Join-Path $PSScriptRoot 'FileManager.apk')
Write-Host "== DONE"
