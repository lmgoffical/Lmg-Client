@echo off
title Lmg Client Installer
echo Getting the latest Lmg Client installer...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; $repo='lmgoffical/Lmg-Client'; $ref='claude/modest-cray-bg98zr'; try { $s=(Invoke-WebRequest -UseBasicParsing -Uri ('https://api.github.com/repos/'+$repo+'/commits/'+$ref) -Headers @{Accept='application/vnd.github.sha'}).Content; if ($s -is [byte[]]) { $s=[Text.Encoding]::ASCII.GetString($s) }; $s=([string]$s).Trim(); if ($s -match '^[0-9a-f]{40}$') { $ref=$s } } catch {}; $p=Join-Path $env:TEMP 'lmg-client-install.ps1'; try { Invoke-WebRequest -UseBasicParsing -Uri ('https://raw.githubusercontent.com/'+$repo+'/'+$ref+'/installer/install.ps1') -OutFile $p } catch { Write-Host 'Could not download the installer. Check your internet connection.' -ForegroundColor Red; exit 1 }; & $p"
echo.
pause
