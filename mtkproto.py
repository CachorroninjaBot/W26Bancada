"""Protocolo MTK 3.0 do relogio (usado pelo M Activie para notificacoes).

Frames: F0 F0 F0 F1 | len (4 bytes BE) | RC4(plaintext + '\\0')
RC4 com chave fixa de 130 bytes (libCommand.so, getTheDataBuffer).
Formato do plaintext: "<TAG> <texto>", TAG em {DATA,SYNC,MAPX,MAPD,CAPC,MREE,EXCD}.
Notificacao = frame("DATA <n>") seguido de n bytes de XML em claro.
"""
import time
import xml.sax.saxutils as sx

KEY = (
    b"we had everything before us, we had nothing before us, "
    b"we were all going direct to Heaven, we were all going direct the other way\x00"
)
SYNC = b"\xf0\xf0\xf0\xf1"
TAGS = {
    1: b"DATA", 2: b"SYNC", 5: b"MAPX", 6: b"MAPD",
    7: b"CAPC", 8: b"MREE", 9: b"EXCD",
}


def rc4(data: bytes, key: bytes = KEY) -> bytes:
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) & 0xFF
        s[i], s[j] = s[j], s[i]
    out = bytearray()
    i = j = 0
    for b in data:
        i = (i + 1) & 0xFF
        j = (j + s[i]) & 0xFF
        s[i], s[j] = s[j], s[i]
        out.append(b ^ s[(s[i] + s[j]) & 0xFF])
    return bytes(out)


def frame(plain: bytes) -> bytes:
    if not plain.endswith(b"\x00"):
        plain += b"\x00"
    return SYNC + len(plain).to_bytes(4, "big") + rc4(plain)


def cmd(tag: str, text: str = "") -> bytes:
    plain = f"{tag} {text}".rstrip().encode("utf-8") if text else tag.encode()
    return frame(plain)


def decode(data: bytes) -> list[tuple[bytes, bytes]]:
    out = []
    i = 0
    while True:
        i = data.find(SYNC, i)
        if i < 0 or len(data) < i + 8:
            return out
        n = int.from_bytes(data[i + 4:i + 8], "big")
        if n > 65536:
            i += 4
            continue
        out.append((data[i:i + 8 + n], rc4(data[i + 8:i + 8 + n])))
        i += 8 + n


def timestamp() -> int:
    return int(time.time())


def tz_offset_ms() -> int:
    return int(-time.timezone * 1000) if not time.daylight else int(-time.altzone * 1000)


def esc(text: str) -> str:
    return sx.escape(text)


def notification(sender: str, title: str, content: str, app_id: int = 5, msg_id: int = 100) -> bytes:
    xml = (
        "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
        "<event_report><header>"
        "<category>notification</category><subType>text</subType>"
        f"<msgId>{msg_id}</msgId><action>add</action>"
        "</header><body>"
        f"<sender>{esc(sender)}</sender><appId>{app_id}</appId>"
        f"<title><![CDATA[{title}]]></title>"
        f"<content><![CDATA[{content}]]></content>"
        f"<timestamp>{timestamp()}</timestamp>"
        "</body></event_report>"
    ).encode("utf-8")
    return cmd("DATA", str(len(xml))) + xml
