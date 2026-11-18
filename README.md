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
- `app/src/com/luiz/w26/W26.java` — GATT + SPP + fila de instalação MREE + alerta 1802.
- `app/src/com/luiz/w26/Mtk.java` — montagem de frames MTK.
- `app/src/com/luiz/w26/Finder.java` — ultima posicao GPS do celular + mapa + alerta.
- `app/src/com/luiz/w26/Prefs.java` — preferencias de sync (hora auto, notificacoes, ultima sync).
- `app/src/com/luiz/w26/Weather.java` — clima Open-Meteo (sem chave) -> `X0,WEATHER`.
- `app/src/com/luiz/w26/NotiSvc.java` — listener que encaminha notificacoes (respeita o toggle).
- `app/src/com/luiz/w26/MainActivity.java` — UI estilo Da Fit: dashboard + barra de 5 abas (Inicio, Dispositivo, Mostradores, Ajustes, Console).
- `app/src/com/luiz/w26/Icon.java` — icones de linha desenhados em Canvas (tingiveis por estado), sem recursos XML.
- `app/src/com/luiz/w26/Ring.java` — anel de progresso da bateria do relogio (trilho + arco).
- `app/src/com/luiz/w26/FaceBuilder.java` — criador de mostrador DIGITAL (fundos pre-montados A/B do ronman, Configtbl oficial preservado).
- `app/assets/*.vxp` — mostradores embarcados (face, luizface, luizapp, luizface3=RES,
  ref=Longines). `app/assets/faces/` — template digital `ronman.vxp` + variantes
  pre-montadas `ronman_a.vxp`/`ronman_b.vxp` (fundos A/B, geradas por `sdk/mkdigital.py`).

Features (18/11): **interface redesenhada estilo Da Fit** — dashboard com hero card
(gradient azul + anel de bateria do relogio), grade de acoes rapidas (sincronizar,
achar relogio/celular, clima), tiles de estatisticas e barra inferior de 5 abas
(Inicio/Dispositivo/Mostradores/Ajustes/Console); toda a UI e programatica (Canvas
`Icon`/`Ring`, sem R/XML). Cards escuros com header de estado global; **finder** (GPS do celular ao conectar, mapa `geo:`, "Fazer vibrar" =
`1802/2A06` + TJD FindDevice); **sync automatico** (hora ao conectar, bateria do
relogio parseada de `X0,GET,7,<n>#` com remontagem de frames `#`, notificacoes com
toggle + status do listener, refresh a cada 30 s); **encontrar celular** (watch manda
`X1,PRSEND,FindApp,1/0` -> app toca/vibra 11 s e ACK `X1,PRRET,FindApp`); **musica**
(watch->phone via MTK `msctrl`, opcode no indice 4 -> teclas de media); **clima**
(Open-Meteo -> `X0,WEATHER;cidade;dias`); MRE cmd `8`/`-4`/`-3`; `build.py` emite tag
`0x35` se `permission_mask` no manifest.

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

## 6. Conclusões sobre modo mostrador e o toolchain

- **Causa raiz da tela preta (confirmada):** o W26 roda **MRE 2.0** (a tabela de
  recursos se chama `mre-2.0`; o SDK tem `MRE2.0.dll`). O toolchain GCC
  (mre-makefile) **só executa em MRE 3.x** — o README dele diz explicitamente
  "Running a compiled VXP on MRE <3.0 phones: doesn't work". Por isso o código
  oficial ADS roda (desenha o fundo) e o nosso GCC ficava preto sem nem vibrar.
- **Convenção de import ADS/MRE2 (engenharia reversa, 18/11):** os vxp ADS chamam
  as APIs por *veneer* que lê um **slot de ponteiro nomeado** em RW/ZI
  (`_vm_res_get_image`, `_vm_graphic_setcolor`, `_vm_graphic_fill_rect_ex`,
  `_vm_reg_sysevt_callback`, …). Se o slot está nulo, o veneer cai no resolvedor
  `vm_get_sym_entry` que o **loader deixa em endereço fixo 0x0c** (não em `r0`).
  O loader resolve **por nome de símbolo** (os offsets variam por app). Os wrappers
  GCC `per*.a` JÁ definem esses mesmos slots `_vm_*` + fallback `vm_get_sym_entry`.
- **Bug do nosso entry (corrigido):** `gcc_entry` confiava em `r1/r2` como
  `init_array` (convenção MRE3). No MRE2 `r1/r2` são lixo → chamava ponteiro lixo
  ANTES do `vm_main` (por isso nem vibrava). Fix: ignorar `r1/r2` e usar
  `__init_array_start/end` nossos; resolvedor na ordem slot-nomeado → 0x0c → `r0`;
  cabeçalho `.mre2_head` reservado em RAM 0x00–0x0c (`scat_exec.ld`).
  Teste: `LuizFaceMRE2.vxp` (asset `luizface5.vxp`, botão "LuizFace MRE2 (teste
  CODIGO)") = recursos oficiais + código vermelho/vibração. Se vibrar/pintar →
  código NOSSO roda no MRE2 → destrava apps novos e ponteiros por código.
