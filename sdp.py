import argparse
import asyncio

from winrt.windows.devices.bluetooth import BluetoothDevice


def prop(obj, name: str) -> str:
    try:
        return str(getattr(obj, name))
    except Exception as exc:  # noqa: BLE001
        return f"<{type(exc).__name__}: {exc}>"


async def run(address: int) -> None:
    device = await BluetoothDevice.from_bluetooth_address_async(address)
    if device is None:
        print("Dispositivo clássico não encontrado (relógio longe/desligado?).")
        return
    print(f"nome: {device.name}")
    print(f"device_id: {prop(device, 'device_id')}")
    print(f"connection_status: {prop(device, 'connection_status')}")
    print(f"paired: {prop(device.device_information.pairing, 'is_paired')}")
    try:
        result = await device.get_rfcomm_services_async()
    except Exception as exc:  # noqa: BLE001
        print(f"get_rfcomm_services_async falhou: {type(exc).__name__}: {exc}")
        return
    print(f"erro: {prop(result, 'error')}")
    services = list(result.services)
    print(f"\n{len(services)} serviço(s) RFCOMM:")
    for svc in services:
        print(f"  uuid: {prop(svc.service_id, 'uuid')}")
        print(f"  nome: {prop(svc, 'connection_service_name')}")
        print(f"  host: {prop(svc, 'connection_host_name')}")
        print()


def main() -> None:
    parser = argparse.ArgumentParser(description="Lista serviços RFCOMM clássicos do relógio.")
    parser.add_argument("address", nargs="?", default="0x0687E60C6261", help="MAC clássico em hex")
    args = parser.parse_args()
    asyncio.run(run(int(args.address, 16)))


if __name__ == "__main__":
    main()
