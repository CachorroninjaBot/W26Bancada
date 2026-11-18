import argparse
import asyncio
import datetime
import sys
import time

from bleak import BleakClient, BleakScanner
from bleak.args.winrt import WinRTClientArgs


def explain(exc: BaseException) -> str:
    parts = [f"{type(exc).__name__}: {exc}"]
    hresult = getattr(exc, "hresult", None)
    if hresult is not None:
        parts.append(f"hresult={hresult:#010x}")
    return " | ".join(parts)


async def attempt(label: str, factory, hold: float) -> bool:
    print(f"\n== {label}")
    try:
        async with factory() as client:
            print(f"   conectado: {client.is_connected}")
            try:
                print(f"   mtu: {client.mtu_size}")
            except Exception:  # noqa: BLE001
                pass
            print(f"   serviços: {sum(1 for _ in client.services)}")
            await asyncio.sleep(hold)
            print(f"   após {hold:.0f}s ainda conectado: {client.is_connected}")
            return True
    except Exception as exc:  # noqa: BLE001
        print(f"   falhou: {explain(exc)}")
        return False


async def run(args: argparse.Namespace) -> None:
    fresh = WinRTClientArgs(use_cached_services=False)
    cached = WinRTClientArgs(use_cached_services=True)

    if args.loop > 0:
        deadline = time.monotonic() + args.loop
        found: dict[str, tuple] = {}
        target = args.address.upper()

        def on_detect(device, adv) -> None:
            if device.address.upper() == target:
                found[device.address.upper()] = (device, adv)

        print("Escutando anúncios continuamente... (mantenha a tela do relógio acesa)")
        scanner = BleakScanner(detection_callback=on_detect)
        await scanner.start()
        attempt_no = 0
        try:
            while time.monotonic() < deadline:
                if not found:
                    await asyncio.sleep(0.3)
                    continue
                device, adv = found.pop(target)
                attempt_no += 1
                stamp = datetime.datetime.now().strftime("%H:%M:%S")
                rssi = getattr(adv, "rssi", "?")
                print(f"\n--- tentativa {attempt_no} ({stamp}) visto rssi={rssi} dBm")
                await scanner.stop()
                ok = await attempt(
                    "conectar",
                    lambda: BleakClient(device, timeout=8, winrt=fresh),
                    hold=4,
                )
                if ok:
                    print("\nLink estável. Podemos seguir para o probe.")
                    return
                await scanner.start()
        finally:
            try:
                await scanner.stop()
            except Exception:  # noqa: BLE001
                pass
        print("\nTempo esgotado sem conexão estável.")
        return

    print(f"Procurando {args.address} ...")
    device = await BleakScanner.find_device_by_address(args.address, timeout=15)
    if device is None:
        print("Não encontrado anunciando. Acorda o relógio e tenta de novo.")
        sys.exit(1)
    print(f"achado: {device.name} @ {device.address} ({getattr(device.details, 'rssi', '?')} dBm)")

    strategies = [
        (
            "BLEDevice + serviços frescos",
            lambda: BleakClient(device, timeout=args.timeout, winrt=fresh),
        ),
        (
            "endereço + serviços frescos",
            lambda: BleakClient(args.address, timeout=args.timeout, winrt=fresh),
        ),
        (
            "BLEDevice + cache",
            lambda: BleakClient(device, timeout=args.timeout, winrt=cached),
        ),
    ]
    if args.pair:
        strategies.append(
            (
                "BLEDevice + pareamento (pode aparecer diálogo no Windows)",
                lambda: BleakClient(device, timeout=args.timeout, pair=True, winrt=fresh),
            )
        )

    for label, factory in strategies:
        if await attempt(label, factory, args.timeout, args.hold):
            print("\nDeu certo. Use essa estratégia nas próximas etapas.")
            return
        await asyncio.sleep(args.pause)

    print("\nNenhuma estratégia conectou. Provável causa: relógio segurando link com o lado Classic/outro host.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Testa estratégias de conexão BLE no Windows.")
    parser.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--hold", type=float, default=5.0)
    parser.add_argument("--pause", type=float, default=3.0)
    parser.add_argument("--pair", action="store_true", help="tenta parear como última estratégia")
    parser.add_argument("--loop", type=float, default=0, help="segundos de tentativa contínua (0 = desligado)")
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
