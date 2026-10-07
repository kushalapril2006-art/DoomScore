# Passwords are read locally from Windows-protected storage, never from command arguments.
param([string]$Project=(Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference='Stop'
$Project=(Resolve-Path -LiteralPath $Project).Path
$credentialPath=Join-Path $Project 'private-signing\upload-password.clixml'
if(!(Test-Path -LiteralPath $credentialPath)){throw 'No protected signing password found. Set the upload password environment variables for your existing key instead.'}
$credential=Import-Clixml -LiteralPath $credentialPath
$oldStore=$env:DOOMSCORE_UPLOAD_STORE_PASSWORD
$oldKey=$env:DOOMSCORE_UPLOAD_KEY_PASSWORD
try {
    $env:DOOMSCORE_UPLOAD_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    $env:DOOMSCORE_UPLOAD_KEY_PASSWORD=$credential.GetNetworkCredential().Password
    Push-Location $Project
    try { & .\gradlew.bat :app:bundleRelease :app:assembleRelease --no-daemon --console=plain; if($LASTEXITCODE -ne 0){throw 'Production build did not pass its release checks.'} }
    finally {Pop-Location}
} finally {
    $env:DOOMSCORE_UPLOAD_STORE_PASSWORD=$oldStore
    $env:DOOMSCORE_UPLOAD_KEY_PASSWORD=$oldKey
}
