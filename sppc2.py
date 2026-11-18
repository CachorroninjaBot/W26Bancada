"""Sessao SPP com leitura crua (InputStream.read_async) para sondar o protocolo clasico."""
import asyncio
import sys

from winrt.windows.devices.bluetooth import BluetoothDevice
from winrt.windows.networking.sockets import StreamSocket
from winrt.windows.storage.streams import Buffer, InputStreamOptions


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
    return (body + f"{crc8(bytes.fromhex(body)):02X}").encode()


async def pump(ins, seconds: float, label: str, sock) -> None:
    end = asyncio.get_running_loop().time() + seconds
    while asyncio.get_running_loop().time() < end:
        buf = Buffer(1024)
        try:
            await asyncio.wait_for(ins.read_async(buf, 1024, InputStreamOptions.PARTIAL), 1.5)
        except asyncio.TimeoutError:
            continue
        except Exception as exc:  # noqa: BLE001
            print(f"[{label}] leitura encerrada: {type(exc).__name__}: {exc}")
            return
        print(f"[{label}] RX {bytes(memoryview(buf))[:buf.length]!r}")


async def main() -> None:
    dev = await BluetoothDevice.from_bluetooth_address_async(0x0687E60C6261)
    res = await dev.get_rfcomm_services_async()
    svc = next(s for s in res.services if "00001101" in str(s.service_id.uuid).lower())
    sock = StreamSocket()
    await sock.connect_async(svc.connection_host_name, svc.connection_service_name)
    ins, outs = sock.input_stream, sock.output_stream
    print("conectado; escutando antes de enviar")
    await pump(ins, 2.0, "idle", sock)
    for payload in (b"$LHBT TJD_APP TJD_Dev 0 7 X1,LINK#", brlt("AB", "00", "", ""), b"\r\n", b"AT\r\n"):
        print(f"TX {payload!r}")
        outs.write_async(payload)
        await pump(ins, 2.0, payload[:12].hex(), sock)
    sock.close()


asyncio.run(main())
