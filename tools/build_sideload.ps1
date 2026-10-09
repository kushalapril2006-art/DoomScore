# Private installation candidate; production release checks remain on build_release.ps1.
# Load signing passwords from Windows-protected local storage, never from arguments/output.
param([string]$Project=(Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference='Stop'
$Project=(Resolve-Path -LiteralPath $Project).Path
$credentialPath=Join-Path $Project 'private-signing\upload-password.clixml'
if(!(Test-Path -LiteralPath $credentialPath)){throw 'Protected release signing credentials are missing.'}
$credential=Import-Clixml -LiteralPath $credentialPath
$previousStorePassword=$env:DOOMSCORE_UPLOAD_STORE_PASSWORD
$previousKeyPassword=$env:DOOMSCORE_UPLOAD_KEY_PASSWORD
try {
    $env:DOOMSCORE_UPLOAD_STORE_PASSWORD=$credential.GetNetworkCredential().Password
    $env:DOOMSCORE_UPLOAD_KEY_PASSWORD=$credential.GetNetworkCredential().Password
    Push-Location $Project
    try {
        & .\gradlew.bat :app:assembleSideload :app:lintSideload --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC' --console=plain
        if($LASTEXITCODE -ne 0){throw 'Private installation candidate build failed.'}
        $apkRoot=Join-Path $Project 'app\build\outputs\apk\sideload'
        $metadata=Get-Content -LiteralPath (Join-Path $apkRoot 'output-metadata.json') -Raw | ConvertFrom-Json
        if($metadata.applicationId -ne 'com.gridcc.doomscore.android' -or $metadata.elements.Count -ne 1){throw 'Unexpected APK identity or split output.'}
        $entry=$metadata.elements[0]
        if($entry.versionName -notmatch '^\d+\.\d+\.\d+$' -or $entry.outputFile -ne 'app-sideload.apk'){throw 'Unexpected APK version/path.'}
        $artifactRoot=Join-Path $Project 'artifacts'
        New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
        $artifact=Join-Path $artifactRoot ('DoomScore-'+$entry.versionName+'.apk')
        Copy-Item -LiteralPath (Join-Path $apkRoot $entry.outputFile) -Destination $artifact -Force
        Write-Output "Private installation candidate saved to $artifact"

    } finally {Pop-Location}
} finally {
    $env:DOOMSCORE_UPLOAD_STORE_PASSWORD=$previousStorePassword
    $env:DOOMSCORE_UPLOAD_KEY_PASSWORD=$previousKeyPassword
}
