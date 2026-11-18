"""Cliente SPP classico (RFCOMM) do relogio via Winsock/cTypes."""
import ctypes
import socket
import struct
import sys
import uuid
from ctypes import wintypes

AF_BTH = 32
BTHPROTO_RFCOMM = 3
SOL_SOCKET = 0xFFFF
SO_RCVTIMEO = 0x1006
SOCKET_ERROR = -1

SPP = uuid.UUID("00001101-0000-1000-8000-00805f9b34fb")


class WSAData(ctypes.Structure):
    _fields_ = [
        ("wVersion", wintypes.WORD),
        ("wHighVersion", wintypes.WORD),
        ("szDescription", ctypes.c_char * 257),
        ("szSystemStatus", ctypes.c_char * 129),
        ("iMaxSockets", wintypes.WORD),
        ("iMaxUdpDg", wintypes.WORD),
        ("lpVendorInfo", ctypes.c_char_p),
    ]


ws2 = ctypes.WinDLL("ws2_32", use_last_error=True)
ws2.socket.restype = ctypes.c_size_t
ws2.socket.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_int]
ws2.connect.argtypes = [ctypes.c_size_t, ctypes.c_char_p, ctypes.c_int]
ws2.connect.restype = ctypes.c_int
ws2.send.argtypes = [ctypes.c_size_t, ctypes.c_char_p, ctypes.c_int, ctypes.c_int]
ws2.send.restype = ctypes.c_int
ws2.recv.argtypes = [ctypes.c_size_t, ctypes.c_char_p, ctypes.c_int, ctypes.c_int]
ws2.recv.restype = ctypes.c_int
ws2.closesocket.argtypes = [ctypes.c_size_t]
ws2.setsockopt.argtypes = [ctypes.c_size_t, ctypes.c_int, ctypes.c_int, ctypes.c_char_p, ctypes.c_int]


def bth_addr(text: str) -> int:
    return int(text.replace(":", ""), 16)


def connect_bt(address: str, timeout_ms: int = 8000) -> int:
    data = WSAData()
    if ws2.WSAStartup(0x202, ctypes.byref(data)) != 0:
        raise OSError("WSAStartup falhou")
    s = ws2.socket(AF_BTH, socket.SOCK_STREAM, BTHPROTO_RFCOMM)
    if s == ctypes.c_size_t(-1).value:
        raise OSError(ctypes.get_last_error(), "socket AF_BTH falhou")
    ws2.setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, struct.pack("I", timeout_ms), 4)
    addr = struct.pack("<H6xQ16sI", AF_BTH, bth_addr(address), SPP.bytes_le, 0) + b"\x00" * 4
    if ws2.connect(s, addr, len(addr)) != 0:
        err = ctypes.get_last_error()
        ws2.closesocket(s)
        raise OSError(err, f"connect falhou (WSA {err})")
    return s


def send_all(s: int, data: bytes) -> None:
    sent = 0
    while sent < len(data):
        n = ws2.send(s, data[sent:], len(data) - sent, 0)
        if n <= 0:
            raise OSError(ctypes.get_last_error(), "send falhou")
        sent += n


def recv_some(s: int, size: int = 4096) -> bytes:
    buf = ctypes.create_string_buffer(size)
    n = ws2.recv(s, buf, size, 0)
    if n > 0:
        return buf.raw[:n]
    if n == 0:
        return b""
    err = ctypes.get_last_error()
    if err in (10060,):  # WSAETIMEDOUT
        return b""
    raise OSError(err, f"recv falhou (WSA {err})")


def frame(cmd: str) -> bytes:
    return f"$LHBT TJD_APP TJD_Dev 0 {len(cmd)} {cmd}#".encode()


def main() -> None:
    addr = sys.argv[1] if len(sys.argv) > 1 else "06:87:E6:0C:62:61"
    cmds = sys.argv[2:] or ["X1,LINK"]
    s = connect_bt(addr)
    print(f"conectado SPP em {addr}")
    try:
        for cmd in cmds:
            data = frame(cmd)
            print(f">> {data!r}")
            send_all(s, data)
            replies = []
            while True:
                chunk = recv_some(s)
                if not chunk:
                    break
                replies.append(chunk)
                if b"#" in chunk or len(chunk) >= 4096:
                    break
            print(f"   << {b''.join(replies)!r}")
    finally:
        ws2.closesocket(s)


if __name__ == "__main__":
    main()