- **Caminho para mostrador NOSSO sem depender de código:** trocar as imagens do
  `.vm_res` (fundo/ponteiros/preview) no formato VRE3 usando o oficial como
  template — o firmware renderiza do recurso. Ferramentas: `PACKRES.exe`,
  `ResEditor`, `LogoMake.exe`, `ElfConvert.exe` em `tmp/mresdk/tools` (cópia antiga).
- **Caminho para código próprio:** compilador ADS (proprietário) ou adaptar o
  entry/convenção de símbolos ao MRE 2.0 (engenharia reversa do loader).
- O experimento `LuizFaceRes` (recursos oficiais + código GCC) mostra **o fundo
  Longines, sem ponteiros e sem vibração** → o firmware desenha o fundo do
  `.vm_res` nativamente e NÃO executa o código em modo face (consistente com
  GCC↔MRE2.0). O `ref.vxp` "oficial" é na verdade um repack de terceiro: o código
  ADS dele é um stub `draw_hello` que só desenha texto/retângulo (sem
  `vm_res_get_image`/`vm_graphic_blt`/rotate) — também não desenha ponteiros.
- **Ponteiros + config ESTÃO no `.vm_res`** (@`0x4b4c`): `clock_bg.png`, `hand.png`,
  `minute.png`, `second.png` (todos **240x240**), `preview.png`, e `configtbl.bin`
  (16 B, pivô bytes[2,3] = `0x77,0x77` = (119,119), centro de um dial 240x240).
  `configtbl` não aparece no SDK MRE → é convenção do render nativo do firmware.
- **Causa provável dos ponteiros sumidos:** mismatch de resolução. A tag `0x10`
  declara 176x220 (tela real), mas as imagens de ponteiro são 240x240 com pivô
  (119,119); a camada de rotação estoura o limite de tela/layer e falha. O fundo
  (blit simples) aparece; os ponteiros (rotação) não.
- **Próximo experimento (resource-only):** redimensionar `clock_bg/hand/minute/second`
  para ≤176x220 (ex. 176x176), `configtbl.bin` pivô (88,88) = `00 00 58 58 …0a`,
  manter os NOMES dos recursos, tag `0x10`=176x220, `0x34`=1, copiar o mapa de IDs
  tag-`0x13` do ref. Instalar via MREE e selecionar. Se os ponteiros aparecerem →
  confirma render nativo + mismatch de tamanho.
- **Teste barato já montado (`LuizFace240.vxp` = asset `luizface4.vxp`, botão
  "LuizFace 240 (teste ponteiros)"):** mesmos recursos oficiais 240x240 + configtbl
  (119,119), mas tag `0x10` declarada **240x240** (em vez de 176x220). Se o firmware
  aloca a camada de rotação pelo tamanho da tag `0x10`, declarar 240x240 casa com as
  imagens e os ponteiros podem aparecer (escalados/recortados na tela). Build:
  `MRE_RESOLUTION=240x240 MRE_NAME=LuizFace240 MRE_RES=sdk/resource_long.bin …`
  (overrides `MRE_NAME`/`MRE_RESOLUTION` adicionados ao `build.py`).

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
  `MREProperties.getPermission`). Nosso `build.py` já emite `0x35` quando
  `permission_mask` está no manifest.
- Vocabulário TJD/MTK do app oficial (jadx-out) que o nosso app já usa: bateria
  `X0,GET,7` → `X0,GET,7,<n>#`; find-phone `X1,PRSEND,FindApp,1/0` (ACK
  `X1,PRRET,FindApp`); find-device `X1,PRSET,FindDevice,<0|1>`; musica MTK
  `msctrl` opcode idx4 (3=play 4=pause 5=vol+ 6=vol- 7=prev 8=next); clima
  `X0,WEATHER;cidade;week,yyyy-MM-dd,min,max,icon,desc|…`; hora
  `X1,PRSET,DATETIME`. **Não existe sync de contatos/phonebook** no app oficial.

## 10. Próximos passos

1. **Ponteiros:** rodar o experimento resource-only (§6): recursos redimensionados
   ≤176x220 + `configtbl` pivô (88,88), nomes preservados, IDs tag-`0x13` do ref.
2. Arte própria NOSSA no `.vm_res` (PACKRES/ResEditor em `tmp/mresdk/tools`) uma
   vez confirmado o render nativo; preview/icone próprios.
3. Sync: metadata de musica phone→watch (`mtk_msctrl` push `D 0 00 31 …`), clima
   com nome de cidade real (reverse-geocode), chamadas/SMS com XML `call`/`sms`.
4. Roadmap maior: apps MRE próprios (ADS ou RE do loader MRE2.0), canal de
   atualização de VXP pelo nosso app, interface 100% nossa.

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

## 12. Apps do relógio: criar / apagar / mexer (resposta direta)

