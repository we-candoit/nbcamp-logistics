-- 0) 환경변수 → psql 변수
\getenv user_schema       DB_USER_SCHEMA
\getenv user_username     DB_USER_SCHEMA_USERNAME
\getenv user_password     DB_USER_SCHEMA_PASSWORD
\getenv hub_schema        DB_HUB_SCHEMA
\getenv hub_username      DB_HUB_SCHEMA_USERNAME
\getenv hub_password      DB_HUB_SCHEMA_PASSWORD
\getenv company_schema    DB_COMPANY_SCHEMA
\getenv company_username  DB_COMPANY_SCHEMA_USERNAME
\getenv company_password  DB_COMPANY_SCHEMA_PASSWORD
\getenv order_schema      DB_ORDER_SCHEMA
\getenv order_username    DB_ORDER_SCHEMA_USERNAME
\getenv order_password    DB_ORDER_SCHEMA_PASSWORD
\getenv delivery_schema   DB_DELIVERY_SCHEMA
\getenv delivery_username DB_DELIVERY_SCHEMA_USERNAME
\getenv delivery_password DB_DELIVERY_SCHEMA_PASSWORD

-- 1) 서비스별 계정
CREATE USER :"user_username"     WITH PASSWORD :'user_password';
CREATE USER :"hub_username"      WITH PASSWORD :'hub_password';
CREATE USER :"company_username"  WITH PASSWORD :'company_password';
CREATE USER :"order_username"    WITH PASSWORD :'order_password';
CREATE USER :"delivery_username" WITH PASSWORD :'delivery_password';

-- 2) 스키마 생성 + 소유자 지정
CREATE SCHEMA IF NOT EXISTS :"user_schema"     AUTHORIZATION :"user_username";
CREATE SCHEMA IF NOT EXISTS :"hub_schema"      AUTHORIZATION :"hub_username";
CREATE SCHEMA IF NOT EXISTS :"company_schema"  AUTHORIZATION :"company_username";
CREATE SCHEMA IF NOT EXISTS :"order_schema"    AUTHORIZATION :"order_username";
CREATE SCHEMA IF NOT EXISTS :"delivery_schema" AUTHORIZATION :"delivery_username";

-- 3) 계정별 기본 search_path
ALTER ROLE :"user_username"     SET search_path TO :"user_schema";
ALTER ROLE :"hub_username"      SET search_path TO :"hub_schema";
ALTER ROLE :"company_username"  SET search_path TO :"company_schema";
ALTER ROLE :"order_username"    SET search_path TO :"order_schema";
ALTER ROLE :"delivery_username" SET search_path TO :"delivery_schema";

-- 4) public 스키마 잠금
REVOKE ALL ON SCHEMA public FROM PUBLIC;