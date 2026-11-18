"""Notificacoes no W26 via MTK 3.0 sobre BLE GATT (servico 18A0).

2AA1 = escrita (phone->relogio), 2AA0 = notify/leitura (relogio->phone).
Uso: python mtk_ble.py [--notify TEXTO] [--title T] [--sender S] [--dump] [--no-handshake]
"""
import argparse
import asyncio
import datetime

import blecom
import mtkproto

SVC = "000018a0-0000-1000-8000-00805f9b34fb"
CHR_W = "00002aa1-0000-1000-8000-00805f9b34fb"
CHR_R = "00002aa0-0000-1000-8000-00805f9b34fb"


def stamp() -> str:
    return datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]


def on_rx(_sender, data: bytearray) -> None:
    raw = bytes(data)
    frames = mtkproto.decode(raw)
    if frames:
        for _f, plain in frames:
            print(f"{stamp()} RX {plain[:160]!r}")
    else:
        print(f"{stamp()} RX cru {raw[:48].hex(' ')}")


async def run(args) -> None:
    client = await blecom.connect(args.address, timeout=args.timeout)
    try:
        print(f"conectado, mtu={client.mtu_size}")
        if args.dump:
            for svc in client.services:
                print(f"[SVC] {svc.uuid}")
                for ch in svc.characteristics:
                    print(f"  [CHR] {ch.uuid} {','.join(ch.properties)}")
                    for desc in ch.descriptors:
                        print(f"        [DSC] {desc.uuid}")
        try:
            await client.start_notify(CHR_R, on_rx)
            print("notify ativo em 2AA0")
        except Exception as exc:  # noqa: BLE001
            print(f"sem notify ({exc})")

        async def send(payload: bytes, label: str, wait: float = 1.0) -> None:
            print(f"{stamp()} TX {label} ({len(payload)}b)")
            await client.write_gatt_char(CHR_W, payload, response=False)
            await asyncio.sleep(wait)

        if not args.no_handshake:
            await send(mtkproto.cmd("SYNC", "REQV"), "REQV")
        now = int(datetime.datetime.now().timestamp())
        await send(
            mtkproto.cmd("SYNC", f"{now} {mtkproto.tz_offset_ms()}"), "SYNC hora"
        )
        if args.notify is not None:
            msg = mtkproto.notification(
                sender=args.sender, title=args.title, content=args.notify
            )
            print(f"{stamp()} TX notificacao ({len(msg)}b)")
            await client.write_gatt_char(CHR_W, msg, response=False)

        await asyncio.sleep(args.seconds)
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


def main() -> None:
    p = argparse.ArgumentParser(description="MTK 3.0 sobre BLE GATT para o W26.")
    p.add_argument("--address", default="C2:BA:45:15:C8:F9")
    p.add_argument("--notify")
    p.add_argument("--title", default="Teste do PC")
    p.add_argument("--sender", default="Qoder")
    p.add_argument("--timeout", type=float, default=12.0)
    p.add_argument("--seconds", type=float, default=4.0)
    p.add_argument("--dump", action="store_true")
    p.add_argument("--no-handshake", action="store_true")
    args = p.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
