param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$WorkDirectory = (Join-Path $PSScriptRoot '..\.local\checks')
)
$ErrorActionPreference='Stop'
if (-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\javac.exe'))) {
    throw 'Set JAVA_HOME or supply -Jdk with a JDK 17+ path.'
}
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$work=[IO.Path]::GetFullPath($WorkDirectory)
$classes=Join-Path $work 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javaSource=Join-Path $project 'app\src\main\java\dev\local\tvhomeselector'
& (Join-Path $Jdk 'bin\javac.exe') -d $classes (Join-Path $javaSource 'HomePolicy.java') (Join-Path $project 'checks\HomePolicyCheck.java')
if($LASTEXITCODE -ne 0){throw 'Status-policy check compilation failed.'}
& (Join-Path $Jdk 'bin\java.exe') -cp $classes HomePolicyCheck
if($LASTEXITCODE -ne 0){throw 'Status-policy checks failed.'}
$sources=@('AdbWire.java','AdbIdentity.java','SwitchEngine.java','LocalAdb.java') | ForEach-Object {Join-Path $javaSource $_}
$sources+=Join-Path $project 'checks\LocalAdbCheck.java'
& (Join-Path $Jdk 'bin\javac.exe') -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'ADB check compilation failed.'}
& (Join-Path $Jdk 'bin\java.exe') -cp $classes dev.local.tvhomeselector.LocalAdbCheck $work
if($LASTEXITCODE -ne 0){throw 'ADB checks failed.'}
Write-Host 'All 45 standalone checks passed. Test keys stay in the ignored/local work directory.'
