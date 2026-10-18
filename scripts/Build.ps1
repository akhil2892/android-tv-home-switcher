param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$GradleUserHome,
    [string]$WorkDirectory = (Join-Path $PSScriptRoot '..\.local'),
    [switch]$AnalysisOnly,
    [switch]$Offline
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\java.exe'))){throw 'Set JAVA_HOME or supply -Jdk with a JDK 17+ path.'}
if(-not $Sdk){$Sdk=$env:ANDROID_SDK_ROOT}
if(-not $Sdk -or -not (Test-Path -LiteralPath (Join-Path $Sdk 'platforms\android-36\android.jar'))){throw 'Supply -Sdk with official SDK platform 36 installed.'}
if(-not(Test-Path -LiteralPath (Join-Path $Sdk 'build-tools\36.0.0\aapt2.exe'))){throw 'Install official build-tools 36.0.0.'}
$env:JAVA_HOME=$Jdk
$env:ANDROID_HOME=$Sdk
if($GradleUserHome){$env:GRADLE_USER_HOME=[IO.Path]::GetFullPath($GradleUserHome)}
('sdk.dir='+$Sdk.Replace('\','/').Replace(':','\:')) | Set-Content -LiteralPath (Join-Path $project 'local.properties') -Encoding ascii
Push-Location $project
try{
    $lintTask=if($AnalysisOnly){':app:lintAnalyzeDebug'}else{':app:lintDebug'}
    $arguments=@('--no-daemon','--console=plain',':app:assembleDebug',$lintTask)
    if($AnalysisOnly){$arguments+='--rerun-tasks'}
    if($Offline){$arguments+='--offline'}
    & '.\gradlew.bat' @arguments
    if($LASTEXITCODE -ne 0){throw 'Build/lint failed. Nothing was installed.'}
    if($AnalysisOnly){
        foreach($name in @('lint-definite.xml','lint-provisional.xml')){
            $path=Join-Path $project ('app\build\intermediates\lint_partial_results\debug\lintAnalyzeDebug\out\'+$name)
            [xml]$analysis=Get-Content -LiteralPath $path -Raw
            $errors=$analysis.SelectNodes('//incident[@severity="error" or @severity="fatal"]')
            if($errors.Count){$errors|ForEach-Object {Write-Host $_.OuterXml};throw 'Lint analysis contains error/fatal incidents.'}
        }
        Write-Host 'Lint analysis passed: no error/fatal incidents. Full report writing was bypassed explicitly; warnings remain in XML.'
    }
    & (Join-Path $PSScriptRoot 'Check.ps1') -Jdk $Jdk -WorkDirectory (Join-Path ([IO.Path]::GetFullPath($WorkDirectory)) 'checks')
}finally{Pop-Location}
Get-FileHash -LiteralPath (Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk') -Algorithm SHA256
Write-Host 'Build and checks complete. This script never connects to or changes a TV.'
