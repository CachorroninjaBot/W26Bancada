import asyncio

from bleak import BleakClient, BleakScanner
from bleak.args.winrt import WinRTClientArgs


def _new_client(target, timeout: float, pair: bool = False) -> BleakClient:
    return BleakClient(
        target,
        timeout=timeout,
        pair=pair,
        winrt=WinRTClientArgs(use_cached_services=False),
    )


async def connect(address: str, timeout: float = 12.0, retries: int = 4, pause: float = 1.5) -> BleakClient:
    last: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            device = await BleakScanner.find_device_by_address(address, timeout=6)
            if device is None:
                last = TimeoutError("relógio não está anunciando (tela apagada?)")
            else:
                client = _new_client(device, timeout)
                await client.connect()
                return client
        except Exception as exc:  # noqa: BLE001
            last = exc
            print(f"  conexão {attempt}/{retries} falhou: {type(exc).__name__}: {exc}")
        await asyncio.sleep(pause)
    raise RuntimeError(f"não consegui conectar: {last}")


def services_of(client: BleakClient) -> list:
    return list(client.services)
