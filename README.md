# W26 Bancada — reprogramação do relógio "Watch 6" (clone W26)

Este repositório é a bancada de engenharia reversa + app Android próprio para o
relógio W26. Objetivo final: substituir o app oficial (M Activie, `com.micro.active`
v3.6.2) por um app nosso, criar mostradores e apps MRE próprios, e ter canal próprio
de atualização (VXP via SPP) — tudo pelo celular, sem gravar firmware.

> Pasta de trabalho nova (18/11/2026): `C:\Users\luiz.DESKTOP-FH1LM9Q\Downloads\w26-ble`.
> A cópia antiga em `Documents\Qoder\2026-11-18\b9711aab\w26-ble` fica como arquivo.
> Regras: nunca usar `D:\` para dados (só jogos); nunca commitar a partir do repo
> acidental `C:\` (remote GolSite); filtros de captura devem excluir os MACs do relógio.

## 1. Relógio alvo

| Item | Valor |
|---|---|
| Modelo | "Watch 6" / W26 (clone MTK) |
| Firmware | `W26_B_BL6133_201128_1` |
| BLE (GATT) MAC | `C2:BA:45:15:C8:F9` |
| BT clássico (SPP) | `06:87:E6:0C:62:61` |
| Tela | 176x220 (tag 0x10 do VXP dos mostradores oficiais) |

O relógio tem dois canais de rádio em paralelo:

1. **BLE GATT** — usado pelo app oficial para notificações MTK 3.0 (SYNC/REQV/ACKY…).
2. **Bluetooth clássico / SPP** (UUID `00001101`) — canal do **instalador MREE**
   (instala/remove apps e mostradores `.vxp`). É o canal que usamos para instalar.

## 2. Protocolo SPP / MTK 3.0

Frame: `F0 F0 F0 F1 | len(BE32) | RC4(plaintext + '\0') | payload cru`

Chave RC4:
`"we had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way\0"`

Handshake que o relógio aceita:
`MREE mre_installer smartwatch <cmd> <part> <len> ` + payload.
Resposta: `MREE <code>` (1 = ok; -101 = embutido/não removível; -106 = comando
não suportado; -1000 = "erro desconhecido" do instalador).

Comandos usados:
- `0` instalar: part0 = `"<len> <nome>"`, part1 = bytes do vxp
- `5` listar
- `-1` uninstall, `-2` status, `-6` delete (o W26 responde -106 = não suportado)
- `9` CTA (não suportado por este relógio → instalamos "sem CTA")

Também existe `$LHBT`/TJD para comandos de texto (ex.: `X1,LINK`), notificações
MTK e `L4Comm.BrltWallpaperPush` (caminho de wallpaper do app oficial).

## 3. App Android (bancada)

- `app/` — app Java sem Gradle (aapt2 + javac + d8 + apksigner).
- `app/src/com/luiz/w26/W26.java` — GATT + SPP + fila de instalação MREE.
- `app/src/com/luiz/w26/Mtk.java` — montagem de frames MTK.
- `app/src/com/luiz/w26/MainActivity.java` — tela de testes com log.
- `app/assets/*.vxp` — mostradores embarcados (face.vxp, luizface, luizface2, ref=Longines).

Build: `cd app && bash build.sh` → `app/dist/w26.apk`.
Instalar: `adb install -r app/dist/w26.apk` e abrir "W26 Bancada".

## 4. Formato VXP (MRE)

- Cabeçalho ELF32 ARM (little-endian) **embutido no arquivo**, seguido de bloco de tags.
- Tags relevantes: `0x01` vendor, `0x04` nome, `0x05` versão, `0x10` resolução
  (2x int16 → `0x00B000DC` = 176x220), `0x11` engine `0x09C40000`,
  `0x21` compilador (0=ADS, 6=GCC), `0x2F` imagem AB2, `0x31` ícone avançado,
  `0x33` cria atalho, `0x34` é mostrador de relógio.
- ELF dos vxp oficiais (ADS): `ET_EXEC`, entry `0x8000`, um único `PT_LOAD`
  (`p_offset=0x34`, `p_vaddr=0x8000`, `p_flags=0xB0000007`), seções
  `ER_RO@0x8000`, `ER_RW@0`, `ER_ZI@4`, `.vm_res` com `SHF_ALLOC`.
- `.vm_res` dos oficiais fica **sempre em `0x4b4c`** (mesmo em faces diferentes) e o
  diretório de recursos guarda **offsets absolutos no arquivo**:
  - `AppLogo.img` +0x0a4, `clock_bg.png` +0x884, `hand.png` +0x5398,
    `minute.png` +0x5944, `second.png` +0x6040, `preview.png` +0x6650,
    `configtbl.bin` +0x8f4c, `mre-2.0` (tabela de ids) +0x8f5c.
  - Diretório: `"nome\0" <u32 offset_abs> <u32 tamanho>` repetido; tabela
    `mre-2.0` tem pares `(id, offset_abs)` terminados por `0xFFFFFFFF`.
- Imagens são VRE3 ("Transfer image file to Mediatek format"): início
  `34 01 <len16>`; preview = `34 01 f2 28` (150x150). Logo tem ASCII
  `VREAPPLOGO09BVREJPG`.

Nosso toolchain sem ADS: `mre/hello/` compila com `arm-none-eabi-gcc` (xPack),
`MRE_EXEC=1` gera layout EXEC estilo ADS; `sdk/build.py` insere tags;
`sdk/fixelf.py` replica o segmento único; `sdk/reflow.py` posiciona `.vm_res` em
`0x4b4c` (layout idêntico ao oficial); `sdk/build.sh` encadeia tudo.

Comando de build do mostrador com recursos oficiais (atual):

```bash
cd mre/hello
export MRE_GCC_BIN="/c/Users/luiz.DESKTOP-FH1LM9Q/Documents/Qoder/2026-11-18/b9711aab/w26-ble/tmp/xpack-arm-none-eabi-gcc-15.2.1-1.1/bin"
MRE_EXEC=1 MRE_RES=sdk/resource_long.bin MRE_NOFIX=1 MRE_REFLOW=0x4b4c \
  MRE_OUT=LuizFaceRes bash build.sh
```

## 5. O que já está provado

1. O canal MREE-over-SPP instala mostradores reais no relógio (o Longines oficial
   `refface.vxp`, 57 KB, instalou e apareceu na lista do relógio).
2. O relógio **renderiza o fundo a partir do `.vm_res`** — o mostrador Longines
   apareceu com fundo, mas **sem ponteiros**.
3. Nosso VXP **17 KB (recursos mínimos) instala** com o layout ELF novo → layout
   ELF/tags ok para o instalador.
4. Nosso VXP com o `.vm_res` oficial de 36 KB foi **recusado** (-1000) duas vezes:
   - uma com `.vm_res` em `0x27a8` (offsets realocados);
   - outra com `.vm_res` em `0x4b4c` (offsets absolutos originais, 60 KB);
   - e mais uma com o reflow completo (56,4 KB, quase idêntico ao oficial).
   Hipótese atual: o instalador recusa **nome já existente** (`luizface2.vxp` já
   estava instalado desde o teste de 17 KB) — o erro -1000 apareceu depois do
   teste que instala com sucesso, então pode não ser conteúdo.
5. Após as tentativas frustradas, **os dois mostradores custom (Longines e o nosso)
   passaram a renderizar preto** — o estado da face foi corrompido de alguma forma.
   Falta: reiniciar o relógio e ver se o Longines volta (fundo aparece/ponteiros não).

## 6. Conclusões atuais sobre modo mostrador

- O firmware desenha o fundo (`clock_bg`) direto do `.vm_res`; a ausência dos
  ponteiros no Longines sugere que **o código do VXP não roda nesse modo** (ou roda
  em sandbox sem acesso ao layer). Precisamos provar com sinal não-visual:
  `vm_vibrator_once()` no `vm_main` + timer de 3 s (já está no `src/test.c`).
- Se o código não rodar em modo face, o caminho para "meu mostrador" é empacotar
  imagens próprias no `.vm_res` (fundo/ponteiros) no formato VRE3, como os oficiais.

## 7. Estado atual (18/11/2026, ~04h)

- Relógio: mostradores custom instalados (`refface.vxp` Longines, `luizface2.vxp`
  nosso 17 KB) **aparecem pretos** agora.
- `luizface2.vxp` (56,4 KB, recursos oficiais, reflow) **não instalou** (-1000);
  a próxima tentativa deve usar **nome novo** (`luizface3.vxp`) e, de preferência,
  depois de reiniciar o relógio.
- Celular: APK da bancada instalado; botões atuais: "Instalar LuizFace RES",
  "Instalar face REF (oficial)", "Limpar testes MRE".

## 8. Próximos passos

1. Reiniciar o relógio (segurar botão) e conferir se o Longines volta a renderizar
   o fundo; reinstalar `refface.vxp` se necessário.
2. Instalar o reflow (`luizface3.vxp`) com nome inédito; observar fundo e vibração
   (vibração = nosso código executa em modo face).
3. Se fundo aparecer: substituir imagens VRE3 (fundo/ponteiros) por arte própria
   (PACKRES/ResEditor do SDK MTK em `tmp/mresdk/tools` na cópia antiga).
4. Restaurar o mostrador analógico completo (`/tmp/luizface_backup.c`) quando houver
   renderização; depois auto-start (tag 0x32) e iteração de design.
5. Roadmap maior: mapear APIs do relógio (arquivos/sensores/LBS), interface própria,
   sincronização celular↔relógio sobre SPP, "finder" (última localização + data,
   alarme/vibração no relógio) e atualização de VXP pelo nosso app.

## 9. Estrutura

```
app/                app Android da bancada (W26.java, Mtk.java, MainActivity.java)
mre/hello/          mostrador/app MRE (C + build.sh + sdk/)
  src/test.c        teste de vibração + tela vermelha (diagnóstico)
  sdk/              build.py (tags), fixelf.py, reflow.py, layout.py, scat*.ld
  sdk/resource.bin       recursos mínimos de teste
  sdk/resource_long.bin  .vm_res oficial do Longines (36.836 B)
*.py                scripts de captura/protocolo BT usados na engenharia reversa
```
