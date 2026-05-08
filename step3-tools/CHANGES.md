# CHANGES — step3-tools (vs step2-memory)

## 추가

- `src/main/java/com/kosta/agent/tool/OrderTools.java`
  - `@Tool` 4종: `findCustomer`, `getRecentOrders`, `getOrderStatus`, `cancelOrder`
  - 권한 검증을 위해 `callerCustomerId` 파라미터 명시
  - `cancelOrder`에 `@Transactional` 부여

## 변경

- `src/main/java/com/kosta/agent/config/AgentConfig.java`
  - `OrderTools` 주입 + `.defaultTools(orderTools)` 호출
  - 시스템 프롬프트에 "도구 호출 시 callerCustomerId 전달" 지침 추가

## 변경 없음

- AgentController 시그니처
- 도메인 / Repository / DataSeeder / OrderController
- application.yml (vectorstore 설정은 step4에서 추가)
