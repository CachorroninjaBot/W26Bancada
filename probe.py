import argparse
import asyncio

import blecom

SKIP_READ = {"00002a05-0000-1000-8000-00805f9b34fb"}


def show(value: bytes) -> str:
    if not value:
        return "(vazio)"
    text = value.decode("utf-8", errors="replace")
    if not text.isprintable():
        text = "(bytes)"
    return f"{value.hex(' ')}  [{text}]"


async def read_one(address: str, uuid: str, delay: float, timeout: float) -> None:
    client = await blecom.connect(address, timeout=timeout)
    try:
        await asyncio.sleep(delay)
        char = client.services.get_characteristic(uuid)
        if char is None:
            print(f"Característica {uuid} não existe.")
            return
        value = await client.read_gatt_char(char)
        print(f"OK  {uuid}: {show(bytes(value))}")
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


async def run(args: argparse.Namespace) -> None:
    if args.char:
        await read_one(args.address, args.char, args.delay, args.timeout)
        return

    client = await blecom.connect(args.address, timeout=args.timeout)
    try:
        targets = [
            (service.uuid, char.uuid)
            for service in blecom.services_of(client)
            for char in service.characteristics
            if "read" in char.properties and char.uuid.lower() not in SKIP_READ
        ]
    finally:
        await client.disconnect()

    print(f"{len(targets)} característica(s) legível(is). Uma conexão nova para cada uma.\n")
    ok = 0
    for service_uuid, char_uuid in targets:
        print(f"--- {char_uuid} (svc {service_uuid})")
        try:
            await read_one(args.address, char_uuid, args.delay, args.timeout)
            ok += 1
        except Exception as exc:  # noqa: BLE001
            print(f"FALHOU {char_uuid}: {type(exc).__name__}: {exc}")
        await asyncio.sleep(0.5)
    print(f"\n{ok}/{len(targets)} leituras ok.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Lê características GATT com conexão nova por característica.")
    parser.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    parser.add_argument("--char", help="lê só esta característica (UUID)")
    parser.add_argument("--delay", type=float, default=0.5, help="espera após conectar, antes de ler")
    parser.add_argument("--timeout", type=float, default=12.0)
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
