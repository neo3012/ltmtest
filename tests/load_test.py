"""Concurrency check for the CAP/1.0 plain-text protocol."""
import os
import socket
import threading

HOST = os.getenv("TEST_HOST", "127.0.0.1")
PORT = int(os.getenv("SERVER_PORT", "5000"))
N = int(os.getenv("N_CLIENTS", "50"))
results, lock = [], threading.Lock()


def receive(file, expected):
    for _ in range(5):
        frame = file.readline().decode().strip()
        if frame.split(",", 1)[0] == expected:
            return
    raise RuntimeError(f"missing {expected}")


def worker(index):
    error = None
    try:
        with socket.create_connection((HOST, PORT), timeout=5) as connection:
            file = connection.makefile("rwb")
            receive(file, "server-ready")
            file.write(f"set-name,load_{index}\n".encode()); file.flush()
            receive(file, "name-accepted")
            file.write(b"ping\n"); file.flush()
            receive(file, "pong")
    except Exception as exception:
        error = str(exception)
    with lock:
        results.append(error)


threads = [threading.Thread(target=worker, args=(index,)) for index in range(N)]
for thread in threads: thread.start()
for thread in threads: thread.join()
failures = [result for result in results if result]
assert not failures, failures
print(f"PASS: {N}/{N} concurrent CAP/1.0 clients completed set-name and ping")
