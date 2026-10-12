param([string]$JdkPath = '', [string]$SdkPath = '', [switch]$SkipSdkSetup)
# Run: powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
# Optional existing tools: -JdkPath <JDK17> -SdkPath <AndroidSDK> -SkipSdkSetup
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$cacheRoot = Join-Path $env:TEMP 'SaltAirMouse-build'
New-Item -ItemType Directory -Force $cacheRoot | Out-Null
function Fetch-Zip($name, $url) {
    $target = Join-Path $cacheRoot $name
    if (!(Test-Path $target)) {
        $archive = Join-Path $cacheRoot ($name + '.zip')
        Write-Host "Downloading $name..."
        Invoke-WebRequest -Uri $url -OutFile $archive
        Expand-Archive -LiteralPath $archive -DestinationPath $target
    }
    return $target
}
function Check-Exit($step) { if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE). Read the error above." } }
if (!$JdkPath) {
    $jdkRoot = Fetch-Zip 'jdk' 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip'
    $JdkPath = (Get-ChildItem -LiteralPath $jdkRoot -Directory | Select-Object -First 1).FullName
}
if (!$SdkPath) { $SdkPath = Fetch-Zip 'sdk' 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' }
$env:JAVA_HOME = $JdkPath
$env:Path = "$JdkPath\bin;$env:Path"
if (!$SkipSdkSetup) {
    $sdkManager = Join-Path $SdkPath 'cmdline-tools\bin\sdkmanager.bat'
    Write-Host 'Read the Android SDK licenses; type y to accept each if you agree.'
    & $sdkManager "--sdk_root=$SdkPath" --licenses
    Check-Exit 'SDK license acceptance'
    & $sdkManager "--sdk_root=$SdkPath" 'platforms;android-35' 'build-tools;35.0.0'
    Check-Exit 'SDK installation'
}
$tools = Join-Path $SdkPath 'build-tools\35.0.0'
$androidJar = Join-Path $SdkPath 'platforms\android-35\android.jar'
$stage = Join-Path $cacheRoot 'package'
New-Item -ItemType Directory -Force "$stage\classes", "$stage\dex" | Out-Null
# Stage only this project's class output. No third-party dependencies.
$classTargets = Get-ChildItem -LiteralPath "$stage\classes" -File -Recurse
foreach ($item in $classTargets) { Remove-Item -LiteralPath $item.FullName }
$source = (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'android\app\src\main\java') -Filter *.java -Recurse).FullName
& "$JdkPath\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$androidJar;$tools\core-lambda-stubs.jar" -d "$stage\classes" $source
Check-Exit 'Java compilation'
$classes = (Get-ChildItem -LiteralPath "$stage\classes" -Filter *.class -Recurse).FullName
& "$tools\d8.bat" --min-api 23 --lib $androidJar --output "$stage\dex" $classes
Check-Exit 'DEX conversion'
$manifest = [IO.File]::ReadAllText((Join-Path $projectRoot 'android\app\src\main\AndroidManifest.xml'))
$manifest = $manifest.Replace('<manifest xmlns:', '<manifest package="dev.salt.airmouse" xmlns:')
[IO.File]::WriteAllText("$stage\AndroidManifest.xml",$manifest,(New-Object Text.UTF8Encoding($false)))
& "$tools\aapt2.exe" link -I $androidJar --manifest "$stage\AndroidManifest.xml" --min-sdk-version 23 --target-sdk-version 35 --version-code 2 --version-name 2.0 -o "$stage\base.apk"
Check-Exit 'Manifest packaging'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$stage\base.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$stage\dex\classes.dex",'classes.dex') | Out-Null } finally { $zip.Dispose() }
& "$tools\zipalign.exe" -f -p 4 "$stage\base.apk" "$stage\aligned.apk"
Check-Exit 'APK alignment'
$keyStore = Join-Path $cacheRoot 'debug.keystore'
if (!(Test-Path $keyStore)) {
    & "$JdkPath\bin\keytool.exe" -genkeypair -keystore $keyStore -storepass android -keypass android -alias androiddebugkey -dname 'CN=Salt Air Mouse Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    Check-Exit 'Debug key creation'
}
$apk = Join-Path $projectRoot 'SaltAirMouse-debug.apk'
& "$tools\apksigner.bat" sign --ks $keyStore --ks-pass pass:android --key-pass pass:android --out $apk "$stage\aligned.apk"
Check-Exit 'APK signing'
& "$tools\apksigner.bat" verify --verbose $apk
Check-Exit 'APK signature verification'
Write-Host "APK ready: $apk"
