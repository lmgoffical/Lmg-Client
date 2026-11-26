#!/usr/bin/env bash
# Builds the Lmg Client desktop app (Windows, .NET Framework 4.8) and puts it in download/.
# Needs the .NET SDK (8+). Then run `python tools/release.py` to publish it in the manifest.
set -euo pipefail
cd "$(dirname "$0")"
dotnet build -c Release -nologo -v quiet
cp "bin/Release/net48/Lmg Client.exe" ../download/Lmg-Client.exe
echo "Built download/Lmg-Client.exe ($(cat VERSION))"
