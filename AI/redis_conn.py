"""Redis 연결 팩토리 — 워커(main.py)·DLQ 도구·실험 스크립트가 같은 함수로 붙는다.

REDIS_MODE 환경 변수 하나로 토폴로지를 고른다. 세 모드 모두 Streams 명령은 동일하게 동작한다
(스트림은 키 하나 = 슬롯 하나라 클러스터에서도 XREADGROUP/XAUTOCLAIM이 그대로 된다).

  standalone (기본)  REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
  sentinel           REDIS_SENTINELS="h1:26379,h2:26379,h3:26379", REDIS_MASTER_NAME(기본 mymaster),
                     REDIS_PASSWORD(데이터 노드), REDIS_SENTINEL_PASSWORD(센티널 자체, 없으면 생략)
  cluster            REDIS_CLUSTER_NODES="h1:7001,h2:7002" (하나만 줘도 나머지는 CLUSTER SLOTS로 찾는다), REDIS_PASSWORD

페일오버 중 각 모드가 던지는 예외가 다르다는 점이 실험 포인트다:
  standalone → ConnectionError (마스터 주소가 안 바뀌니 영영 못 붙는다)
  sentinel   → 승격 전엔 ConnectionError/MasterNotFoundError, 승격 후엔 센티널에 다시 물어 새 마스터로 간다
  cluster    → ClusterDownError(FAIL~승격 사이), 승격 후 MOVED를 받으면 슬롯 맵을 갱신해 따라간다
"""
import os

import redis
from redis.cluster import RedisCluster
from redis.sentinel import Sentinel


def _hostports(csv):
    return [(h, int(p)) for h, p in (x.strip().rsplit(":", 1) for x in csv.split(",") if x.strip())]


def make_redis(socket_timeout=15, decode_responses=False):
    mode = os.getenv("REDIS_MODE", "standalone").lower()
    password = os.getenv("REDIS_PASSWORD") or None
    common = dict(password=password, socket_timeout=socket_timeout, socket_connect_timeout=5,
                  decode_responses=decode_responses)

    if mode == "sentinel":
        sentinel_pw = os.getenv("REDIS_SENTINEL_PASSWORD") or None
        s = Sentinel(_hostports(os.environ["REDIS_SENTINELS"]),
                     sentinel_kwargs={"password": sentinel_pw, "socket_timeout": 2},
                     **common)
        # master_for는 매 연결마다 센티널에 "지금 마스터 누구냐"를 묻는 커넥션 풀을 돌려준다.
        # 페일오버로 마스터가 바뀌면 기존 연결은 끊기고(ConnectionError), 다음 명령에서 새 마스터로 다시 붙는다.
        return s.master_for(os.getenv("REDIS_MASTER_NAME", "mymaster"))

    if mode == "cluster":
        nodes = _hostports(os.environ["REDIS_CLUSTER_NODES"])
        host, port = nodes[0]
        # 시작 노드 하나로 CLUSTER SLOTS를 읽어 전체 맵을 만든다. MOVED를 받으면 맵을 다시 읽는다.
        return RedisCluster(host=host, port=port, **common)

    return redis.Redis(host=os.getenv("REDIS_HOST", "127.0.0.1"), port=int(os.getenv("REDIS_PORT", 6379)), **common)
