# step0-base — 도메인 + REST CRUD (AI 없음)

본 단계는 Spring AI를 도입하기 전, 도메인 모델과 REST 엔드포인트를 정상 동작시키는 출발점입니다.

## 목표

- Spring Boot 3.5 + JPA + PostgreSQL 환경 구성을 검증한다.
- `Customer`, `Order` 엔티티와 `DataSeeder`를 통해 데모 데이터를 적재한다.
- AI 호출 없이 순수 REST API로 데이터를 조회한다.

## 새 파일

| 종류 | 경로 | 설명 |
|------|------|------|
| 추가 | `domain/Customer.java` | 고객 엔티티 |
| 추가 | `domain/Order.java` | 주문 엔티티 (`cancel()` 도메인 메서드) |
| 추가 | `domain/CustomerRepository.java` | `findByEmail` |
| 추가 | `domain/OrderRepository.java` | `findTop10ByCustomerIdOrderByCreatedAtDesc` |
| 추가 | `domain/DataSeeder.java` | 부팅 시 1회 데모 데이터 적재 |
| 추가 | `web/OrderController.java` | REST 조회 엔드포인트 |
| 추가 | `docker-compose.yml` | PostgreSQL+pgvector 컨테이너 |

## 실행

```bash
docker compose up -d
./gradlew bootRun
```

## 5가지 체크포인트

1. `curl http://localhost:8080/actuator/health` 가 `{"status":"UP"}` 응답
2. `curl http://localhost:8080/api/customers` 응답에 alice, bob 2건이 조회됨
3. `curl http://localhost:8080/api/orders` 응답에 4건의 주문이 조회됨
4. `curl http://localhost:8080/api/customers/1/orders` 로 alice 주문 3건 조회
5. 컨테이너 재기동 후에도 데이터가 유지되어 `DataSeeder`가 중복 적재하지 않음

## 다음 단계

`step1-chat` 으로 이동하여 ChatClient를 도입합니다.
