# Lmg Client installer / updater for Windows.
#
# What it does:
#   1. Installs Fabric for the Minecraft version in download/manifest.json
#   2. Downloads Lmg Client, Fabric API, Sodium, Lithium and Iris into
#      %APPDATA%\.lmgclient\mods (its own folder, so it never clashes with other mods)
#   3. Adds an "Lmg Client" profile to the Minecraft Launcher
# Running it again updates everything and removes old versions.
#
# Normally started by Install-Lmg-Client.bat, which downloads the newest copy of this file.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes downloads much faster in Windows PowerShell
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo = 'lmgoffical/Lmg-Client'
$Branch = 'claude/modest-cray-bg98zr'
$Raw = "https://raw.githubusercontent.com/$Repo/$Branch"
$ProfileKey = 'lmg-client'
$ProfileName = 'Lmg Client'
$UserAgent = 'LmgClientInstaller/1.0 (github.com/lmgoffical/Lmg-Client)'

function Say([string]$Message, [string]$Color = 'Gray') { Write-Host $Message -ForegroundColor $Color }
function Step([string]$Message) { Write-Host ''; Write-Host "> $Message" -ForegroundColor Cyan }

function Get-Json([string]$Url) {
    # Parse explicitly: GitHub serves .json files as text/plain, which Invoke-RestMethod
    # would hand back as a plain string.
    $content = (Invoke-WebRequest -Uri $Url -UserAgent $UserAgent -UseBasicParsing).Content
    if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
    $result = $content | ConvertFrom-Json
    # Windows PowerShell 5.1 returns a JSON list as ONE object; Write-Output unrolls it
    # so @(Get-Json ...) always gives a normal list.
    Write-Output $result
}

function Save-File([string]$Url, [string]$Path, [string]$Sha1) {
    $tmp = "$Path.part"
    Invoke-WebRequest -Uri $Url -OutFile $tmp -UserAgent $UserAgent -UseBasicParsing
    if ($Sha1) {
        $actual = (Get-FileHash -Path $tmp -Algorithm SHA1).Hash.ToLower()
        if ($actual -ne $Sha1.ToLower()) {
            Remove-Item $tmp -Force
            throw "The download of $(Split-Path $Path -Leaf) was damaged. Please run the installer again."
        }
    }
    Move-Item -Path $tmp -Destination $Path -Force
}

function Test-Sha1([string]$Path, [string]$Sha1) {
    if (-not $Sha1 -or -not (Test-Path $Path)) { return $false }
    return (Get-FileHash -Path $Path -Algorithm SHA1).Hash.ToLower() -eq $Sha1.ToLower()
}

function Find-Launcher {
    $paths = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Minecraft Launcher\MinecraftLauncher.exe'),
        (Join-Path $env:ProgramFiles 'Minecraft Launcher\MinecraftLauncher.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Minecraft Launcher\MinecraftLauncher.exe')
    )
    foreach ($p in $paths) { if ($p -and (Test-Path $p)) { return $p } }
    return $null
}

Write-Host ''
Write-Host '   Lmg Client installer' -ForegroundColor White
Write-Host '   --------------------' -ForegroundColor DarkGray

