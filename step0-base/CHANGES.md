# CHANGES — step0-base

본 단계는 출발점이므로 비교 대상 이전 step이 없습니다.

## 포함 구성

- Spring Boot 3.5.0 (web, data-jpa, actuator)
- PostgreSQL JDBC 드라이버 (런타임)
- pgvector 확장 (docker-compose, step4부터 사용)
- Spring AI 의존성 일체 없음

## 도메인 스키마

- `customers(id, email UNIQUE, name, tier)`
- `orders(id, customer_id, product_name, quantity, total_amount, status, created_at)`
- DataSeeder가 alice/bob 고객 및 주문 4건을 1회 적재
