@echo off
title Lmg Client Installer
echo Getting the latest Lmg Client installer...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; $p=Join-Path $env:TEMP 'lmg-client-install.ps1'; try { Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/lmgoffical/Lmg-Client/claude/modest-cray-bg98zr/installer/install.ps1' -OutFile $p } catch { Write-Host 'Could not download the installer. Check your internet connection.' -ForegroundColor Red; exit 1 }; & $p"
echo.
pause
