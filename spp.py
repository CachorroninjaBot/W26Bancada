import argparse
import asyncio
import datetime

from winrt.windows.devices.bluetooth import BluetoothDevice
from winrt.windows.devices.bluetooth.rfcomm import RfcommServiceId
from winrt.windows.networking.sockets import StreamSocket
from winrt.windows.storage.streams import DataReader


def stamp() -> str:
    return datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]


async def read_loop(reader: DataReader, seconds: float) -> None:
    deadline = asyncio.get_event_loop().time() + seconds
    while asyncio.get_event_loop().time() < deadline:
        remaining = deadline - asyncio.get_event_loop().time()
        try:
            loaded = await asyncio.wait_for(reader.load_async(256), timeout=min(2.0, remaining))
        except asyncio.TimeoutError:
            continue
        if loaded == 0:
            print(f"{stamp()}  conexão encerrada pelo relógio")
            return
        data = bytearray(loaded)
        reader.read_bytes(data)
        print(f"{stamp()}  RX  {bytes(data).hex(' ')}")


async def run(args: argparse.Namespace) -> None:
    device = await BluetoothDevice.from_bluetooth_address_async(int(args.address, 16))
    if device is None:
        print("Dispositivo clássico não encontrado.")
        return
    print(f"dispositivo: {device.name}  status={device.connection_status}")

    result = await device.get_rfcomm_services_async()
    services = list(result.services)
    sps = [s for s in services if "00001101" in str(s.service_id.uuid).lower()]
    if not sps:
        uuids = [str(s.service_id.uuid) for s in services]
        print(f"Nenhum serviço SPP. serviços vistos: {uuids} erro={result.error}")
        return
    svc = sps[0]

    sock = StreamSocket()
    print("conectando no SPP...")
    await sock.connect_async(svc.connection_host_name, svc.connection_service_name)
    print("conectado no SPP")

    if args.send:
        payload = bytes.fromhex(args.send.replace(" ", ""))
        print(f"{stamp()}  TX  {payload.hex(' ')}")
        await sock.output_stream.write_async(payload)

    print(f"escutando por {args.seconds:.0f}s ...")
    reader = DataReader(sock.input_stream)
    await read_loop(reader, args.seconds)

    try:
        reader.close()
    except Exception:  # noqa: BLE001
        pass
    try:
        sock.close()
    except Exception:  # noqa: BLE001
        pass
    print("fim.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Conecta no SPP clássico do relógio e escuta.")
    parser.add_argument("address", nargs="?", default="0x0687E60C6261")
    parser.add_argument("--seconds", type=float, default=30.0)
    parser.add_argument("--send", default="", help="hex para enviar (ex: '00 01 02')")
    args = parser.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
