#!/bin/bash

# --- CONFIGURACIÓN ---
REPO="Antonio9000-ops/Capri"
ARTIFACT="capri-debug-apk"
DEST_FILE="$HOME/Descargas/Capri.apk"
# El puerto de la depuración inalámbrica cambia en cada reconexión: se toma el primer equipo conectado.
ADB_DEVICE=$(adb devices | awk '$2=="device"{print $1; exit}')

echo "🔍 Buscando la última compilación exitosa en GitHub..."
RUN_ID=$(gh run list --repo "$REPO" --status success --limit 1 --json databaseId --jq '.[0].databaseId')

if [ -z "$RUN_ID" ]; then
    echo "❌ No se encontraron ejecuciones recientes."
    exit 1
fi

echo "📥 Descargando artefacto de la ejecución $RUN_ID..."
TMP_DIR=$(mktemp -d)
gh run download "$RUN_ID" --repo "$REPO" -n "$ARTIFACT" -D "$TMP_DIR"

if [ -f "$TMP_DIR/app-debug.apk" ]; then
    echo "📦 Moviendo APK a $DEST_FILE..."
    mv "$TMP_DIR/app-debug.apk" "$DEST_FILE"
    rm -rf "$TMP_DIR"
    
    echo "📲 Instalando en el dispositivo ($ADB_DEVICE)..."
    if adb -s "$ADB_DEVICE" install -r "$DEST_FILE"; then
        echo "✅ ¡Listo! La nueva versión de Capri se ha instalado."
    else
        echo "❌ No se pudo instalar. Desbloquea el teléfono y comprueba la depuración inalámbrica."
    fi
else
    echo "❌ Error: No se encontró el APK en el artefacto descargado."
    rm -rf "$TMP_DIR"
fi
