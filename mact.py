"""Cliente PC para o relogio W26 (protocolo TJD / app M Activie, device type 0).

Frames app->relogio:  $LHBT TJD_APP TJD_Dev 0 <len> <CMD>#   (ASCII cru no 2E01)
Respostas do relogio: notificacao no mesmo 2E01, texto terminado em '#'.
"""
import argparse
import asyncio
import datetime

from bleak.exc import BleakError

import blecom

SVC = "000018e0-0000-1000-8000-00805f9b34fb"
CHR = "00002e01-0000-1000-8000-00805f9b34fb"


def frame(cmd: str) -> bytes:
    return f"$LHBT TJD_APP TJD_Dev 0 {len(cmd)} {cmd}#".encode("ascii")


class Rx:
    def __init__(self) -> None:
        self.buf = ""
        self.rx: asyncio.Queue[str] = asyncio.Queue()

    def feed(self, sender, data: bytearray) -> None:
        text = bytes(data).decode("utf-8", errors="replace")
        print(f"  << {text!r}")
        self.buf += text
        while "#" in self.buf:
            msg, self.buf = self.buf.split("#", 1)
            self.rx.put_nowait(msg + "#")

    async def wait(self, timeout: float) -> str | None:
        try:
            return await asyncio.wait_for(self.rx.get(), timeout)
        except asyncio.TimeoutError:
            return None

    def drain(self) -> None:
        while not self.rx.empty():
            self.rx.get_nowait()


def find_char(client):
    for svc in client.services:
        if svc.uuid.lower() == SVC:
            for ch in svc.characteristics:
                if ch.uuid.lower() == CHR:
                    return ch
    raise BleakError(f"servico {SVC} / caracteristica {CHR} nao encontrados")


async def subscribe(client, rx: Rx) -> None:
    ch = find_char(client)
    print(f"2E01 properties: {','.join(ch.properties)}")
    for desc in ch.descriptors:
        print(f"  desc {desc.uuid}")
    errors = []
    for desc in ch.descriptors:
        if desc.uuid.lower().startswith("00002902"):
            try:
                await client.write_gatt_descriptor(desc, b"\x01\x00")
                print("  cccd 2902 escrito (0100)")
            except Exception as exc:  # noqa: BLE001
                errors.append(f"cccd 2902: {exc}")
                print(f"  cccd 2902 falhou: {exc}")
    try:
        await client.start_notify(ch, rx.feed)
        print("  notify ativo")
        return
    except Exception as exc:  # noqa: BLE001
        errors.append(f"start_notify: {exc}")
        print(f"  start_notify falhou: {exc}")
    for guess in ("00002e10", "00002e11"):
        for desc in ch.descriptors:
            if desc.uuid.lower().startswith(guess):
                try:
                    await client.write_gatt_descriptor(desc, b"\x01\x00")
                    print(f"  desc {desc.uuid} <- 0100")
                except Exception as exc:  # noqa: BLE001
                    print(f"  desc {desc.uuid} falhou: {exc}")
        try:
            await client.start_notify(ch, rx.feed)
            print("  notify ativo (apos descritor custom)")
            return
        except Exception as exc:  # noqa: BLE001
            errors.append(f"start_notify pos {guess}: {exc}")
    raise BleakError("nao consegui assinar notificacoes: " + " | ".join(errors))


async def send(client, rx: Rx, cmd: str, wait: float = 3.0) -> str | None:
    data = frame(cmd)
    print(f">> {data!r}")
    await client.write_gatt_char(find_char(client), data, response=True)
    reply = await rx.wait(wait)
    print(f"   resposta: {reply!r}" if reply else "   sem resposta")
    return reply


async def run(args) -> None:
    client = await blecom.connect(args.address, timeout=args.timeout)
    try:
        print(f"conectado mtu={client.mtu_size}")
        if args.pair:
            print("pareando...")
            try:
                ok = await client.pair()
                print(f"  pair -> {ok}")
            except Exception as exc:  # noqa: BLE001
                print(f"  pair falhou: {type(exc).__name__}: {exc}")
        rx = Rx()
        try:
            await subscribe(client, rx)
        except BleakError as exc:
            print(f"  sem notify ({exc}); seguindo so com escrita")
        if args.dump:
            for svc in client.services:
                print(f"[SVC] {svc.uuid}")
                for ch in svc.characteristics:
                    print(f"  [CHR] {ch.uuid} {','.join(ch.properties)}")
                    for desc in ch.descriptors:
                        print(f"        [DSC] {desc.uuid}")
        cmds = list(args.cmd or [])
        if not args.no_default:
            tz = datetime.datetime.now().astimezone().utcoffset()
            hours = int(tz.total_seconds() // 3600)
            tzstr = f"{hours:+03d}"
            now = datetime.datetime.now().strftime("%Y%m%d %H%M%S")
            cmds = [
                "X0,PS,GET",
                *(f"X0,GET,{n}" for n in range(10)),
                f"X1,PRSET,DATETIME,{now},{tzstr}",
                "X1,PRSET,LANG,pt_BR",
            ] + cmds
        cmds.insert(0, "X1,LINK")
        if args.find:
            cmds.append("X1,PRSET,FindDevice,1")
        if args.notify:
            cmds.append("X1,BTNoti," + args.notify)
        if args.goal:
            cmds.append("X0,PS,SET," + args.goal)
        if args.tfmt:
            cmds.append(f"X1,PRSET,TFMT,{args.tfmt}")
        for cmd in cmds:
            await send(client, rx, cmd, args.wait)
            await asyncio.sleep(args.gap)
        if args.sleep:
            print(f"segurando conexao por {args.sleep:.0f}s...")
            await asyncio.sleep(args.sleep)
    finally:
        try:
            await client.disconnect()
        except Exception:  # noqa: BLE001
            pass


def main() -> None:
    p = argparse.ArgumentParser(description="Cliente TJD para o W26 (M Activie).")
    p.add_argument("address", nargs="?", default="C2:BA:45:15:C8:F9")
    p.add_argument("--cmd", action="append", help="comando sem '$LHBT...#' (pode repetir)")
    p.add_argument("--wait", type=float, default=3.0, help="timeout por resposta (s)")
    p.add_argument("--gap", type=float, default=0.6)
    p.add_argument("--timeout", type=float, default=12.0)
    p.add_argument("--dump", action="store_true", help="imprime o mapa GATT")
    p.add_argument("--pair", action="store_true", help="pareia (just works) antes de assinar")
    p.add_argument("--no-default", action="store_true", help="nao envia o fluxo padrao (LINK/GETs/hora)")
    p.add_argument("--find", action="store_true", help="faz o relogio vibrar (FindDevice,1)")
    p.add_argument("--notify", help="X1,BTNoti com 5 campos separados por |")
    p.add_argument("--goal", help="X0,PS,SET,<passos>|<altura>|<peso> ex: 8000|170CM|60KG")
    p.add_argument("--tfmt", choices=["12", "24"], help="formato de hora")
    p.add_argument("--sleep", type=float, default=0.0, help="segura a conexao apos os comandos")
    args = p.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
