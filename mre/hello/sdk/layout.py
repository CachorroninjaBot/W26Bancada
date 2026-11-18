# Move o .vm_res para 0x4b4c (posicao dos vxp oficiais) inserindo padding
# e deslocando as secoes seguintes + section header table.
import sys, struct

fn, target = sys.argv[1], int(sys.argv[2], 0)
d = bytearray(open(fn, 'rb').read())

etype, mach, ver, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx = \
    struct.unpack_from('<HHIIIIIHHHHHH', d, 16)

shdr = [struct.unpack_from('<IIIIIIIIII', d, shoff + i * shentsize) for i in range(shnum)]
st = shdr[shstrndx]
strtab = bytes(d[st[4]:st[4] + st[5]])

ri = None
for i, s in enumerate(shdr):
    name = strtab[s[0]:strtab.find(b'\0', s[0])].decode()
    if name == '.vm_res':
        ri = i
assert ri is not None

cur = shdr[ri][4]
delta = target - cur
if delta <= 0:
    print('layout: ja esta em %#x (delta %d)' % (cur, delta))
    sys.exit(0)

for i, s in enumerate(shdr):
    if s[4] >= cur and s[5] > 0:
        struct.pack_into('<I', d, shoff + i * shentsize + 16, s[4] + delta)
struct.pack_into('<I', d, 32, shoff + delta)

d[cur:cur] = b'\0' * delta

# tags + trailer shifted by delta along with everything after .vm_res
if len(d) >= 12 and d[-8:] == b'\0' * 8:
    tp = struct.unpack_from('<I', d, len(d) - 12)[0]
    if 0 < tp <= len(d) - 12:
        struct.pack_into('<I', d, len(d) - 12, tp + delta)

open(fn, 'wb').write(bytes(d))
print('layout: .vm_res %#x -> %#x (padding %d)' % (cur, target, delta))