- **Apps VXP (instalados por nós / mostradores): CRUD completo já funciona** pelo
  canal MREE-over-SPP: `0` instalar, `5` listar, `-1` uninstall, `-2` status,
  `-4` uninstall all, `-3` push only, `8` set permission. O app da bancada tem
  botões pra tudo isso (Instalar VXP…, picker, Listar, Status, Permissões, Apagar,
  Excluir, Desinstalar TODOS, Limpar testes). Atualizar = reinstalar com mesmo nome
  e versão maior. **Criar app novo = gerar um .vxp nosso e instalar.**
- **Apps nativos do firmware (os que já vêm no relógio): NÃO são apagáveis/editáveis
  pelo MREE.** O instalador responde `-101` (embutido/não removível) pra eles; vivem
  na flash do firmware, não na área de apps VXP. Mexer neles = modificar firmware
  (risco de brick), fora do canal seguro que usamos.
- **O que bloqueia app novo "de verdade" (com lógica, não só imagem):** executar
  código nosso no MRE 2.0 (§6). Recursos/imagens já renderizam nativo; lógica
  (ponteiros, telas interativas, sensores) precisa do entry/import corrigido acima.
  O teste `LuizFace MRE2` decide se destravamos isso.
- **Interface "NOSSA" do relógio:** sem firmware mod, o caminho é mostrador/app VXP
  nosso em tela cheia (auto-start tag `0x32`) + nosso app Android como companion.
  Reconstruir o launcher nativo inteiro exigiria firmware mod (não seguro hoje).

## 13. Mostrador DIGITAL — conclusões (18/11/2026)

Foco atual: **só digital** (ponteiros abandonados; PNG personalizado abandonado).
O criador da bancada (`FaceBuilder`) monta mostradores digitais pré-montados.

1. **O firmware renderiza o digital NATIVAMENTE, não o código VXP.** O `ronman.vxp`
   (único template digital) tem `.symtab`/`.strtab`: o código ADS é um stub
   `draw_hello` (fonte `c:\Users\kuasa\Desktop\Ironman2\Ironman2.c`, 180 B), igual
   ao `ref.vxp`. Ele importa `vm_reg_pen_callback`/`vm_graphic_*` mas não desenha
   o relógio. Quem desenha horas/numerais/estatísticas é o **render nativo do
   firmware** lendo os recursos: `num_0..9.png` (glifos de dígito), `clock_bg.png`,
   `clock_bg0.png`, `clock_bg1.png` e `Configtbl.bin`.
2. **Estatísticas (passos/HR/data) e atalhos de toque (música/HR) são nativos** e
   dependem do `Configtbl.bin` oficial + conjunto de recursos. Por isso o
   `FaceBuilder.build()` **preserva o Configtbl exatamente** (patch in-place de
   16 B, sem reflow): `00 00 29 69 87 69 a5 82 00 00 01 60 80 00 03 00`
   (byte[14]=`0x03` = digital; analogos usam byte[15]=`0x0a`, pivô bytes[2,3]).
   Zerar bytes "no chute" quebrava o mostrador — não fazer.
3. **O caminho oficial de "dial push" NÃO existe no W26.** O protocolo
   `DialInfoSet`/`WallpaperPush` (que configuraria slots de estatística
   timeTop/timeBottom = data/sono/HR/passos, posição da hora, cor e fundo PNG)
   usa o serviço **GATT `0x30A0`** (write `0x3A01`, notify `0x3A00`). Confirmado
   pelo cache GATT do relógio em `dumpsys bluetooth_manager` (bloco
   `gatt_hash_…`, device `…c8:f9`): os serviços são **1800, 1801, 1803, 18a0
   (MTK 2aa0/2aa1), 18e0 (TJD 2e00/2e01), 1804, 1802 (2a06 + 2abc)** — **sem
   30A0**. Logo não dá para empurrar config de estatística dinamicamente; os
   slots nativos do Configtbl são o que temos.
4. **Fundos A/B pré-montados:** o ronman já traz duas artes VRE3 distintas
   (`clock_bg.png`=`clock_bg1.png`, 32024 B; `clock_bg0.png`, 32564 B) que o
   relógio alterna pelo toque. `sdk/mkdigital.py` gera `ronman_a.vxp` (fundo
   original) e `ronman_b.vxp` (`clock_bg.png` ← arte do `clock_bg0.png`),
   preservando código ADS (ER_RO 4112 B @0x34, entry 0x8000), tags (nome
   "Ironman Interface", `0x34=1`, 176x220) e Configtbl; `.vm_res` reflowado para
   `0x4b4c` (layout oficial). Todas as imagens são **VRE3** (`34 01 <len16>`),
   formato fechado — sem edição de PNG.
5. **Instalação:** o relógio tem pouco espaço para mostradores; `installFace()`
   desinstala os presets (`lz_ronman_a/b`) antes de instalar (senão MREE 19 =
   -1000 "sem espaço"). Códigos MREE: 15=instalado, 16=(-4 ok), 19=-1000 recusado,
   -101=embutido.
6. **Pendente de teste no relógio:** selecionar o digital instalado e confirmar
   horas + estatísticas + atalhos de toque e a troca de fundo A/B. (Celular
   estava bloqueado na sessão; APK instalado via `adb install -r`.)
