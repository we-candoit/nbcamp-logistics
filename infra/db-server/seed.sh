#!/bin/sh
set -e

# db-server.env의 값 재사용
export PGHOST=db-server
export PGUSER="$POSTGRES_USER"
export PGPASSWORD="$POSTGRES_PASSWORD"
export PGDATABASE="$POSTGRES_DB"

echo "[seeder] DB 접속 대기"
until pg_isready -q; do sleep 2; done

# 각 서비스가 JPA로 만드는 테이블 (실제 스키마/테이블명으로 수정)
TABLES="
user_schema.p_users
hub_schema.p_hubs
company_schema.p_companies
order_schema.p_orders
delivery_schema.p_deliveries
"

for t in $TABLES; do
  echo "[seeder] $t 생성 대기"
  until [ -n "$(psql -tAc "SELECT to_regclass('$t')")" ]; do
    sleep 3
  done
done

# 같은 서비스의 나머지 테이블 생성 여유
sleep 5

echo "[seeder] mock_data.sql 실행"
psql -v ON_ERROR_STOP=1 -f /seed/mock_data.sql
echo "[seeder] 완료"