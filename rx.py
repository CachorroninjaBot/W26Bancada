import argparse
import re
import subprocess
import sys

TSHARK = r"C:\Program Files\Wireshark\tshark.exe"


def notifications(pcap: str) -> list[bytes]:
    out = subprocess.run(
        [TSHARK, "-r", pcap, "-Y", "btatt.opcode==0x1b", "-T", "fields", "-e", "btatt.value"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    frames = []
    for line in out.splitlines():
        line = line.strip()
        if line:
            frames.append(bytes.fromhex(line.replace(":", "")))
    return frames


def messages(frames: list[bytes]) -> list[str]:
    buf = b""
    msgs = []
    for chunk in frames:
        buf += chunk
        while b"#" in buf:
            raw, buf = buf.split(b"#", 1)
            text = raw.decode("utf-8", errors="replace") + "#"
            msgs.append(text)
    return msgs


def main() -> None:
    p = argparse.ArgumentParser(description="Decodifica respostas do relogio a partir do pcapng.")
    p.add_argument("pcap", nargs="?", default=r"tmp\bt.pcapng")
    p.add_argument("--raw", action="store_true", help="mostra o hex de cada notificacao")
    args = p.parse_args()
    frames = notifications(args.pcap)
    for msg in messages(frames):
        print(msg)
    if args.raw:
        print("---")
        for chunk in frames:
            print(chunk.hex(" "))


if __name__ == "__main__":
    main()
