import argparse
import asyncio
import datetime

import blecom


def make_handler(tag: str):
    def handler(_char, data: bytearray) -> None:
        stamp = datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]
        print(f"{stamp}  {tag}  {bytes(data).hex(' ')}")

    return handler


async def run(args: argparse.Namespace) -> None:
    client = await blecom.connect(args.address, timeout=args.timeout)
    try:
        print(f"Conectado. mtu={client.mtu_size}")
        descriptors = [
            (char.uuid, desc)
            for service in blecom.services_of(client)
            for char in service.characteristics
            for desc in char.descriptors
        ]
        print(f"{len(descriptors)} descritor(es):")
        for char_uuid, desc in descriptors:
            print(f"  {desc.uuid}  (char {char_uuid})")

        for char_uuid, desc in descriptors:
            try:
                await client.write_gatt_descriptor(desc, b"\x01\x00")
                print(f"write 0100 -> {desc.uuid}: ok")
            except Exception as exc:  # noqa: BLE001
                print(f"write 0100 -> {desc.uuid}: {type(exc).__name__}: {exc}")
            await asyncio.sleep(0.3)

        targets = [
            char
            for service in blecom.services_of(client)
            for char in service.characteristics
            if "notify" in char.properties or "indicate" in char.properties
        ]
        subscribed = 0
        for char in targets:
            try:
                await client.start_notify(char, make_handler(char.uuid[:8]))
                print(f"notify ok em {char.uuid}")
                subscribed += 1
            except Exception as exc:  # noqa: BLE001
                print(f"notify falhou em {char.uuid}: {type(exc).__name__}: {exc}")

        if subscribed:
            print(f"\n{subscribed} canal(is) ativo(s). Aperta botões do relógio. {args.seconds:.0f}s ...")
            await asyncio.sleep(args.seconds)
        print(f"\nainda conectado: {client.is_connected}")
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Tenta habilitar notificações via descritores customizados.")
    parser.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    parser.add_argument("--seconds", type=float, default=15.0)
    parser.add_argument("--timeout", type=float, default=20.0)
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
