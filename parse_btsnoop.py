import argparse
import datetime
import struct
import sys
from collections import Counter, defaultdict

WATCH_ADDRESSES = {"c2:ba:45:15:c8:f9", "06:87:e6:0c:62:61"}

HANDLE_NAMES = {
    2: "2A00 nome",
    4: "2A01 appearance",
    7: "2A05 service changed",
    257: "2A06 alert (link loss)",
    260: "2AA0 cmd/indicate",
    262: "2AA1 cmd/write",
    265: "2E00 data/notify",
    267: "2E01 data/write+notify",
    269: "2E10 desc",
    270: "2E11 desc",
    272: "2A07 tx power",
    275: "2A06 alert (immediate)",
    277: "2ABC notify",
}

ATT_NAMES = {
    0x01: "Error Response",
    0x02: "MTU Request",
    0x03: "MTU Response",
    0x04: "Find Info Request",
    0x05: "Find Info Response",
    0x06: "Find By Type Value Request",
    0x07: "Find By Type Value Response",
    0x08: "Read By Type Request",
    0x09: "Read By Type Response",
    0x0A: "Read Request",
    0x0B: "Read Response",
    0x0C: "Read Blob Request",
    0x0D: "Read Blob Response",
    0x0E: "Read Multiple Request",
    0x0F: "Read Multiple Response",
    0x10: "Read By Group Type Request",
    0x11: "Read By Group Type Response",
    0x12: "Write Request",
    0x13: "Write Response",
    0x16: "Prepare Write Request",
    0x17: "Prepare Write Response",
    0x18: "Execute Write Request",
    0x19: "Execute Write Response",
    0x1B: "Notify",
    0x1D: "Indicate",
    0x1E: "Confirm",
    0x52: "Write Command",
    0xD2: "Signed Write Command",
}


def name_handle(handle: int) -> str:
    return HANDLE_NAMES.get(handle, f"handle {handle}")


def btsnoop_records(data: bytes):
    if not data.startswith(b"btsnoop\x00"):
        raise ValueError("Não parece um arquivo btsnoop (magic ausente).")
    version, datalink = struct.unpack_from(">II", data, 8)
    offset = 16
    while offset + 24 <= len(data):
        orig_len, incl_len, flags, drops, secs, usecs = struct.unpack_from(">IIIIII", data, offset)
        offset += 24
        packet = data[offset:offset + incl_len]
        offset += incl_len
        ts = datetime.datetime(1, 1, 1) + datetime.timedelta(seconds=secs, microseconds=usecs)
        yield version, datalink, ts, flags, packet


def parse_le_connections(packet: bytes, connections: dict) -> None:
    if len(packet) < 8 or packet[0] != 0x04 or packet[3] != 0x01:
        return
    params = packet[4:]
    if len(params) < 13:
        return
    handle = struct.unpack_from("<H", params, 1)[0]
    addr = ":".join(f"{b:02x}" for b in params[6:12][::-1])
    connections[handle] = addr


def att_text(pdu: bytes) -> str:
    if not pdu:
        return "(ATT vazio)"
    opcode = pdu[0]
    name = ATT_NAMES.get(opcode, f"opcode {opcode:#04x}")
    if opcode in (0x1B, 0x1D, 0x12) and len(pdu) >= 3:
        handle = struct.unpack_from("<H", pdu, 1)[0]
        return f"{name} {name_handle(handle)} = {pdu[3:].hex(' ')}"
    if opcode == 0x52 and len(pdu) >= 3:
        handle = struct.unpack_from("<H", pdu, 1)[0]
        return f"{name} {name_handle(handle)} = {pdu[3:].hex(' ')}"
    if opcode == 0x0A and len(pdu) >= 3:
        handle = struct.unpack_from("<H", pdu, 1)[0]
        return f"{name} {name_handle(handle)}"
    if opcode == 0x0B:
        return f"{name} = {pdu[1:].hex(' ')}"
    if opcode == 0x02 and len(pdu) >= 3:
        return f"{name} mtu={struct.unpack_from('<H', pdu, 1)[0]}"
    if opcode == 0x03 and len(pdu) >= 3:
        return f"{name} mtu={struct.unpack_from('<H', pdu, 1)[0]}"
    if opcode == 0x01 and len(pdu) >= 5:
        handle = struct.unpack_from("<H", pdu, 1)[0]
        return f"{name} req={pdu[1]:#04x} handle={handle} code={pdu[4]:#04x}"
    return f"{name} {pdu[1:].hex(' ')}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Extrai o tráfego ATT do relógio a partir de um btsnoop_hci.log.")
    parser.add_argument("file")
    parser.add_argument("--all", action="store_true", help="mostra também pacotes de outros dispositivos")
    parser.add_argument("--handle", type=int, help="mostra só este handle")
    parser.add_argument("--summary", action="store_true", help="só o resumo por handle")
    parser.add_argument("--limit", type=int, default=0, help="máximo de linhas a imprimir")
    args = parser.parse_args()

    with open(args.file, "rb") as fh:
        data = fh.read()

    connections: dict[int, str] = {}
    watch_handles: set[int] = set()
    counts = Counter()
    samples = defaultdict(list)
    printed = 0

    for version, datalink, ts, flags, packet in btsnoop_records(data):
        if not packet:
            continue
        h4 = packet[0]
        if h4 == 0x04:
            parse_le_connections(packet, connections)
            for handle, addr in connections.items():
                if addr in WATCH_ADDRESSES:
                    watch_handles.add(handle)
            continue
        if h4 != 0x02 or len(packet) < 9:
            continue
        acl_header = struct.unpack_from("<H", packet, 1)[0]
        handle = acl_header & 0x0FFF
        if not args.all and watch_handles and handle not in watch_handles:
            continue
        l2cap = packet[5:]
        if len(l2cap) < 4:
            continue
        l2cap_len, cid = struct.unpack_from("<HH", l2cap, 0)
        if cid != 0x0004:
            continue
        pdu = l2cap[4:4 + l2cap_len]
        if not pdu:
            continue
        direction = "PC->REL" if (flags & 0x01) == 0 else "REL->PC"
        text = att_text(pdu)
        opcode_handle = None
        if len(pdu) >= 3 and pdu[0] in (0x1B, 0x1D, 0x12, 0x52, 0x0A):
            opcode_handle = struct.unpack_from("<H", pdu, 1)[0]
        if args.handle is not None and opcode_handle != args.handle:
            continue
        if opcode_handle is not None:
            counts[opcode_handle] += 1
            if len(samples[opcode_handle]) < 3:
                samples[opcode_handle].append(text)
        if not args.summary:
            stamp = ts.strftime("%H:%M:%S.%f")[:-3]
            print(f"{stamp} {direction} h={handle:<3} {text}")
            printed += 1
            if args.limit and printed >= args.limit:
                break

    print(f"\n== resumo (datalink={datalink})")
    for handle, count in counts.most_common():
        print(f"  {name_handle(handle):<24} {count:>6} pacote(s)")
        for sample in samples[handle]:
            print(f"      ex: {sample[:110]}")


if __name__ == "__main__":
    sys.exit(main())
