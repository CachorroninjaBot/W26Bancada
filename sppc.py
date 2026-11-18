"""Cliente SPP com reconexao tolerante a fechamento, para sondar o canal classico."""
import asyncio
import sys

from winrt.windows.devices.bluetooth import BluetoothDevice
from winrt.windows.networking.sockets import StreamSocket
from winrt.windows.storage.streams import DataReader, InputStreamOptions

SPP = "00001101"


def crc8(data: bytes) -> int:
    crc = 0
    for byte in data:
        b = byte
        for _ in range(8):
            if (b ^ crc) & 1:
                crc = ((crc ^ 0x18) >> 1) | 0x80
            else:
                crc >>= 1
            b >>= 1
    return crc & 0xFF


def brlt(head: str, cmd: str, sub: str, data_hex: str) -> bytes:
    size = len(data_hex) // 2 + (4 if sub else 3) + 1
    body = head + f"{size:02X}" + cmd + sub + data_hex
    raw = bytes.fromhex(body)
    return (body + f"{crc8(raw):02X}").encode()


def ascii_frame(cmd: str) -> bytes:
    return f"$LHBT TJD_APP TJD_Dev 0 {len(cmd)} {cmd}#".encode()


async def session(label: str, payloads: list[bytes], seconds: float) -> None:
    dev = await BluetoothDevice.from_bluetooth_address_async(0x0687E60C6261)
    res = await dev.get_rfcomm_services_async()
    svc = next((s for s in res.services if SPP in str(s.service_id.uuid).lower()), None)
    if svc is None:
        print(f"{label}: sem SPP")
        return
    sock = StreamSocket()
    await sock.connect_async(svc.connection_host_name, svc.connection_service_name)
    in_stream = sock.input_stream
    out_stream = sock.output_stream
    reader = DataReader(in_stream)
    reader.input_stream_options = InputStreamOptions.PARTIAL
    print(f"[{label}] conectado; enviando {len(payloads)} pacote(s)")
    for p in payloads:
        print(f"   TX {p!r}")
        out_stream.write_async(p)
        await asyncio.sleep(0.4)
    deadline = asyncio.get_running_loop().time() + seconds
    try:
        while asyncio.get_running_loop().time() < deadline:
            left = deadline - asyncio.get_running_loop().time()
            try:
                n = await asyncio.wait_for(reader.load_async(512), min(2.0, left))
            except asyncio.TimeoutError:
                continue
            if n == 0:
                print(f"[{label}] relogio fechou a conexao")
                break
            print(f"[{label}] RX {reader.read_bytes(n)!r}")
    except Exception as exc:  # noqa: BLE001
        print(f"[{label}] erro de leitura: {type(exc).__name__}: {exc}")
    finally:
        try:
            sock.close()
        except Exception:  # noqa: BLE001
            pass


async def main() -> None:
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "listen"):
        await session("listen", [], 6)
    if which in ("all", "ascii"):
        await session("ascii", [ascii_frame("X1,LINK")], 6)
    if which in ("all", "brlt"):
        await session("brlt", [brlt("AB", "00", "", "")], 6)


asyncio.run(main())
