#!/bin/bash

# --- CONFIGURACIÓN ---
REPO="Antonio9000-ops/Capri"
ARTIFACT="capri-debug-apk"
# Pon aquí la IP de tu Oppo que te sale en `adb devices`
ADB_DEVICE="192.168.1.100:38195" 
DEST_FILE="$HOME/Descargas/Capri.apk"

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
    adb -s "$ADB_DEVICE" install -r "$DEST_FILE"
    
    echo "✅ ¡Listo! La nueva versión de Capri se ha instalado."
else
    echo "❌ Error: No se encontró el APK en el artefacto descargado."
    rm -rf "$TMP_DIR"
fi
