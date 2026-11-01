"""CAP/1.0 end-to-end test. Start the server, then run this file."""
import os
import socket

HOST = os.getenv("TEST_HOST", "127.0.0.1")
PORT = int(os.getenv("SERVER_PORT", "5000"))


def encode(*fields):
    def escape(value):
        return str(value).replace("\\", "\\\\").replace(",", "\\,").replace("\n", "\\n").replace("\r", "\\r")
    return ",".join(escape(field) for field in fields)


def decode(frame):
    fields, field, escaped = [], [], False
    translations = {"\\": "\\", ",": ",", "n": "\n", "r": "\r"}
    for character in frame:
        if escaped:
            if character not in translations:
                raise ValueError("invalid escape")
            field.append(translations[character])
            escaped = False
        elif character == "\\":
            escaped = True
        elif character == ",":
            fields.append("".join(field)); field.clear()
        else:
            field.append(character)
    if escaped:
        raise ValueError("unfinished escape")
    fields.append("".join(field))
    return fields


class Client:
    def __init__(self, name):
        self.socket = socket.create_connection((HOST, PORT), timeout=3)
        self.file = self.socket.makefile("rwb")
        self.receive("server-ready")
        self.send("set-name", name)
        self.receive("name-accepted")

    def send(self, *fields):
        self.file.write((encode(*fields) + "\n").encode("utf-8"))
        self.file.flush()

    def receive(self, expected):
        for _ in range(12):
            line = self.file.readline()
            assert line, f"connection ended while waiting for {expected}"
            fields = decode(line.decode("utf-8").rstrip("\r\n"))
            if fields[0] == expected:
                return fields
        raise AssertionError(f"did not receive {expected}")

    def close(self):
        self.file.close(); self.socket.close()


def main():
    alice, bob, viewer = Client("alice"), Client("bob"), Client("viewer")
    alice.send("create-room")
    room_id = alice.receive("your-created-room")[1]
    alice.receive("room-state")

    bob.send("join-room", room_id, "player")
    assert bob.receive("join-room-success")[2] == "O"
    bob.receive("room-state"); alice.receive("room-state")

    viewer.send("join-room", room_id, "spectator")
    assert viewer.receive("join-room-success")[2] == "SPECTATOR"
    viewer.receive("room-state"); alice.receive("room-state"); bob.receive("room-state")

    moves = [(alice, 0, 0), (bob, 1, 0), (alice, 0, 1), (bob, 1, 1),
             (alice, 0, 2), (bob, 1, 2), (alice, 0, 3), (bob, 1, 3), (alice, 0, 4)]
    for client, row, col in moves:
        client.send("caro", row, col)
        client.receive("move-accepted")
        move = viewer.receive("caro")
        alice.receive("caro"); bob.receive("caro")
    assert move[4] == "WIN"
    assert viewer.receive("game-over")[2] == "alice"
    alice.receive("game-over"); bob.receive("game-over")

    viewer.send("chat", "Xin chao, co dau phay va tieng Viet")
    viewer.receive("chat-accepted")
    chat = alice.receive("chat")
    assert chat[2] == "Xin chao, co dau phay va tieng Viet"
    bob.receive("chat"); viewer.receive("chat")

    viewer.file.write(b"bad\\qescape\n"); viewer.file.flush()
    assert viewer.receive("error")[1] == "400"
    viewer.send("ping"); viewer.receive("pong")

    for client in (alice, bob, viewer): client.close()
    print("PASS: CAP/1.0 plain text, 3 clients, 9 moves, win, escaped comma chat, malformed frame recovery")


if __name__ == "__main__":
    main()
