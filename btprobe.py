import argparse
import socket
import sys

DEFAULT_ADDR = "06:87:E6:0C:62:61"


def try_channel(address: str, channel: int, timeout: float) -> tuple[bool, str, bytes]:
    sock = socket.socket(socket.AF_BTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    sock.settimeout(timeout)
    try:
        sock.connect((address, channel))
        try:
            data = sock.recv(64)
        except socket.timeout:
            data = b""
        except OSError:
            data = b""
        return True, "aberto", data
    except OSError as exc:
        return False, f"{exc.winerror if hasattr(exc, 'winerror') else ''} {exc}", b""
    finally:
        sock.close()


def main() -> None:
    if sys.platform != "win32":
        raise SystemExit("Este script usa sockets Bluetooth do Windows (AF_BTH).")
    parser = argparse.ArgumentParser(description="Sonda canais RFCOMM/SPP do relógio.")
    parser.add_argument("address", nargs="?", default=DEFAULT_ADDR)
    parser.add_argument("--first", type=int, default=1)
    parser.add_argument("--last", type=int, default=30)
    parser.add_argument("--timeout", type=float, default=2.0)
    args = parser.parse_args()

    print(f"Sondando canais {args.first}-{args.last} em {args.address} ...\n")
    opened = []
    for channel in range(args.first, args.last + 1):
        ok, info, data = try_channel(args.address, channel, args.timeout)
        if ok:
            extra = f"  recebeu: {data.hex(' ')}" if data else ""
            print(f"  canal {channel:>2}: ABERTO{extra}")
            opened.append(channel)
        elif "10049" not in info and "10060" not in info and "10061" not in info:
            print(f"  canal {channel:>2}: {info}")
    print(f"\n{len(opened)} canal(is) aberto(s): {opened or 'nenhum'}")


if __name__ == "__main__":
    main()
