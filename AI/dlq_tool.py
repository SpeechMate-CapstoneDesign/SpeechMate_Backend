"""DLQ(nonverbal-analysis-dlq) 운영 도구.

DLQ는 자동 재투입 창구가 아니라 "왜 죽었는지" 증거 보관함이다. 개별 재실행은 사용자 재요청(FAILED → 재요청 가능)으로 충분하고,
이 도구는 워커 버그를 고쳐 배포한 뒤 그 버그로 쌓인 작업을 사람이 확인하고 일괄 재투입할 때 쓴다.

usage:
  python dlq_tool.py list                 # id / 사유 / 원본 메시지 id / payload
  python dlq_tool.py replay <id|all>      # 원본 스트림에 새 메시지로 재투입(전달 횟수 0부터) 후 DLQ에서 제거
  python dlq_tool.py purge  <id|all>      # 재투입 없이 제거
env: REDIS_MODE 등 redis_conn.py와 동일 (워커와 같은 값)
"""
import os
import sys

import redis

STREAM_KEY = "nonverbal-analysis-jobs"
DLQ_STREAM_KEY = "nonverbal-analysis-dlq"

from redis_conn import make_redis  # noqa: E402

r = make_redis(decode_responses=True)


def select(sel):
    return r.xrange(DLQ_STREAM_KEY) if sel == "all" else r.xrange(DLQ_STREAM_KEY, sel, sel)


def main(argv):
    cmd, sel = (argv + ["all"])[:2]
    if cmd == "list":
        for id_, e in r.xrange(DLQ_STREAM_KEY):
            print(f"{id_}  reason={e.get('reason')}  origin={e.get('origin_id')}  job={e.get('job')}")
        print(f"total {r.xlen(DLQ_STREAM_KEY)}")
    elif cmd == "replay":
        for id_, e in select(sel):
            new_id = r.xadd(STREAM_KEY, {"job": e["job"]})
            r.xdel(DLQ_STREAM_KEY, id_)
            print(f"replayed {id_} -> {STREAM_KEY}/{new_id}")
    elif cmd == "purge":
        for id_, _ in select(sel):
            r.xdel(DLQ_STREAM_KEY, id_)
            print(f"purged {id_}")
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
