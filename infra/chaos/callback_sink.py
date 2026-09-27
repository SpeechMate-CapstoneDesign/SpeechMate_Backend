"""Spring 콜백 수신부 대역. 워커가 보내는 POST를 받아 /data/callbacks.log에 한 줄씩 남긴다.
형식: <epoch> <speechId> <COMPLETED|FAILED>
"""
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

LOG = "/data/callbacks.log"


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0)) or b"{}"))
        status = "COMPLETED" if body.get("response") is not None else "FAILED"
        line = f"{time.time():.3f} {body.get('speechId')} {status}\n"
        with open(LOG, "a") as f:
            f.write(line)
        print("callback:", line.strip(), flush=True)
        self.send_response(200)
        self.end_headers()

    def log_message(self, *a):  # 기본 액세스 로그 끄기
        pass


if __name__ == "__main__":
    open(LOG, "a").close()
    HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
