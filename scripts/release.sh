#!/usr/bin/env bash
# 릴리스 빌드 → dist/ 에 이름 붙여 복사 → 깃허브 릴리스(공개)에 APK 첨부.
#   ./scripts/release.sh "릴리스 제목" [노트 파일]
# 버전은 app/build.gradle.kts 의 versionName. 태그 vX.Y.Z 를 만들어 push 한다.
set -euo pipefail
cd "$(dirname "$0")/.."
. ./env.sh
GH="${GH:-$(command -v gh || echo "$HOME/bin/gh")}"

VER=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
TITLE="${1:-v$VER}"
NOTES="${2:-}"

echo "== 릴리스 빌드 v$VER =="
./gradlew assembleRelease --no-daemon -q
mkdir -p dist
APK="dist/ChoflixClock-v$VER.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
echo "== 준비: $APK ($(du -h "$APK" | cut -f1)) =="

git tag -f "v$VER" >/dev/null
git push -q origin main "v$VER"
if [ -n "$NOTES" ]; then
  "$GH" release create "v$VER" "$APK" --title "$TITLE" --notes-file "$NOTES"
else
  "$GH" release create "v$VER" "$APK" --title "$TITLE" --generate-notes
fi
