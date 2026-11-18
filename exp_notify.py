import asyncio

import blecom

SVC = "000018e0-0000-1000-8000-00805f9b34fb"
CHR = "00002e01-0000-1000-8000-00805f9b34fb"


def frame(cmd: str) -> bytes:
    return f"$LHBT TJD_APP TJD_Dev 0 {len(cmd)} {cmd}#".encode()


async def attempt(client, label, fn):
    try:
        await fn()
        print(f"  {label}: OK")
    except Exception as exc:  # noqa: BLE001
        print(f"  {label}: {type(exc).__name__}: {exc}")


async def run() -> None:
    client = await blecom.connect("C2:BA:45:15:C8:F9")
    try:
        print(f"mtu={client.mtu_size}")
        ch = client.services.get_characteristic(CHR) if hasattr(client.services, "get_characteristic") else None
        if ch is None:
            for svc in client.services:
                if svc.uuid.lower() == SVC:
                    for c in svc.characteristics:
                        if c.uuid.lower() == CHR:
                            ch = c
        print("props:", ch.properties)

        async def cb(sender, data):
            print("  <<", bytes(data))

        await attempt(client, "write X1,LINK", lambda: client.write_gatt_char(ch, frame("X1,LINK"), response=True))
        await asyncio.sleep(1.0)
        await attempt(client, "start_notify", lambda: client.start_notify(ch, cb))
        await asyncio.sleep(1.0)

        async def wr2e10(val):
            for d in ch.descriptors:
                if d.uuid.lower().startswith("00002e10"):
                    await client.write_gatt_descriptor(d, val)
                    print(f"    2E10 <- {val.hex()}")

        await attempt(client, "write 2E10=0200", lambda: wr2e10(b"\x02\x00"))
        await attempt(client, "start_notify apos 2E10", lambda: client.start_notify(ch, cb))
        await attempt(client, "write X0,PS,GET", lambda: client.write_gatt_char(ch, frame("X0,PS,GET"), response=True))
        await asyncio.sleep(2.0)
        print("fim")
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


asyncio.run(run())
