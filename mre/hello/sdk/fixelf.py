# Replica o layout de ELF do vxp oficial (ADS): um unico PT_LOAD
# (p_offset=ER_RO, vaddr 0x8000, flags 0xB0000007) e .vm_res com SHF_ALLOC.
import sys, struct

fn = sys.argv[1]
d = bytearray(open(fn, 'rb').read())

assert d[:4] == b'\x7fELF'
(etype, mach, ver, entry, phoff, shoff, flags, ehsize, phentsize,
 phnum, shentsize, shnum, shstrndx) = struct.unpack_from('<HHIIIIIHHHHHH', d, 16)

def sh(i):
    return struct.unpack_from('<IIIIIIIIII', d, shoff + i * shentsize)

shstr = sh(shstrndx)
strtab = bytes(d[shstr[4]:shstr[4] + shstr[5]])
secs = {}
for i in range(shnum):
    s = sh(i)
    name = strtab[s[0]:strtab.find(b'\0', s[0])].decode()
    secs[name] = s

ro, rw, zi = secs['ER_RO'], secs['ER_RW'], secs['ER_ZI']
filesz = ro[5] + rw[5]
memsz = filesz + zi[5]

# um segmento so, igual ao oficial
if phnum != 1:
    phoff = 0x34
    struct.pack_into('<I', d, 28, phoff)   # e_phoff
    struct.pack_into('<H', d, 44, 1)       # e_phnum
struct.pack_into('<IIIIIIII', d, phoff,
                 1, ro[4], 0x8000, 0x8000, filesz, memsz, 0xB0000007, 4)

# .vm_res carregado pelo loader (flags 0x2 no oficial)
ri = None
for i in range(shnum):
    s = sh(i)
    name = strtab[s[0]:strtab.find(b'\0', s[0])].decode()
    if name == '.vm_res':
        ri = i
if ri is not None:
    struct.pack_into('<I', d, shoff + ri * shentsize + 8, 0x2)

open(fn, 'wb').write(bytes(d))
print('fixelf: phoff=%#x filesz=%#x memsz=%#x' % (phoff, filesz, memsz))
