# hello — app MRE (.vxp) feito por nos

Primeiro app MRE compilado fora do SDK proprietario da MediaTek e instalado no
Watch 6 pela bancada (`MREE mre_installer smartwatch 0 ...`). Responde `1`
("instalado") e o relogio aceita.

## Origem

Projeto base: [gtrxAC/mre-makefile](https://github.com/gtrxAC/mre-makefile) (MIT,
ver `LICENSE`). `sdk/` traz os headers VRE, as libs `per*.a` pre-compiladas,
`resource.bin`, `tags.bin` e `scat.ld` do repo original. `src/` e o exemplo
"Hello, world!" do SDK da MediaTek.

## Build

```sh
export MRE_GCC_BIN=/caminho/arm-none-eabi-gcc/bin   # xPack ou ARM GNU Toolchain
bash build.sh
```

O `build.sh` aplica duas correcoes que o Makefile original nao cobre no binutils
novo:

- `-Wl,--no-dynamic-linker`: sem isso o linker injeta `PT_INTERP
  /usr/lib/ld.so.1`, que nao existe no relogio.
- `objcopy` sem `-I elf32-little`: com `-I` generico o campo `EM_ARM` do header
  era zerado.

Saida: `MyMREApp.vxp` (ET_DYN ARM, entry `gcc_entry`, `.vm_res` + tags do
`build.py`).

## Instalar no relogio

1. Conectar SPP na bancada (Android, `com.luiz.w26`).
2. "Instalar VXP..." -> escolher o `.vxp` no celular.

Comportamento observado do instalador (`MREE`, cmd 0):

- `1` = instalado; nomes existentes sao sobrescritos (`face.vxp` aceitou duas
  instalacoes seguidas; reinstalar o mostrador original de 57 KB em cima do
  proprio deu `-1000`).
- `-2 <nome>` = status (`1` instalado, `0` nao).
- `5` = lista (`mre_installer 5 <nome,versao,...>`); nao muda ao trocar o
  conteudo de um nome ja existente.
- `7` (permissoes) nao responde neste firmware.
