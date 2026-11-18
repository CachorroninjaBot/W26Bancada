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
4. Nosso VXP com o `.vm_res` oficial de 36 KB foi **recusado** (-1000) várias vezes.
   **Causa raiz encontrada (18/11):** o `sdk/reflow.py` reconstruía o ELF truncando
   o arquivo no fim dos section headers e **descartava o bloco de tags** que o
   `build.py` appended depois do ELF. Sem a tag `0x04` (nome) o instalador recusa.
   O `layout.py` também quebrava o trailer (deslocava as tags sem atualizar
   `tags_pos`). Ambos corrigidos: agora preservam as tags e reescrevem o trailer.
   `LuizFaceRes.vxp` rebuildado = estruturalmente idêntico ao oficial (`.vm_res` em
   `0x4b4c`, mesmos offsets de recurso, tags completas), só muda o código (nosso).
5. Após as tentativas frustradas, **os dois mostradores custom (Longines e o nosso)
   passaram a renderizar preto** — estado da face corrompido; reiniciar o relógio.

## 6. Conclusões atuais sobre modo mostrador

- O firmware desenha o fundo (`clock_bg`) direto do `.vm_res`; a ausência dos
  ponteiros no Longines sugere que **o código do VXP não roda nesse modo** (ou roda
  em sandbox sem acesso ao layer). Precisamos provar com sinal não-visual:
  `vm_vibrator_once()` no `vm_main` + timer de 3 s (já está no `src/test.c`).
- Se o código não rodar em modo face, o caminho para "meu mostrador" é empacotar
  imagens próprias no `.vm_res` (fundo/ponteiros) no formato VRE3, como os oficiais.

## 7. Estado atual (18/11/2026, ~04h)

- `reflow.py`/`layout.py` corrigidos (preservam tags). `LuizFaceRes.vxp` (57.315 B)
  rebuildado e válido: `.vm_res` em `0x4b4c`, offsets idênticos ao oficial, tags
  completas (nome `LuizFace`, `0x34=1`, 176x220). Código = `src/test.c` (vibração
  + tela vermelha) — de propósito, para o experimento decisivo abaixo.
- `app/assets/face.vxp` estava quebrado (sem tags, `.vm_res` desalinhado em
  `0x23a4`); substituído pelo mostrador mínimo válido (`MyMREApp.vxp`).
- APK rebuildado e instalado no celular (`adb install -r`), assets: `face.vxp`,
  `luizface.vxp`, `luizapp.vxp`, `luizface3.vxp` (= LuizFaceRes), `ref.vxp`.
- Longines oficial (`refface.vxp`) instala e mostra **fundo, sem ponteiros**.

## 8. Experimento decisivo (próximo teste no relógio)

`LuizFaceRes` tem os recursos oficiais (fundo Longines) + **nosso código** que
vibra no `vm_main` e a cada 3 s, e pinta a tela de vermelho. Instalar e selecionar:

1. Reiniciar o relógio (segurar botão) para limpar o estado corrompido.
2. No relógio, voltar para um mostrador **nativo** (nenhum custom ativo).
3. Bancada: Conectar SPP → "Limpar testes MRE" → "Instalar LuizFace RES".
4. Selecionar o LuizFace no relógio e observar:
   - **Vibrou / tela vermelha** → nosso código RODA em modo face. Aí o problema
     dos ponteiros no Longines é renderização/recurso, não execução. Caminho:
     arte própria no `.vm_res` + código de ponteiros nosso.
   - **Só o fundo Longines, sem vibração** → firmware desenha o fundo do
     `.vm_res` mas NÃO executa o código em modo face. Caminho: empacotar tudo
     (fundo + ponteiros) como imagens VRE3 e depender do render nativo.
   - **Preto** → nem fundo nem código; investigar loader/permissoes.

## 9. Capacidades do relógio (mapa GATT confirmado no dumpsys)

Serviços que o W26 expõe: `1800/1801` (GAP/GATT), `1802` **Immediate Alert**
(`2A06` = find me / alerta), `1803` (link loss), `1804` (tx power), `18A0` (MTK
3.0: `2AA0` notify, `2AA1` write), `18E0` (TJD: `2E00/2E01`).

- **NÃO tem `30A0`** (serviço de dial push do app oficial M Active) → o controle
  de mostrador nativo via `DialInfoSet`/`DialSelect` NÃO existe neste firmware.
  Mostradores aqui são apenas VXP via `mre_installer`.
- **NÃO tem GPS** nem serviço de localização → "finder" usa o GPS do **celular**:
  o app grava a última posição/conexão e mostra num mapa; ao perder o relógio,
  dispara o alerta (`1802/2A06` ou TJD `X1,PRSET,FindDevice,1`) para vibrar.
- App oficial decompilado (`tmp/jadx-out` na cópia antiga): `VxpInstallController`
  tem cmd `-3` (push only), `-4` (uninstall all), `8` (set permission); a tag
  `0x35` no VXP declara a máscara de permissões exigida (lida por
  `MREProperties.getPermission`). Nosso `build.py` ainda não emite `0x35`.

## 10. Próximos passos

1. Rodar o experimento decisivo (§8) e registrar o resultado.
2. Se código roda: restaurar o mostrador analógico (`/tmp/luizface_backup.c`),
   depois arte própria no `.vm_res` (PACKRES/ResEditor em `tmp/mresdk/tools`).
3. Emitir tag `0x35` + instalar com máscara de permissões (cmd 8) como o oficial.
4. Roadmap maior: apps MRE próprios, sync celular↔relógio sobre SPP, finder
   (última localização do celular + alerta no relógio), UI própria e canal de
   atualização de VXP pelo nosso app.

## 11. Estrutura

```
app/                app Android da bancada (W26.java, Mtk.java, MainActivity.java)
mre/hello/          mostrador/app MRE (C + build.sh + sdk/)
  src/test.c        teste de vibração + tela vermelha (diagnóstico)
  sdk/              build.py (tags), fixelf.py, reflow.py, layout.py, scat*.ld
  sdk/resource.bin       recursos mínimos de teste
  sdk/resource_long.bin  .vm_res oficial do Longines (36.836 B)
*.py                scripts de captura/protocolo BT usados na engenharia reversa
```
