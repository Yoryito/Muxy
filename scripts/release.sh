#!/usr/bin/env bash
#
# Publica una versión de Muxy en GitHub Releases.
#
#   scripts/release.sh 0.1.11 "Lo que cambia en esta versión"
#
# Hace todo lo que tiene que pasar junto: subir la versión, compilar el APK
# firmado, commitear, etiquetar y subir la release con el APK colgado. La app
# instalada mira esa release al abrirse, así que publicar aquí es lo que hace
# que el móvil de enfrente se entere.
#
# El texto de las notas es lo que sale en el aviso emergente de la app: escribir
# algo que se entienda leyéndolo en un móvil, no un changelog técnico.

set -euo pipefail

cd "$(dirname "$0")/.."

version="${1:-}"
notes="${2:-}"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Uso: scripts/release.sh <version> [notas]    (por ejemplo: 0.1.11)" >&2
  exit 1
fi

if [[ -z "$notes" ]]; then
  echo "Faltan las notas: son lo que lee el usuario en el aviso de actualización." >&2
  exit 1
fi

# En esta máquina JAVA_HOME no está en el entorno y Gradle no arranca sin él.
export JAVA_HOME="${JAVA_HOME:-F:/Java Open JDK/Hotspot}"

if [[ ! -f keystore.properties ]]; then
  echo "No hay keystore.properties: el APK saldría sin firmar y no se podría instalar." >&2
  exit 1
fi

if git rev-parse "v$version" >/dev/null 2>&1; then
  echo "La etiqueta v$version ya existe. Sube el número de versión." >&2
  exit 1
fi

echo "==> Versión $version"
# La versión vive en una sola línea del build: el versionCode se calcula de ella.
sed -i -E "s/^val muxyVersionName = \".*\"$/val muxyVersionName = \"$version\"/" app/build.gradle.kts
grep -q "val muxyVersionName = \"$version\"" app/build.gradle.kts

echo "==> Compilando el APK firmado"
./gradlew --quiet assembleRelease

apk="build/muxy-$version.apk"
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk "$apk"

echo "==> Commit y etiqueta"
git add -A
git commit -m "$version: $notes" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
git tag "v$version"
git push origin HEAD --tags

echo "==> Publicando la release"
gh release create "v$version" \
  --title "Muxy $version" \
  --notes "$notes" \
  "$apk"

echo
echo "Listo. La app instalada verá la $version la próxima vez que se abra."
