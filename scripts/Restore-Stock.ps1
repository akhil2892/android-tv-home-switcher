[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='Medium')]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:\[\]-]+$')][string]$Device,
    [string]$Adb = 'adb.exe'
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$profile=Get-Content -LiteralPath (Join-Path $project 'app\src\main\assets\verified-tv.json') -Raw | ConvertFrom-Json
$stock=$profile.stockComponent
$stockPackage=($stock -split '/')[0]
if($profile.user -notmatch '^\d+$' -or $stock -notmatch '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$'){throw 'Invalid recorded profile.'}
if(-not(Get-Command $Adb -ErrorAction SilentlyContinue) -and -not(Test-Path -LiteralPath $Adb)){throw 'Official Android SDK adb.exe is required; supply -Adb if needed.'}
if(-not $PSCmdlet.ShouldProcess($Device,'Verify recorded firmware/user; restore stock package default-enabled state and preferred Home; open stock Home')){return}
$logs=Join-Path $project ('.local\recovery-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $logs | Out-Null
function Capture([string]$Name,[string[]]$Arguments){
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$Adb;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in $Arguments){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    $null=$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
    $timedOut=-not $process.WaitForExit(30000)
    if($timedOut){$process.Kill($true);$process.WaitForExit()}
    $text=$stdout.GetAwaiter().GetResult();$errorText=$stderr.GetAwaiter().GetResult();$exitCode=$process.ExitCode;$process.Dispose()
    @{Command=$Arguments;Exit=$exitCode;TimedOut=$timedOut;Stdout=$text;Stderr=$errorText}|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $logs "$Name.json") -Encoding utf8
    if($timedOut -or $exitCode -ne 0 -or ($text+$errorText) -match '(?im)(SecurityException|Permission Denial|^Error:|^Failure|unauthorized|no devices|cannot connect|failed to connect)'){throw "$Name failed. See $logs"}
    return $text.Trim()
}
$null=Capture 'connect' @('connect',$Device)
$fingerprint=Capture 'fingerprint' @('-s',$Device,'shell','getprop','ro.build.fingerprint')
$activeUser=Capture 'active-user' @('-s',$Device,'shell','am','get-current-user')
if($fingerprint -ne $profile.fingerprint -or $activeUser -ne $profile.user){throw 'TV firmware/user does not match the tested profile. No launcher change made.'}
$restore=Capture 'restore-package' @('-s',$Device,'shell','pm','default-state','--user',$profile.user,$stockPackage)
if($restore -notmatch 'new state: default'){throw 'Stock package restoration was not confirmed.'}
$preferred=Capture 'restore-preference' @('-s',$Device,'shell','cmd','package','set-home-activity','--user',$profile.user,$stock)
if($preferred -ne 'Success'){throw 'Stock Home preference was not confirmed.'}
$resolved=Capture 'verify-home' @('-s',$Device,'shell','cmd','package','resolve-activity','--brief','--user',$profile.user,'-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-c','android.intent.category.DEFAULT')
if(@($resolved -split '\r?\n'|ForEach-Object {$_.Trim()}) -notcontains $stock){throw 'Stock Home resolution did not match.'}
$null=Capture 'open-stock' @('-s',$Device,'shell','am','start','-W','--user',$profile.user,'-n',$stock)
Write-Host 'Stock Home restored and resolution verified. Test the physical Home button. No unrelated package or app data was changed.'
