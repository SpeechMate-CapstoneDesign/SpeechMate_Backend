#!/usr/bin/env bash
# STT 접수 요청의 "요청 점유 시간"을 잰다. 전/후 코드 양쪽에서 같은 명령으로 돌린다.
#   전(동기 폴링): 응답이 STT가 끝날 때 온다 → 응답 시간 = 점유 시간
#   후(접수형):    응답이 IN_PROGRESS로 바로 온다 → 응답 시간 = 점유 시간, 완료는 따로 폴링
# 사용: BASE_URL=https://api.example.com TOKEN=eyJ... ./stt-latency.sh 12 13 14 15 16
#   인자: 아직 STT 안 한(또는 rawTranscription이 없는) speechId 목록. 동시에 쏜다.
# 함께 볼 것(Grafana/Prometheus, 같은 시간대):
#   tomcat_threads_busy_threads                                         ← 전: N개가 STT 내내 busy / 후: 바로 내려감
#   http_server_requests_seconds_max{uri="/api/speech/rtzrstt/{speechId}"} ← 전: 수십 초 / 후: ms
set -euo pipefail
: "${BASE_URL:?}" "${TOKEN:?}"
[ $# -ge 1 ] || { echo "speechId를 하나 이상 주세요"; exit 1; }

OUT=$(mktemp -d)
echo "[$(date +%T)] 동시 접수 $# 건 → $BASE_URL"
for id in "$@"; do
  ( t0=$(date +%s%N)
    code=$(curl -s -o "$OUT/$id.json" -w '%{http_code}' -X POST "$BASE_URL/api/speech/rtzrstt/$id" -H "Authorization: Bearer $TOKEN")
    echo "$id $(( ($(date +%s%N)-t0)/1000000 )) $code $(grep -o '"sttStatus":"[A-Z_]*"' "$OUT/$id.json" | cut -d'"' -f4)" > "$OUT/$id.row" ) &
done
wait
echo "speechId  응답(ms)  HTTP  sttStatus(후 코드만)"
cat "$OUT"/*.row | sort -n | awk '{printf "%-9s %-9s %-5s %s\n",$1,$2,$3,$4}'

# 후 코드라면 완료까지 걸린 시간도 잰다 (사용자가 체감하는 시간. 전/후가 같아야 정상)
if grep -q IN_PROGRESS "$OUT"/*.row 2>/dev/null; then
  echo; echo "완료 폴링(5초 간격, 최대 10분)..."
  t0=$(date +%s)
  for id in "$@"; do
    for _ in $(seq 1 120); do
      st=$(curl -s -X POST "$BASE_URL/api/speech/rtzrstt/$id" -H "Authorization: Bearer $TOKEN" | grep -o '"sttStatus":"[A-Z_]*"' | cut -d'"' -f4)
      [ "$st" = COMPLETED ] || [ "$st" = FAILED ] && { echo "$id $st +$(( $(date +%s)-t0 ))s"; break; }
      sleep 5
    done
  done
fi
echo "[$(date +%T)] 끝. 원본: $OUT"
