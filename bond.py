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
        print("Pareando... (se aparecer aviso do Windows, aceite)")
        try:
            await client.pair()
            print("pair() retornou sem erro")
        except Exception as exc:  # noqa: BLE001
            hresult = getattr(exc, "hresult", None)
            print(f"pair() falhou: {type(exc).__name__}: {exc} hresult={hresult}")

        print(f"ainda conectado: {client.is_connected}")
        if not client.is_connected:
            return

        for uuid in args.notify:
            try:
                await client.start_notify(uuid, make_handler(uuid[:8]))
                print(f"notify ativo em {uuid}")
            except Exception as exc:  # noqa: BLE001
                print(f"notify em {uuid} falhou: {type(exc).__name__}: {exc}")

        for uuid in args.read:
            try:
                value = await client.read_gatt_char(uuid)
                print(f"read {uuid}: {bytes(value).hex(' ') or '(vazio)'}")
            except Exception as exc:  # noqa: BLE001
                print(f"read {uuid} falhou: {type(exc).__name__}: {exc}")

        if args.seconds:
            print(f"\nescutando {args.seconds:.0f}s...")
            await asyncio.sleep(args.seconds)
            print(f"ainda conectado: {client.is_connected}")
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Pareia e testa notificações/leituras.")
    parser.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    parser.add_argument("--notify", nargs="*", default=["00002e00-0000-1000-8000-00805f9b34fb", "00002aa0-0000-1000-8000-00805f9b34fb"])
    parser.add_argument("--read", nargs="*", default=["00002aa0-0000-1000-8000-00805f9b34fb"])
    parser.add_argument("--seconds", type=float, default=15.0)
    parser.add_argument("--timeout", type=float, default=20.0)
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
