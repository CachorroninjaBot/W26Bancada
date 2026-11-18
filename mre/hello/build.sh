#!/usr/bin/env bash
# Build de app MRE (.vxp) sem o SDK proprietario da MediaTek.
# Baseado em https://github.com/gtrxAC/mre-makefile (MIT, ver LICENSE).
# Requer: arm-none-eabi-gcc (xPack ou ARM GNU Toolchain) + Python 3.
#
# Toolchain: exporte MRE_GCC_BIN=<dir com arm-none-eabi-gcc> ou tenha no PATH.
# Nesta maquina: tmp/xpack-arm-none-eabi-gcc-*/bin (nao versionado).
set -e
cd "$(dirname "$0")"

BIN="${MRE_GCC_BIN:-}"
if [ -z "$BIN" ]; then
  if command -v arm-none-eabi-gcc >/dev/null; then
    BIN="$(dirname "$(command -v arm-none-eabi-gcc)")"
  else
    BIN="$(ls -d ../../tmp/xpack-arm-none-eabi-gcc-*/bin 2>/dev/null | head -1)"
  fi
fi
if [ -z "$BIN" ] || { [ ! -x "$BIN/arm-none-eabi-gcc.exe" ] && [ ! -x "$BIN/arm-none-eabi-gcc" ]; }; then
  echo "erro: arm-none-eabi-gcc nao encontrado (defina MRE_GCC_BIN)" >&2
  exit 1
fi
GCC="$BIN/arm-none-eabi-gcc"
[ -x "$GCC.exe" ] && GCC="$GCC.exe"
OBJCOPY="$BIN/arm-none-eabi-objcopy"
[ -x "$OBJCOPY.exe" ] && OBJCOPY="$OBJCOPY.exe"
PY="$(command -v python3 || command -v python)"
SDK=sdk
APP="${MRE_OUT:-MyMREApp}"

INCLUDES="-I $SDK/include -I src"
DEFINES="-D _MINIGUI_LIB_ -D _USE_MINIGUIENTRY -D _NOUNIX_ -D _FOR_WNC -D __MRE_SDK__ -D __MRE_VENUS_NORMAL__ -D __MMI_MAINLCD_240X320__ -D MRE -D GCC -D __MRE_COMPILER_GCC__"
SHARED="-c -fpic -march=armv5te -fvisibility=hidden -Os -mlittle-endian $INCLUDES $DEFINES -fdata-sections -ffunction-sections"
LINK_LIB="-lstdc++ $SDK/lib/*.a"
# --no-dynamic-linker: binutils novo injeta PT_INTERP /usr/lib/ld.so.1, que nao existe no relogio
LINK="-fno-threadsafe-statics -Wl,--gc-sections -fpic -fpcc-struct-return -disable-libstdcxx-verbose -pie -Wl,--no-dynamic-linker -lm -T $SDK/scat.ld"

# MRE_EXEC=1: layout legado ADS (EXEC, ER_RO@0x8000, ER_RW/ER_ZI@0) para VMs antigas
if [ "${MRE_EXEC:-0}" = "1" ]; then
  SHARED="${SHARED/-fpic/-fno-pic}"
  LINK="-fno-threadsafe-statics -Wl,--gc-sections -fno-pic -fpcc-struct-return -disable-libstdcxx-verbose -no-pie -nostartfiles -lm -T $SDK/scat_exec.ld"
fi

rm -rf build; mkdir -p build
for f in src/*.c; do
  "$GCC" $SHARED -std=gnu89 -Wno-implicit-function-declaration -Wno-int-conversion -c "$f" -o "build/$(basename "${f%.c}").o"
done
"$GCC" -o "build/$APP.axf" build/*.o $LINK_LIB $LINK
# sem -I: arm-none-eabi-objcopy ja sabe ler elf32-littlearm (o -I generico zerava EM_ARM)
"$OBJCOPY" --add-section .vm_res=$SDK/resource.bin "build/$APP.axf" "build/$APP.vxp"
"$PY" $SDK/build.py "build/$APP.vxp" "$APP.vxp"
ls -l "$APP.vxp"
