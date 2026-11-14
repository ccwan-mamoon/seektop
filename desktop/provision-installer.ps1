param([string]$ToolDirectory = (Join-Path $PSScriptRoot '../build/tooling/inno-setup-7.1.1'))
$ErrorActionPreference = 'Stop'
$taskVersion = '7.1.1'
$taskRoot = [IO.Path]::GetFullPath($ToolDirectory)
$taskInstaller = Join-Path $taskRoot 'innosetup-7.1.1-x64.exe'
$taskCompiler = Join-Path $taskRoot 'compiler/ISCC.exe'
New-Item -ItemType Directory -Force -Path $taskRoot | Out-Null
if (-not (Test-Path -LiteralPath $taskCompiler)) {
    if (-not (Test-Path -LiteralPath $taskInstaller)) {
        Invoke-WebRequest -Uri "https://github.com/jrsoftware/issrc/releases/download/is-7_1_1/innosetup-$taskVersion-x64.exe" -OutFile $taskInstaller
    }
    $taskSignature = Get-AuthenticodeSignature -LiteralPath $taskInstaller
    if ($taskSignature.Status -ne 'Valid' -or $taskSignature.SignerCertificate.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::SimpleName, $false) -ne 'Pyrsys B.V.') {
        throw 'Inno Setup download does not have a valid expected publisher signature'
    }
    $taskProcess = Start-Process -FilePath $taskInstaller -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/CURRENTUSER', '/NOICONS', ('/DIR="' + (Join-Path $taskRoot 'compiler') + '"')) -WindowStyle Hidden -Wait -PassThru
    if ($taskProcess.ExitCode -ne 0) { throw "Inno Setup installation failed: $($taskProcess.ExitCode)" }
}
if (-not (Test-Path -LiteralPath $taskCompiler)) { throw 'Inno Setup compiler was not installed' }
$taskCompilerSignature = Get-AuthenticodeSignature -LiteralPath $taskCompiler
if ($taskCompilerSignature.Status -ne 'Valid' -or $taskCompilerSignature.SignerCertificate.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::SimpleName, $false) -ne 'Pyrsys B.V.') { throw 'Unexpected Inno Setup compiler publisher' }
$taskActualVersion = (& $taskCompiler --version | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $taskActualVersion -ne $taskVersion) { throw "Unexpected Inno Setup compiler version: $taskActualVersion" }
if ($env:GITHUB_ENV) { "SEEKTOP_ISCC=$taskCompiler" | Out-File -LiteralPath $env:GITHUB_ENV -Encoding utf8 -Append }
Write-Output $taskCompiler
