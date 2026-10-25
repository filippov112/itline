#!/usr/bin/env bash
# Smoke-проверки этапа 2 через curl.
# Требует: приложение на localhost:8080, jq (опционально, но рекомендуется).
set -u

BASE=${BASE:-http://localhost:8080}
API=$BASE/api/records
PASS=0
FAIL=0

# check <имя> <ожидаемый_статус> <curl-аргументы...>
check() {
    local name=$1 expected=$2; shift 2
    local code
    code=$(curl -s -o /tmp/smoke_body -w "%{http_code}" "$@")
    if [ "$code" = "$expected" ]; then
        echo "PASS  $name  ($code)"
        PASS=$((PASS+1))
    else
        echo "FAIL  $name  expected $expected, got $code"
        echo "      body: $(head -c 300 /tmp/smoke_body)"
        FAIL=$((FAIL+1))
    fi
}

echo "== health =="
# Сервер жив | Ожидание: health → 200, тело {"status":"UP"}
check "GET /actuator/health" 200 "$BASE/actuator/health"
check "GET /itline" 200 "$BASE/itline"

echo "== create =="
# Создание (POST) — успех
check "POST valid"            201 -X POST "$API" -H "Content-Type: application/json" \
    -d '{"active":true,"icon":"icon1","name":"Smoke 1","description":"d"}'
check "POST empty name"       400 -X POST "$API" -H "Content-Type: application/json" \
    -d '{"active":true,"icon":"icon1","name":"","description":"d"}'
check "POST bad icon"         400 -X POST "$API" -H "Content-Type: application/json" \
    -d '{"active":true,"icon":"icon99","name":"x","description":"d"}'
check "POST missing icon"     400 -X POST "$API" -H "Content-Type: application/json" \
    -d '{"active":true,"name":"x","description":"d"}'
check "POST wrong content"    415 -X POST "$API" -H "Content-Type: text/plain" -d 'x'

echo "== read =="
# достаём id созданной записи (если есть jq)
ID=$(curl -s "$API" | jq -r '.[-1].id' 2>/dev/null || echo "")
if [ -n "$ID" ] && [ "$ID" != "null" ]; then
    check "GET by id"         200 "$API/$ID"
    check "PUT by id"         200 -X PUT "$API/$ID" -H "Content-Type: application/json" \
        -d '{"active":false,"icon":"icon2","name":"Smoke 1 upd","description":"d2","version":0}'
    check "DELETE by id"      204 -X DELETE "$API/$ID?version=1"
    check "GET deleted -> 404" 404 "$API/$ID"
else
    echo "SKIP  read/update/delete: jq не установлен или список пуст"
fi

echo "== not found =="
check "GET unknown"           404 "$API/999999"
check "PUT unknown"           404 -X PUT "$API/999999" -H "Content-Type: application/json" \
    -d '{"active":true,"icon":"icon1","name":"x","description":"d", "version":0}'
check "DELETE unknown"        404 -X DELETE "$API/999999?version=0"

echo "== type mismatch =="
check "GET non-numeric id"    400 "$API/abc"

echo
echo "Итого: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]