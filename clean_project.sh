#!/bin/bash
echo "Limpiando proyecto..."

# Matar procesos de gradle
pkill -f "gradle" 2>/dev/null
pkill -f "java" 2>/dev/null

# Esperar un momento
sleep 2

# Limpiar archivos de macOS que causan problemas
find . -name "._*" -delete 2>/dev/null
find . -name ".DS_Store" -delete 2>/dev/null

# Limpiar builds
rm -rf app/build 2>/dev/null
rm -rf build 2>/dev/null
rm -rf .gradle 2>/dev/null
rm -rf .kotlin 2>/dev/null

# Limpiar locks
find . -name "*.lock" -delete 2>/dev/null

echo "Proyecto limpiado. Ahora prueba hacer debug de nuevo."
