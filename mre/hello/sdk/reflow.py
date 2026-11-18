# Reflow no estilo ADS/oficial: ehdr, ER_RO em 0x34, ER_RW, .vm_res em 0x4b4c,
# .shstrtab, phdr unico e section headers. Sem symtab/strtab (nao usados pelo VM).
import sys, struct

fn, target = sys.argv[1], int(sys.argv[2], 0)
d = bytes(open(fn, 'rb').read())

ident, etype, mach, ver, entry, phoff, shoff, eflags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx = \
    struct.unpack_from('<16sHHIIIIIHHHHHH', d, 0)

shdr = [struct.unpack_from('<IIIIIIIIII', d, shoff + i * shentsize) for i in range(shnum)]
st = shdr[shstrndx]
strtab = d[st[4]:st[4] + st[5]]
names = [strtab[s[0]:strtab.find(b'\0', s[0])].decode() for s in shdr]
get = lambda n: shdr[names.index(n)]

def body(s):
    return d[s[4]:s[4] + s[5]]

def align(x, a=4):
    return (x + a - 1) & ~(a - 1)

ro, rw, zi, res = get('ER_RO'), get('ER_RW'), get('ER_ZI'), get('.vm_res')

ro_off = 0x34
rw_off = align(ro_off + ro[5])
res_off = target
shstr_off = res_off + res[5]
ph_off = align(shstr_off + st[5])
sh_off = align(ph_off + 0x20)

# nova tabela: NULL, ER_RO, ER_RW, ER_ZI, .vm_res, .shstrtab
def sec(name, offset):
    s = list(get(name))
    s[4] = offset
    return s

new_sh = [
    [0] * 10,
    sec('ER_RO', ro_off),
    sec('ER_RW', rw_off),
    sec('ER_ZI', rw_off + rw[5]),
    sec('.vm_res', res_off),
    sec('.shstrtab', shstr_off),
]
new_sh[4][2] = 0x2

out = bytearray(sh_off + len(new_sh) * shentsize)
out[0:0x34] = d[:0x34]
out[ro_off:ro_off + ro[5]] = body(ro)
out[rw_off:rw_off + rw[5]] = body(rw)
out[res_off:res_off + res[5]] = body(res)
out[shstr_off:shstr_off + st[5]] = body(st)

filesz = ro[5] + rw[5]
memsz = filesz + zi[5]
out[ph_off:ph_off + 0x20] = struct.pack('<IIIIIIII', 1, ro_off, 0x8000, 0x8000,
                                        filesz, memsz, 0xB0000007, 4)

for i, s in enumerate(new_sh):
    out[sh_off + i * shentsize:sh_off + (i + 1) * shentsize] = struct.pack('<IIIIIIIIII', *s)

struct.pack_into('<I', out, 28, ph_off)   # e_phoff
struct.pack_into('<I', out, 32, sh_off)   # e_shoff
struct.pack_into('<H', out, 44, 1)        # e_phnum
struct.pack_into('<H', out, 48, len(new_sh))
struct.pack_into('<H', out, 50, 5)        # e_shstrndx = .shstrtab

open(fn, 'wb').write(bytes(out))
print('reflow: RO %#x RW %#x RES %#x shstr %#x ph %#x sh %#x len %#x' %
      (ro_off, rw_off, res_off, shstr_off, ph_off, sh_off, len(out)))
