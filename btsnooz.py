"""Decodifica o blob BTSNOOP_LOG_SUMMARY do dumpsys (btsnooz v1/v2) em btsnoop.

Baseado no btsnooz.py oficial do AOSP (system/bt/tools/scripts/btsnooz.py).
"""
import base64
import re
import struct
import sys
import zlib

TYPE_IN_EVT = 0x10
TYPE_IN_ACL = 0x11
TYPE_IN_SCO = 0x12
TYPE_OUT_CMD = 0x20
TYPE_OUT_ACL = 0x21
TYPE_OUT_SCO = 0x22

HCI_TYPE = {
    TYPE_OUT_CMD: b"\x01",
    TYPE_IN_EVT: b"\x04",
    TYPE_IN_ACL: b"\x02",
    TYPE_OUT_ACL: b"\x02",
    TYPE_IN_SCO: b"\x03",
    TYPE_OUT_SCO: b"\x03",
}

BTSNOOP_HEADER = b"btsnoop\x00\x00\x00\x00\x01\x00\x00\x03\xea"


def direction(t):
    return 1 if t in (TYPE_IN_EVT, TYPE_IN_ACL, TYPE_IN_SCO) else 0


def extract_blob(path):
    txt = open(path, "r", errors="replace").read()
    m = re.search(
        r"--- BEGIN:BTSNOOP_LOG_SUMMARY \((\d+) bytes in\) ---(.*?)--- END:BTSNOOP_LOG_SUMMARY ---",
        txt,
        re.S,
    )
    if not m:
        raise SystemExit("bloco BTSNOOP_LOG_SUMMARY nao encontrado")
    b64 = re.sub(r"\s+", "", m.group(2))
    return base64.b64decode(b64), int(m.group(1))


def write_records(out, records, first_ts, base_offset):
    ts = first_ts
    for length, pkt_len, delta, t, payload in records:
        ts += delta
        hci = HCI_TYPE[t]
        out.write(struct.pack(">IIII", pkt_len, length, direction(t), 0))
        out.write(struct.pack(">II", ts >> 32, ts & 0xFFFFFFFF))
        out.write(hci)
        out.write(payload)
    return ts


def decode(snooz, out):
    version, last_ts = struct.unpack_from("=bQ", snooz)
    if version not in (1, 2):
        raise SystemExit(f"versao btsnooz desconhecida: {version}")
    data = zlib.decompress(snooz[9:])
    first_ts = last_ts + 0x00DCDDB30F2F8000
    records = []
    off = 0
    if version == 1:
        while off < len(data):
            length, delta, t = struct.unpack_from("=HIb", data, off)
            off += 7
            payload = data[off:off + length - 1]
            off += length - 1
            records.append((length, length, delta, t, payload))
            first_ts -= delta
        for rec in records:
            first_ts += rec[2]
    else:
        while off < len(data):
            length, pkt_len, delta, t = struct.unpack_from("=HHIb", data, off)
            off += 9
            payload = data[off:off + length - 1]
            off += length - 1
            records.append((length, pkt_len, delta, t, payload))
            first_ts -= delta
        for rec in records:
            first_ts += rec[2]
    out.write(BTSNOOP_HEADER)
    ts = first_ts
    for length, pkt_len, delta, t, payload in records:
        ts += delta
        out.write(struct.pack(">IIII", pkt_len, length, direction(t), 0))
        out.write(struct.pack(">II", ts >> 32, ts & 0xFFFFFFFF))
        out.write(HCI_TYPE[t])
        out.write(payload)
    return version, len(records), ts


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "../tmp/dumpsys_bt.txt"
    dst = sys.argv[2] if len(sys.argv) > 2 else "../tmp/btsnoop_hci.log"
    snooz, reported = extract_blob(src)
    print(f"blob base64-decodificado: {len(snooz)} bytes (dumpsys reportou {reported})")
    with open(dst, "wb") as f:
        version, count, last_ts = decode(snooz, f)
    import datetime
    ts = datetime.datetime(1, 1, 1) + datetime.timedelta(microseconds=last_ts)
    print(f"btsnooz v{version}: {count} pacotes -> {dst}")
    print(f"ultimo pacote em: {ts:%Y-%m-%d %H:%M:%S.%f} UTC")


if __name__ == "__main__":
    main()
