"""Cliente SPP do protocolo MTK 3.0 (MTK wearable) para o W26.

Uso: python mtkcli.py [MAC] [--notify TEXTO] [--seconds 10]
"""
import argparse
import asyncio
import datetime

from winrt.windows.devices.bluetooth import BluetoothDevice
from winrt.windows.networking.sockets import StreamSocket
from winrt.windows.storage.streams import Buffer, DataReader, InputStreamOptions

import mtkproto


def stamp() -> str:
    return datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]


def show_rx(label: str, chunk: bytes) -> None:
    frames = mtkproto.decode(chunk)
    if not frames:
        print(f"{label} RX cru {chunk.hex(' ')}")
        return
    for raw, plain in frames:
        if plain[:4] in (b"DATA", b"SYNC", b"MAPX", b"MAPD", b"CAC", b"CAPC", b"MREE", b"EXCD"):
            print(f"{label} RX {plain!r}")
        elif plain.isascii() and plain.rstrip(b"\x00").isprintable():
            print(f"{label} RX {plain!r}")
        else:
            print(f"{label} RX len={len(raw)} hex={raw.hex(' ')}")


async def pump(ins, seconds: float, label: str) -> None:
    deadline = asyncio.get_running_loop().time() + seconds
    while asyncio.get_running_loop().time() < deadline:
        buf = Buffer(4096)
        try:
            await asyncio.wait_for(
                ins.read_async(buf, 4096, InputStreamOptions.PARTIAL), 1.0
            )
        except asyncio.TimeoutError:
            continue
        except Exception as exc:  # noqa: BLE001
            print(f"{label} leitura encerrada: {type(exc).__name__}: {exc}")
            return
        data = bytes(memoryview(buf))[:buf.length]
        if not data:
            print(f"{label} conexao encerrada")
            return
        show_rx(label, data)


async def run(args) -> None:
    dev = await BluetoothDevice.from_bluetooth_address_async(int(args.address, 16))
    print(f"dispositivo: {dev.name} status={dev.connection_status}")
    res = await dev.get_rfcomm_services_async()
    sps = [s for s in res.services if "00001101" in str(s.service_id.uuid).lower()]
    if not sps:
        print("sem SPP; servicos:", [str(s.service_id.uuid) for s in res.services], "erro:", res.error)
        return
    sock = StreamSocket()
    print("conectando no SPP...")
    await sock.connect_async(sps[0].connection_host_name, sps[0].connection_service_name)
    print("conectado")
    ins, outs = sock.input_stream, sock.output_stream

    async def send(payload: bytes, label: str, wait: float = 2.0) -> None:
        print(f"{stamp()} TX {label} ({len(payload)} bytes)")
        outs.write_async(payload)
        await pump(ins, wait, label)

    if not args.no_handshake:
        await send(mtkproto.cmd("SYNC", "REQV"), "REQV")
    now = int(datetime.datetime.now().timestamp())
    await send(mtkproto.cmd("SYNC", f"{now} {mtkproto.tz_offset_ms()}"), "SYNC heh")
    await send(mtkproto.cmd("EXCD", "mtk_msctrl msctrl_apk 0 0 4"), "EXCD msctrl")
    await send(mtkproto.cmd("MREE", "mre_installer smartwatch 9 0 5"), "MREE installer")
    await send(mtkproto.cmd("EXCD", "language mtk_language 1 0 5"), "EXCD lang")

    if args.notify is not None:
        msg = mtkproto.notification(
            sender=args.sender, title=args.title, content=args.notify
        )
        print(f"{stamp()} TX notificacao ({len(msg)} bytes)")
        outs.write_async(msg)

    print(f"escutando por {args.seconds:.0f}s...")
    await pump(ins, args.seconds, "idle")
    sock.close()
    print("fim")


def main() -> None:
    p = argparse.ArgumentParser(description="Cliente MTK 3.0 do W26 via SPP.")
    p.add_argument("address", nargs="?", default="0x0687E60C6261")
    p.add_argument("--notify", help="conteudo da notificacao de teste")
    p.add_argument("--title", default="Teste do PC")
    p.add_argument("--sender", default="Qoder")
    p.add_argument("--no-handshake", action="store_true")
    p.add_argument("--seconds", type=float, default=8.0)
    args = p.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
