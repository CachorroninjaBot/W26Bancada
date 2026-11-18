"""Teste do protocolo TJD sobre SPP classico do relogio."""
import asyncio
import sys

from winrt.windows.devices.bluetooth import BluetoothDevice
from winrt.windows.networking.sockets import StreamSocket
from winrt.windows.storage.streams import DataReader, DataWriter, InputStreamOptions

CLASSIC = 0x0687E60C6261
SPP = "00001101-0000-1000-8000-00805f9b34fb"


def frame(cmd: str) -> bytes:
    return f"$LHBT TJD_APP TJD_Dev 0 {len(cmd)} {cmd}#".encode()


async def read_some(reader: DataReader, timeout: float) -> bytes:
    try:
        n = await asyncio.wait_for(reader.load_async(4096), timeout)
    except asyncio.TimeoutError:
        return b""
    if n == 0:
        return b""
    return bytes(reader.read_bytes(n))


async def run(commands) -> None:
    dev = await BluetoothDevice.from_bluetooth_address_async(CLASSIC)
    res = await dev.get_rfcomm_services_async()
    svc = next(s for s in res.services if str(s.service_id.uuid).lower() == SPP)
    print(f"SPP {svc.service_id.uuid} | paired={dev.device_information.pairing.is_paired}")
    sock = StreamSocket()
    await sock.connect_async(svc)
    print("conectado")
    reader = DataReader(sock.input_stream)
    reader.input_stream_options = InputStreamOptions.PARTIAL
    try:
        for cmd in commands:
            data = frame(cmd)
            print(f">> {data!r}")
            w = DataWriter(sock.output_stream)
            w.write_bytes(data)
            await w.store_async()
            reply = await read_some(reader, 4.0)
            print(f"   << {reply!r}")
    finally:
        reader.detach_stream()
        sock.close()


if __name__ == "__main__":
    cmds = sys.argv[1:] or ["X1,LINK", "X0,PS,GET"]
    asyncio.run(run(cmds))
