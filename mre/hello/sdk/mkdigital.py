# Gera mostradores DIGITAIS pre-montados a partir do ronman (unico template digital
# provado). O firmware renderiza o digital nativamente a partir dos recursos
# (num_0..9.png + clock_bg + Configtbl.bin); o codigo VXP do ronman e um stub
# draw_hello. Por isso preservamos o Configtbl.bin EXACTAMENTE (estatisticas e
# hotspots nativos) e variamos apenas o fundo padrao (clock_bg.png).
#
# Layout identico ao oficial: ER_RO@0x34, .vm_res@0x4b4c, tags+trailer preservados.
# Uso: python mkdigital.py <ronman.vxp> <saida_dir>
import sys, struct, os
from resdump import vm_res, entries

BASE = 0x4b4c

def u32(b, p): return struct.unpack_from('<I', b, p)[0]
def u16(b, p): return struct.unpack_from('<H', b, p)[0]
def align(x, a=4): return (x + a - 1) & ~(a - 1)

def rebuild(src_bytes, subs):
    d = src_bytes
    # carrega tags+trailer (build.py append depois do ELF)
    tags_blob = b''
    if len(d) >= 12 and d[-8:] == b'\0' * 8:
        tp = u32(d, len(d) - 12)
        if 0 < tp <= len(d) - 12:
            tags_blob = d[tp:len(d) - 12]
    shoff = u32(d, 0x20); es = u16(d, 0x2e); n = u16(d, 0x30); strndx = u16(d, 0x32)
    shdr = [struct.unpack_from('<IIIIIIIIII', d, shoff + i * es) for i in range(n)]
    st = shdr[strndx]; strtab = d[st[4]:st[4] + st[5]]
    names = [strtab[s[0]:strtab.find(b'\0', s[0])].decode() for s in shdr]
    get = lambda nm: shdr[names.index(nm)]
    body = lambda s: d[s[4]:s[4] + s[5]]
    ro, rw, zi = get('ER_RO'), get('ER_RW'), get('ER_ZI')

    start, _ = vm_res(d)
    ents = entries(d, start)
    items = [(nm, subs.get(nm, d[off:off + sz])) for nm, off, sz in ents]
    dirsize = sum(len(nm) + 1 + 8 for nm, _ in items) + 1
    pos = BASE + dirsize
    directory = b''; payloads = b''
    for nm, data in items:
        directory += nm.encode('ascii') + b'\0' + struct.pack('<II', pos, len(data))
        payloads += data; pos += len(data)
    directory += b'\0'
    res = directory + payloads

    ro_off = 0x34
    rw_off = align(ro_off + ro[5])
    res_off = BASE
    shstr_off = res_off + len(res)
    ph_off = align(shstr_off + st[5])
    sh_off = align(ph_off + 0x20)

    def sec(nm, offset, size=None):
        s = list(get(nm)); s[4] = offset
        if size is not None: s[5] = size
        return s

    new_sh = [
        [0] * 10,
        sec('ER_RO', ro_off),
        sec('ER_RW', rw_off),
        sec('ER_ZI', rw_off + rw[5]),
        sec('.vm_res', res_off, len(res)),
        sec('.shstrtab', shstr_off),
    ]
    new_sh[4][2] = 0x2  # SHF_ALLOC

    out = bytearray(sh_off + len(new_sh) * es)
    out[0:0x34] = d[:0x34]
    out[ro_off:ro_off + ro[5]] = body(ro)
    out[rw_off:rw_off + rw[5]] = body(rw)
    out[res_off:res_off + len(res)] = res
    out[shstr_off:shstr_off + st[5]] = body(st)
    filesz = ro[5] + rw[5]; memsz = filesz + zi[5]
    out[ph_off:ph_off + 0x20] = struct.pack('<IIIIIIII', 1, ro_off, 0x8000, 0x8000,
                                            filesz, memsz, 0xB0000007, 4)
    for i, s in enumerate(new_sh):
        out[sh_off + i * es:sh_off + (i + 1) * es] = struct.pack('<IIIIIIIIII', *s)
    struct.pack_into('<I', out, 28, ph_off)
    struct.pack_into('<I', out, 32, sh_off)
    struct.pack_into('<H', out, 44, 1)
    struct.pack_into('<H', out, 48, len(new_sh))
    struct.pack_into('<H', out, 50, 5)
    elf_len = len(out)
    if tags_blob:
        out += tags_blob
        out += struct.pack('<III', elf_len, 0, 0)
    return bytes(out)

def main():
    src, outdir = sys.argv[1], sys.argv[2]
    d = open(src, 'rb').read()
    start, _ = vm_res(d)
    ents = {nm: (off, sz) for nm, off, sz in entries(d, start)}
    bg0_off, bg0_sz = ents['clock_bg0.png']
    bg0 = d[bg0_off:bg0_off + bg0_sz]
    os.makedirs(outdir, exist_ok=True)
    # Fundo A: ronman original (apenas reflowado para o layout oficial 0x4b4c)
    a = rebuild(d, {})
    open(os.path.join(outdir, 'ronman_a.vxp'), 'wb').write(a)
    # Fundo B: fundo padrao (clock_bg.png) recebe a arte alternativa clock_bg0.png
    b = rebuild(d, {'clock_bg.png': bg0})
    open(os.path.join(outdir, 'ronman_b.vxp'), 'wb').write(b)
    print(f'ronman_a.vxp {len(a)} B | ronman_b.vxp {len(b)} B (Configtbl preservado)')

if __name__ == '__main__':
    main()