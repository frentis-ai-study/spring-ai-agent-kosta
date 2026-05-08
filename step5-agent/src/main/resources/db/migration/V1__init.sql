-- 참고용 초기 스키마 (JPA ddl-auto=update를 사용하므로 자동 생성된다)
-- Flyway/Liquibase로 전환할 때 시작점으로 활용한다.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS customers (
    id      BIGSERIAL PRIMARY KEY,
    email   VARCHAR(100) UNIQUE NOT NULL,
    name    VARCHAR(50)         NOT NULL,
    tier    VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS orders (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT          NOT NULL,
    product_name  VARCHAR(200)    NOT NULL,
    quantity      INTEGER         NOT NULL,
    total_amount  NUMERIC(12, 2)  NOT NULL,
    status        VARCHAR(20)     NOT NULL,
    created_at    TIMESTAMP       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);

-- vector_store 테이블, SPRING_AI_CHAT_MEMORY 테이블은
-- Spring AI 자동 설정(initialize-schema=true)이 부팅 시 생성한다.
