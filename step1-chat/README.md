# step1-chat — ChatClient 도입 (단순 챗봇)

step0의 도메인 위에 Spring AI ChatClient를 추가하여 가장 단순한 챗봇 엔드포인트를 만듭니다.

## 목표

- `spring-ai-starter-model-openai` 의존성을 추가한다.
- `ChatClient.Builder`로 `defaultSystem` 시스템 프롬프트가 적용된 챗봇 빈을 등록한다.
- `POST /api/agent`로 한국어 질문을 던져 응답을 받는다.

## 추가/변경 파일

| 종류 | 경로 | 설명 |
|------|------|------|
| 추가 | `config/AgentConfig.java` | ChatClient 빈 정의 |
| 추가 | `web/AgentController.java` | `/api/agent` POST 엔드포인트 |
| 변경 | `build.gradle.kts` | `spring-ai-starter-model-openai` 추가 (Spring AI 1.1.5, Maven Central) |
| 변경 | `application.yml` | `spring.ai.openai.*` 추가 |

## 사전 준비

- Java 21 + `OPENAI_API_KEY` 환경변수만 있으면 됩니다.
- DB는 H2 file (`./data/agentdb`)로 자동 생성됩니다.

## 실행

```bash
export OPENAI_API_KEY=sk-...
./gradlew bootRun
```

## 5가지 체크포인트

1. `OPENAI_API_KEY` 미설정 시 부팅 단계에서 명시적 경고가 보인다 (또는 첫 호출에서 401)
2. `curl -X POST localhost:8080/api/agent -H "Content-Type: application/json" -d '{"message":"안녕하세요"}'`로 한국어 응답
3. 시스템 프롬프트에 따라 응답이 격식체로 시작한다
4. step0의 `/api/customers` 등 REST 엔드포인트는 그대로 동작한다
5. application.yml의 `temperature: 0.0`을 0.8로 바꾸면 같은 질문에도 답이 다양해진다

## 한계

- 같은 conversation으로 두 번 호출해도 이전 발화를 기억하지 못한다 (step2에서 해결)
- 도구 호출 불가 (step3에서 해결)

## 운영 환경 전환 안내

`application.yml`의 `datasource`를 PostgreSQL로 교체하면 동일한 코드가 그대로 동작합니다. 이는 Spring의 PSA(Portable Service Abstraction) 가치 그 자체입니다.
