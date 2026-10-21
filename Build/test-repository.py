#!/usr/bin/env python3
"""Exercise Repository startup and file-store get/put/remove over real TCP.

Requires Python 3, java/javac and Commons Codec 1.9. Example:
  python3 Build/test-repository.py --codec /path/to/commons-codec-1.9.jar

Builds and runs in a temporary directory; no MongoDB or source-tree changes.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codec", type=Path, required=True)
    parser.add_argument("--source", type=Path, help="Read sources from another Elko checkout")
    args = parser.parse_args()
    codec = args.codec.resolve(strict=True)
    root = args.source.resolve() if args.source else Path(__file__).resolve().parent.parent
    with tempfile.TemporaryDirectory(prefix="elko-repository-test-") as temp:
        working = Path(temp)
        source = working / "Source"
        shutil.copytree(root / "ServerCore/java", source)
        version = source / "org/elkoserver/foundation/server/BuildVersion.java"
        version.write_text(Path(str(version) + ".tmpl").read_text().replace(
            "@BuildVersion@", "Repository initialization regression"))
        classes = working / "Classes"
        classes.mkdir()
        entries = ["foundation/boot/Boot", "server/repository/RepositoryBoot",
                   "objdb/store/filestore/FileObjectStore"]
        subprocess.run(["javac", "-d", str(classes), "-cp", str(codec),
                        "-sourcepath", str(source)] + [str(source / (
                            "org/elkoserver/" + entry + ".java"))
                            for entry in entries], check=True, timeout=60,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        data = working / "Data"
        data.mkdir()
        seed = {"ref": "probe", "value": 41}
        (data / "probe.json").write_text(json.dumps(seed))
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        command = ["java", "-cp", os.pathsep.join([str(classes), str(codec)]),
                   "org.elkoserver.foundation.boot.Boot", "tracelog_name=-",
                   "trace_default=error", "conf.listen.protocol=tcp",
                   "conf.listen.host=127.0.0.1:" + str(port),
                   "conf.listen.bind=127.0.0.1:" + str(port),
                   "conf.listen.allow=any", "conf.listen.auth.mode=open",
                   "conf.comm.jsonstrictness=true", "conf.msgdiagnostics=true",
                   "conf.rep.odb=" + str(data),
                   "org.elkoserver.server.repository.RepositoryBoot"]
        with (working / "server.log").open("w+") as log:
            process = subprocess.Popen(command, cwd=working, stdout=log,
                                       stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 10
                while True:
                    try:
                        connection = socket.create_connection(("127.0.0.1", port), 2)
                        break
                    except OSError:
                        if process.poll() is not None or time.monotonic() > deadline:
                            raise RuntimeError("Repository did not start")
                        time.sleep(0.02)
                with connection:
                    connection.settimeout(5)
                    def send(message):
                        connection.sendall((json.dumps(message) + "\r\n\r\n").encode())
                    send({"to": "rep", "op": "auth", "auth": {"type": "auth", "mode": "open"}})
                    with connection.makefile("r", encoding="utf-8") as incoming:
                        sequence = 0
                        def request(op, what):
                            nonlocal sequence
                            sequence += 1
                            tag = str(sequence)
                            send({"to": "rep", "op": op, "tag": tag, "what": what})
                            while True:
                                line = incoming.readline()
                                if not line:
                                    raise RuntimeError("Repository disconnected")
                                if not line.strip():
                                    continue
                                reply = json.loads(line)
                                if reply.get("op") == "debug":
                                    raise AssertionError(reply)
                                if reply.get("tag") == tag:
                                    assert reply["op"] == op, reply
                                    return reply["results"]
                        refs = [{"type": "reqi", "ref": "probe"}]
                        result = request("get", refs)
                        assert json.loads(result[0]["obj"]) == seed, result
                        updated = {"ref": "probe", "value": 42}
                        result = request("put", [{"type": "puti", "ref": "probe",
                                                   "obj": json.dumps(updated)}])
                        assert not result[0].get("failure"), result
                        result = request("get", refs)
                        assert json.loads(result[0]["obj"]) == updated, result
                        assert json.loads((data / "probe.json").read_text()) == updated
                        result = request("remove", refs)
                        assert not result[0].get("failure"), result
                        result = request("get", refs)
                        assert result[0]["failure"] == "not found", result
                        assert not (data / "probe.json").exists()
                print("PASS: Repository startup, get, persisted put, remove and missing-object result")
            except Exception:
                log.flush()
                log.seek(0)
                print(log.read())
                raise
            finally:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    main()
