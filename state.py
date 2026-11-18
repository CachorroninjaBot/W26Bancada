import asyncio

from winrt.windows.devices.bluetooth import BluetoothDevice, BluetoothLEDevice
from winrt.windows.devices.enumeration import DeviceInformation

ADDRESSES = ("06:87:E6:0C:62:61", "C2:BA:45:15:C8:F9")


def prop(obj, name: str) -> str:
    try:
        return str(getattr(obj, name))
    except Exception as exc:  # noqa: BLE001
        return f"<{type(exc).__name__}: {exc}>"


async def dump(label: str, selector: str, pair: bool) -> None:
    print(f"\n== {label}")
    infos = await DeviceInformation.find_all_async(selector, [])
    matched = 0
    for info in infos:
        ident = (info.id or "").upper()
        name = info.name or ""
        if "WATCH" not in name.upper() and not any(a.upper() in ident for a in ADDRESSES):
            continue
        matched += 1
        print(f"  id: {info.id}")
        print(f"  nome: {name}  habilitado: {info.is_enabled}  kind: {info.kind}")
        pairing = info.pairing
        print(
            "  paired:",
            prop(pairing, "is_paired"),
            " can_pair:",
            prop(pairing, "can_pair"),
            " status:",
            prop(pairing, "pairing_status"),
        )
        if pair and str(pairing.is_paired) != "True":
            print("  pareando...")
            try:
                result = await pairing.pair_async()
                print(f"  pair_async -> status={prop(result, 'status')}")
            except Exception as exc:  # noqa: BLE001
                print(f"  pair_async falhou: {type(exc).__name__}: {exc}")
    if not matched:
        print("  (nenhum dispositivo 'Watch' enumerado agora)")


async def run(pair: bool) -> None:
    await dump("Dispositivos BLE", BluetoothLEDevice.get_device_selector(), pair)
    await dump("Dispositivos Bluetooth clássicos", BluetoothDevice.get_device_selector(), pair)


if __name__ == "__main__":
    import sys

    asyncio.run(run("--pair" in sys.argv))
