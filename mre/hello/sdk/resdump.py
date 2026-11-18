# Inspeciona o .vm_res de um .vxp: lista recursos, magic/dims e o configtbl.
import sys, struct

def u32(b, p): return struct.unpack_from('<I', b, p)[0]
def u16(b, p): return struct.unpack_from('<H', b, p)[0]

def vm_res(blob):
    shoff = u32(blob, 0x20); shentsize = u16(blob, 0x2e)
    shnum = u16(blob, 0x30); shstrndx = u16(blob, 0x32)
    strtab_off = u32(blob, shoff + shstrndx * shentsize + 0x10)
    for i in range(shnum):
        base = shoff + i * shentsize
        name_off = u32(blob, base)
        end = blob.index(b'\0', strtab_off + name_off)
        name = blob[strtab_off + name_off:end].decode('ascii', 'replace')
        if name == '.vm_res':
            return u32(blob, base + 0x10), u32(blob, base + 0x14)
    raise RuntimeError('.vm_res nao encontrado')

def entries(blob, start):
    pos = start; out = []
    while blob[pos] != 0:
        e = blob.index(b'\0', pos)
        name = blob[pos:e].decode('ascii', 'replace')
        off = u32(blob, e + 1); size = u32(blob, e + 5)
        out.append((name, off, size)); pos = e + 9
    return out

def main(path):
    blob = open(path, 'rb').read()
    start, size = vm_res(blob)
    print(f'== {path}  .vm_res @0x{start:x} ({size} B)')
    for name, off, sz in entries(blob, start):
        head = blob[off:off + 16]
        tag = ''
        if head[:8] == b'\x89PNG\r\n\x1a\n':
            w = struct.unpack_from('>I', blob, off + 16)[0]
            h = struct.unpack_from('>I', blob, off + 20)[0]
            tag = f'PNG {w}x{h}'
        elif head[:2] == b'\x34\x01':
            tag = f'VRE3 len16=0x{u16(head,2):x}'
        elif head[:3] == b'\xff\xd8\xff':
            tag = 'JPEG'
        print(f'  {name:28s} off=0x{off:05x} size={sz:6d}  {head.hex(" ")}  {tag}')
        if 'configtbl' in name.lower():
            print('      configtbl hex:', blob[off:off + sz].hex(' '))

if __name__ == '__main__':
    for p in sys.argv[1:]:
        main(p)