try {
    # GitHub caches branch files for several minutes, so a fresh release could look old.
    # Ask the API for the newest commit and download from that exact commit instead.
    try {
        $sha = (Invoke-WebRequest -Uri "https://api.github.com/repos/$Repo/commits/$Branch" `
                -Headers @{ Accept = 'application/vnd.github.sha' } -UserAgent $UserAgent -UseBasicParsing).Content
        if ($sha -is [byte[]]) { $sha = [Text.Encoding]::ASCII.GetString($sha) }
        $sha = ([string]$sha).Trim()
        if ($sha -match '^[0-9a-f]{40}$') { $Raw = "https://raw.githubusercontent.com/$Repo/$sha" }
    } catch {
        # fall back to the branch URL (may be a few minutes behind)
    }

    $mc = Join-Path $env:APPDATA '.minecraft'
    $profileFiles = @('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') |
        ForEach-Object { Join-Path $mc $_ } | Where-Object { Test-Path $_ }
    if (-not $profileFiles) {
        Say "Couldn't find the Minecraft Launcher's files in $mc." Red
        Say 'Open the Minecraft Launcher once (and sign in), close it, then run this again.' Yellow
        exit 1
    }

    # The launcher rewrites its profile list when it closes, which would undo our changes.
    if (Get-Process -Name 'MinecraftLauncher', 'Minecraft' -ErrorAction SilentlyContinue) {
        Say 'The Minecraft Launcher is open. Please close it first.' Yellow
        Read-Host 'Press Enter once it is closed'
    }

    # ---- what to install ----
    Step 'Checking for the latest version'
    $manifest = Get-Json "$Raw/download/manifest.json"
    $mcVersion = [string]$manifest.minecraft
    Say "  Minecraft $mcVersion, Lmg Client $($manifest.version)"

    # ---- Fabric ----
    Step 'Setting up Fabric'
    $loaders = @(Get-Json "https://meta.fabricmc.net/v2/versions/loader/$mcVersion")
    if ($loaders.Count -eq 0) { throw "Fabric doesn't support Minecraft $mcVersion yet." }
    $stable = $loaders | Where-Object { $_.loader.stable } | Select-Object -First 1
    if (-not $stable) { $stable = $loaders[0] }
    $loaderVersion = [string]$stable.loader.version
    $versionId = "fabric-loader-$loaderVersion-$mcVersion"
    $versionDir = Join-Path $mc "versions\$versionId"
    New-Item -ItemType Directory -Force -Path $versionDir | Out-Null
    $fabricJson = (Invoke-WebRequest -Uri "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVersion/profile/json" -UserAgent $UserAgent -UseBasicParsing).Content
    if ($fabricJson -is [byte[]]) { $fabricJson = [Text.Encoding]::UTF8.GetString($fabricJson) }
    [IO.File]::WriteAllText((Join-Path $versionDir "$versionId.json"), $fabricJson)
    $dummyJar = Join-Path $versionDir "$versionId.jar"
    if (-not (Test-Path $dummyJar)) { [IO.File]::WriteAllBytes($dummyJar, [byte[]]@()) }
    Say "  Fabric Loader $loaderVersion"

    # ---- game folder ----
    $gameDir = Join-Path $env:APPDATA '.lmgclient'
    $modsDir = Join-Path $gameDir 'mods'
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    # First install: bring over your settings, keybinds and server list.
    foreach ($f in 'options.txt', 'servers.dat') {
        $src = Join-Path $mc $f
        $dst = Join-Path $gameDir $f
        if ((Test-Path $src) -and -not (Test-Path $dst)) { Copy-Item $src $dst }
    }
    # Share worlds and resource packs with normal Minecraft (folder links, no copies).
    foreach ($f in 'saves', 'resourcepacks') {
        $src = Join-Path $mc $f
        $dst = Join-Path $gameDir $f
        New-Item -ItemType Directory -Force -Path $src | Out-Null
        if (-not (Test-Path $dst)) { New-Item -ItemType Junction -Path $dst -Target $src | Out-Null }
    }

    # ---- mods ----
    Step 'Downloading mods'
    $mods = @()
    $mods += [pscustomobject]@{
        Name = 'Lmg Client'; File = [string]$manifest.lmgClient.file
        Url = "$Raw/download/$($manifest.lmgClient.file)"; Sha1 = [string]$manifest.lmgClient.sha1
        Pattern = 'lmg-client-*.jar'
    }
    # Fabric API, Sodium, Lithium, Iris: exact versions that were checked to work together
    # (listed in manifest.json by tools/release.py).
    foreach ($m in @($manifest.mods)) {
        $mods += [pscustomobject]@{
            Name = [string]$m.name; File = [string]$m.file; Url = [string]$m.url
            Sha1 = [string]$m.sha1; Pattern = [string]$m.pattern
        }
    }

    foreach ($mod in $mods) {
        $dest = Join-Path $modsDir $mod.File
        # remove older versions of the same mod
        Get-ChildItem -Path $modsDir -Filter $mod.Pattern -File |
            Where-Object { $_.Name -ne $mod.File } | Remove-Item -Force
        if (Test-Sha1 $dest $mod.Sha1) {
            Say "  $($mod.Name): up to date" DarkGray
            continue
        }
        Say "  $($mod.Name): downloading $($mod.File)"
        Save-File $mod.Url $dest $mod.Sha1
    }

    # ---- launcher profile ----
    Step "Adding the ""$ProfileName"" profile to the launcher"
    $iconBytes = (Invoke-WebRequest -Uri "$Raw/src/main/resources/assets/lmgclient/icon.png" -UserAgent $UserAgent -UseBasicParsing).Content
    $icon = 'data:image/png;base64,' + [Convert]::ToBase64String([byte[]]$iconBytes)
    $ramGb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
    $heap = if ($ramGb -ge 16) { 4 } elseif ($ramGb -ge 8) { 3 } else { 2 }
    $javaArgs = "-Xmx${heap}G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M"
    $now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')

    foreach ($path in $profileFiles) {
        Copy-Item $path "$path.lmg-backup" -Force
        $data = Get-Content -Path $path -Raw -Encoding UTF8 | ConvertFrom-Json
        if (-not $data.profiles) {
            $data | Add-Member -NotePropertyName 'profiles' -NotePropertyValue ([pscustomobject]@{}) -Force
        }
        $old = $data.profiles.PSObject.Properties[$ProfileKey]
        $entry = [pscustomobject]@{
            name          = $ProfileName
            type          = 'custom'
            created       = $(if ($old -and $old.Value.created) { $old.Value.created } else { $now })
            lastUsed      = $now
            icon          = $icon
            lastVersionId = $versionId
            gameDir       = $gameDir
            javaArgs      = $(if ($old -and $old.Value.javaArgs) { $old.Value.javaArgs } else { $javaArgs })
        }
        $data.profiles | Add-Member -NotePropertyName $ProfileKey -NotePropertyValue $entry -Force
        [IO.File]::WriteAllText($path, ($data | ConvertTo-Json -Depth 32))
    }
    Say '  Done'

    Step 'All set!'
    Say '  Open the Minecraft Launcher, pick "Lmg Client" next to the Play button, and press Play.' White
    Say '  Run this installer again any time to update.' DarkGray
    Write-Host ''
    $launcher = Find-Launcher
    $answer = Read-Host 'Open the Minecraft Launcher now? (Y/n)'
    if ($answer -notmatch '^[nN]') {
        if ($launcher) {
            Start-Process -FilePath $launcher
        } else {
            # Microsoft Store / Xbox app version of the launcher
            Start-Process 'explorer.exe' 'shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft'
        }
    }
} catch {
    Write-Host ''
    Say 'Something went wrong:' Red
    Say "  $($_.Exception.Message)" Red
    Say 'Send a screenshot of this window if you need help.' Yellow
    exit 1
}
