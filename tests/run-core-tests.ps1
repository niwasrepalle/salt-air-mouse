param([string]$JdkPath = '')
$ErrorActionPreference='Stop'
if (!$JdkPath) { $JdkPath=$env:JAVA_HOME }
if (!$JdkPath) { $JdkPath=(Get-ChildItem (Join-Path $env:TEMP 'SaltAirMouse-build\jdk') -Directory | Select-Object -First 1).FullName }
if (!(Test-Path "$JdkPath\bin\javac.exe")) { throw 'Build the APK first, or specify -JdkPath with your JDK folder.' }
$sourceDir=Join-Path $PSScriptRoot '..\android\app\src\main\java\dev\salt\airmouse'
$testDir=Join-Path $env:TEMP 'SaltAirMouse-core-tests'
New-Item -ItemType Directory -Force $testDir | Out-Null
& "$JdkPath\bin\javac.exe" -encoding UTF-8 -d $testDir "$sourceDir\TrackpadEngine.java" "$sourceDir\AirMotion.java" "$PSScriptRoot\CoreTests.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$JdkPath\bin\java.exe" -cp $testDir CoreTests
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
