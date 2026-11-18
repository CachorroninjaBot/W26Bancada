import argparse
import asyncio

import blecom

KNOWN_SERVICES = {
    "00001800-0000-1000-8000-00805f9b34fb": "Generic Access",
    "00001801-0000-1000-8000-00805f9b34fb": "Generic Attribute",
    "0000180a-0000-1000-8000-00805f9b34fb": "Device Information",
    "0000180d-0000-1000-8000-00805f9b34fb": "Heart Rate",
    "0000180f-0000-1000-8000-00805f9b34fb": "Battery",
    "00001812-0000-1000-8000-00805f9b34fb": "HID",
    "00001802-0000-1000-8000-00805f9b34fb": "Immediate Alert",
    "00001803-0000-1000-8000-00805f9b34fb": "Link Loss",
    "00001804-0000-1000-8000-00805f9b34fb": "Tx Power",
    "0000180e-0000-1000-8000-00805f9b34fb": "vendor 0x180E",
    "000018a0-0000-1000-8000-00805f9b34fb": "vendor 0x18A0",
}

KNOWN_CHARS = {
    "00002a00-0000-1000-8000-00805f9b34fb": "Device Name",
    "00002a01-0000-1000-8000-00805f9b34fb": "Appearance",
    "00002a04-0000-1000-8000-00805f9b34fb": "Preferred Conn Params",
    "00002a05-0000-1000-8000-00805f9b34fb": "Service Changed",
    "00002a06-0000-1000-8000-00805f9b34fb": "Alert Level",
    "00002a07-0000-1000-8000-00805f9b34fb": "Tx Power Level",
    "00002a19-0000-1000-8000-00805f9b34fb": "Battery Level",
    "00002a24-0000-1000-8000-00805f9b34fb": "Model Number",
    "00002a25-0000-1000-8000-00805f9b34fb": "Serial Number",
    "00002a26-0000-1000-8000-00805f9b34fb": "Firmware Revision",
    "00002a27-0000-1000-8000-00805f9b34fb": "Hardware Revision",
    "00002a28-0000-1000-8000-00805f9b34fb": "Software Revision",
    "00002a29-0000-1000-8000-00805f9b34fb": "Manufacturer Name",
    "00002a37-0000-1000-8000-00805f9b34fb": "Heart Rate Measurement",
}

SKIP_READ = {"00002a05-0000-1000-8000-00805f9b34fb"}


def show(value: bytes) -> str:
    if not value:
        return "(vazio)"
    text = value.decode("utf-8", errors="replace")
    if not text.isprintable():
        text = "(bytes)"
    return f"{value.hex(' ')}  [{text}]"


async def run(args: argparse.Namespace) -> None:
    client = await blecom.connect(args.address, timeout=args.timeout)
    try:
        print(f"Conectado. mtu={client.mtu_size}")
        services = blecom.services_of(client)
        print(f"{len(services)} serviço(s)\n")
        for service in services:
            svc = KNOWN_SERVICES.get(service.uuid.lower(), "vendor/service")
            print(f"[SVC] {service.uuid}  {svc}")
            for char in service.characteristics:
                props = ",".join(char.properties)
                name = KNOWN_CHARS.get(char.uuid.lower(), "")
                line = f"  [CHR] {char.uuid}  {props}"
                if name:
                    line += f"  ({name})"
                print(line)
                if args.read and "read" in char.properties and char.uuid.lower() not in SKIP_READ:
                    await asyncio.sleep(args.read_delay)
                    try:
                        print(f"        -> {show(bytes(await client.read_gatt_char(char)))}")
                    except Exception as exc:  # noqa: BLE001
                        print(f"        -> erro: {type(exc).__name__}: {exc}")
                for desc in char.descriptors:
                    print(f"        [DSC] {desc.uuid}")
        if args.hold:
            print(f"\nSegurando link por {args.hold:.0f}s...")
            await asyncio.sleep(args.hold)
            print(f"ainda conectado: {client.is_connected}")
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Mapa GATT completo do relógio.")
    parser.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    parser.add_argument("--read", action="store_true", help="ler as características legíveis")
    parser.add_argument("--read-delay", type=float, default=0.4)
    parser.add_argument("--hold", type=float, default=0, help="segundos para segurar o link depois do mapa")
    parser.add_argument("--timeout", type=float, default=12.0)
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
