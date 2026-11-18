import argparse
import asyncio
import datetime

import blecom


def make_handler(uuid: str):
    def handler(_char, data: bytearray) -> None:
        stamp = datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]
        print(f"{stamp}  {uuid[:8]}  {bytes(data).hex(' ')}")

    return handler


async def run(args: argparse.Namespace) -> None:
    print(f"Conectando em {args.address} ...")
    client = await blecom.connect(args.address, timeout=args.timeout)
    try:
        print(f"Conectado. mtu={client.mtu_size}. Escutando por {args.seconds:.0f}s.")
        subscribed = 0
        for service in blecom.services_of(client):
            for char in service.characteristics:
                if "notify" not in char.properties and "indicate" not in char.properties:
                    continue
                try:
                    await client.start_notify(char, make_handler(char.uuid))
                    print(f"  escutando {char.uuid}")
                    subscribed += 1
                except Exception as exc:  # noqa: BLE001
                    print(f"  falha ao escutar {char.uuid}: {type(exc).__name__}: {exc}")
        print(f"\n{subscribed} canal(is) ativo(s). Aperta botões do relógio para gerar eventos.\n")
        await asyncio.sleep(args.seconds)
        print(f"\nainda conectado: {client.is_connected}")
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Escuta notificações do relógio.")
    parser.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    parser.add_argument("--seconds", type=float, default=30.0)
    parser.add_argument("--timeout", type=float, default=12.0)
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
