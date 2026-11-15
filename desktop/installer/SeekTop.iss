; Both installer and portable ZIP consume the same verified app-image.
#ifndef AppVersion
  #error AppVersion is required
#endif
#ifndef AppImage
  #error AppImage is required
#endif
#ifndef RepoRoot
  #error RepoRoot is required
#endif
#ifndef MetadataDir
  #error MetadataDir is required
#endif
#ifndef ReleaseDir
  #error ReleaseDir is required
#endif
#ifndef AppIdentity
  #define AppIdentity "ccwan.SeekTop.Desktop"
#endif
#ifndef InstallerStateDir
  #define InstallerStateDir "{localappdata}\SeekTop\installer"
#endif

[Setup]
AppId={#AppIdentity}
AppName=SeekTop
AppVersion={#AppVersion}
AppPublisher=SeekTop contributors
AppPublisherURL=https://github.com/ccwan-mamoon/seektop
AppSupportURL=https://github.com/ccwan-mamoon/seektop/issues
DefaultDirName={localappdata}\Programs\SeekTop
DefaultGroupName=SeekTop
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
DisableProgramGroupPage=yes
UninstallFilesDir={#InstallerStateDir}
UninstallDisplayIcon={app}\SeekTop.exe
OutputDir={#ReleaseDir}
OutputBaseFilename=SeekTop-{#AppVersion}-windows-x64-setup
SetupIconFile={#RepoRoot}\desktop\icons\seektop.ico
LicenseFile={#RepoRoot}\LICENSE
Compression=lzma2/normal
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
UsePreviousAppDir=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; Flags: unchecked

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Excludes: "\app\resources\browser-runtime\NodysseyBrowser.exe,\app\resources\browser-runtime\electron.exe"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#RepoRoot}\LICENSE"; DestDir: "{app}"; DestName: "LICENSE-Nodyssey.txt"; Flags: ignoreversion
Source: "{#RepoRoot}\NOTICE.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#MetadataDir}\SOURCE.txt"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
; Keep this separate from the native toast activator's flat SeekTop.lnk.
Name: "{autoprograms}\SeekTop\SeekTop"; Filename: "{app}\SeekTop.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\SeekTop"; Filename: "{app}\SeekTop.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\SeekTop.exe"; Description: "{cm:LaunchProgram,SeekTop}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{app}\app\resources\SeekTopLinkRouter.exe"; Parameters: "--retire"; RunOnceId: "SeekTopLinksRetire"; Flags: runhidden waituntilterminated skipifdoesntexist

[UninstallDelete]
; ZIP upgrades can introduce filenames absent from Inno's original uninstall log.
; Delete only the two managed runtime directories, never the separate user data.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
