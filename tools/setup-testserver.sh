#!/usr/bin/env bash
# Sets up a throw-away Fabric 1.21.1 dedicated server with Fuji + toNeko + JustARod
# + this bridge mod, so the chat integration can be verified end to end.
#
# Usage: bash tools/setup-testserver.sh [target-dir]   (default: ./testserver)
set -euo pipefail

DIR="${1:-testserver}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MC=1.21.1
LOADER=0.20.0
INSTALLER=1.1.2
FABRIC_API=0.116.17+1.21.1
FUJI=fuji-14.12.0-mc1.21.1.jar
TONEKO=toneko-1.9.6.jar
JUSTAROD_URL="https://github.com/CSneko/JustARod/releases/download/V0.1.22/JustARod-0.2.2.jar"

mkdir -p "$DIR/mods"
cd "$DIR"

echo "==> Fabric server launcher"
curl -fsSL -o fabric-server-launch.jar \
  "https://meta.fabricmc.net/v2/versions/loader/${MC}/${LOADER}/${INSTALLER}/server/jar"

echo "==> Fabric API"
curl -fsSL -o "mods/fabric-api-${FABRIC_API}.jar" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API//+/%2B}/fabric-api-${FABRIC_API//+/%2B}.jar"

echo "==> Fuji / toNeko (reuse the compile-time jars from libs/)"
cp "$PROJECT_ROOT/libs/$FUJI"    mods/
cp "$PROJECT_ROOT/libs/$TONEKO"  mods/

echo "==> JustARod 0.2.2 (last Kotlin/Fabric 1.21.1 build before the NeoForge refactor)"
curl -fsSL -o mods/justarod-0.2.2.jar "$JUSTAROD_URL"

echo "==> toNeko / JustARod runtime dependencies (via Modrinth API)"
python3 - "$DIR" <<'PY'
import json, os, sys, urllib.request

target = sys.argv[1]
UA = {"User-Agent": "bridge-toneko-fuji-test/1.0"}

def api(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60))

picks = {
    "geckolib": lambda v: v["version_number"].startswith("4.8.3"),
    "playeranimator": lambda v: v["version_number"].startswith("2.0.4"),
    "fabric-language-kotlin": lambda v: True,
    "ctlibmod": lambda v: v["version_number"] == "0.1.13",
}
for slug, test in picks.items():
    url = ("https://api.modrinth.com/v2/project/%s/version"
           "?game_versions=%%5B%%221.21.1%%22%%5D&loaders=%%5B%%22fabric%%22%%5D" % slug)
    versions = [v for v in api(url) if test(v)]
    if not versions:
        print("  !! no candidate for", slug)
        continue
    f = versions[0]["files"][0]
    out = os.path.join(target, "mods", f["filename"])
    urllib.request.urlretrieve(f["url"], out)
    print("  ok", f["filename"])
PY

echo "==> this mod"
cp "$PROJECT_ROOT/build/libs/"*.jar mods/
rm -f mods/*-sources.jar

echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
spawn-protection=0
max-tick-time=-1
EOF

echo
echo "Done. Start it with (a fifo lets the test script type into the server console):"
echo "  cd $PWD && rm -f console.fifo && mkfifo console.fifo"
echo "  tail -f console.fifo | java -Xmx2G -jar fabric-server-launch.jar nogui"
echo "Then, in another shell, verify the chat integration:"
echo "  cd $PROJECT_ROOT/tools && npm i mineflayer"
echo "  TB_FIFO=$PWD/console.fifo node verify-chat.js"
