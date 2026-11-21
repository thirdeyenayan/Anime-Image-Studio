from __future__ import annotations

import socket
import threading
import time
import webbrowser

import uvicorn

from web_server import api


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def main() -> None:
    port = free_port()
    config = uvicorn.Config(api, host="127.0.0.1", port=port, log_level="warning")
    server = uvicorn.Server(config)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    url = f"http://127.0.0.1:{port}"
    for _ in range(100):
        if server.started:
            break
        if not thread.is_alive():
            raise RuntimeError("Anime Image Studio server could not start.")
        time.sleep(0.2)
    webbrowser.open(url)
    try:
        while thread.is_alive():
            time.sleep(1)
    except KeyboardInterrupt:
        server.should_exit = True
        thread.join(timeout=10)


if __name__ == "__main__":
    main()
