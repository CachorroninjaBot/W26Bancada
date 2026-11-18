import argparse
import asyncio

from bleak import BleakScanner


def fmt_hex(data: bytes) -> str:
    return data.hex(" ")


async def run(args: argparse.Namespace) -> None:
    print(f"Escaneando BLE por {args.timeout:.0f}s...")
    found = await BleakScanner.discover(timeout=args.timeout, return_adv=True)

    rows = []
    for device, adv in found.values():
        name = adv.local_name or device.name or ""
        if not args.all and not name:
            continue
        if args.name and args.name.lower() not in name.lower():
            continue
        rows.append((name, device.address, adv.rssi, adv))

    if not rows:
        print("Nada encontrado com esse filtro. Tente --all ou um --name vazio.")
        return

    rows.sort(key=lambda r: r[2], reverse=True)
    print(f"\n{len(rows)} dispositivo(s):\n")
    for name, address, rssi, adv in rows:
        print(f"  {rssi:>4} dBm  {address}  {name or '(sem nome)'}")
        if adv.manufacturer_data:
            parts = [f"0x{mid:04x}={fmt_hex(payload)}" for mid, payload in adv.manufacturer_data.items()]
            print(f"            manufacturer: {'; '.join(parts)}")
        if adv.service_uuids:
            print(f"            services: {', '.join(adv.service_uuids)}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Lista dispositivos BLE por perto.")
    parser.add_argument("--name", default="", help="filtra por trecho do nome (case-insensitive)")
    parser.add_argument("--all", action="store_true", help="inclui dispositivos sem nome")
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
