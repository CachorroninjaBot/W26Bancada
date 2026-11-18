# Reconstroi o .vm_res de um vxp fonte, podendo substituir recursos por bytes novos.
# Uso: python respack.py <fonte.vxp> <saida.bin> [nome=recurso_arquivo ...]
# Os offsets do diretorio sao absolutos assumindo .vm_res em 0x4b4c (padrao oficial).
import sys, struct
from resdump import vm_res, entries

BASE = 0x4b4c

def main():
    src, out = sys.argv[1], sys.argv[2]
    subs = {}
    for a in sys.argv[3:]:
        name, path = a.split('=', 1)
        subs[name] = open(path, 'rb').read()
    blob = open(src, 'rb').read()
    start, _ = vm_res(blob)
    ents = entries(blob, start)
    items = []
    for name, off, sz in ents:
        data = subs.get(name, blob[off:off + sz])
        items.append((name, data))
    dirsize = sum(len(n) + 1 + 8 for n, _ in items) + 1
    pos = BASE + dirsize
    directory = b''
    payloads = b''
    for name, data in items:
        directory += name.encode('ascii') + b'\0' + struct.pack('<II', pos, len(data))
        payloads += data
        pos += len(data)
    directory += b'\0'
    open(out, 'wb').write(directory + payloads)
    print(f'{out}: {len(items)} recursos, {len(directory) + len(payloads)} B')

if __name__ == '__main__':
    main()