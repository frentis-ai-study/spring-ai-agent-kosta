#!/usr/bin/env bash
# step6 사용 매뉴얼용 UI 스크린샷 생성 스크립트.
#
# 사전 조건:
#   1) 앱 실행 중:  (step6-prod에서) export OPENAI_API_KEY=...; ./gradlew bootRun
#   2) RAG 인덱싱:  curl -X POST http://localhost:8080/api/index
#   3) agent-browser 설치 (brew install agent-browser) + 브라우저 표시 가능한 환경
#      (헤드리스 CI에서는 캡처가 불안정하므로 디스플레이가 있는 로컬에서 실행 권장)
#
# 결과: docs/screenshots/usage/*.png
set -euo pipefail

SS="$(cd "$(dirname "$0")/.." && pwd)/docs/screenshots/usage"
mkdir -p "$SS"
U="http://localhost:8080"

agent-browser open "$U"
agent-browser wait 2500
agent-browser screenshot "$SS/01-initial.png"

cap() {  # $1=파일명(확장자 제외)  $2=입력 메시지
  agent-browser click "#reset"; agent-browser wait 800
  agent-browser fill "#message" "$2"
  agent-browser click "#send"
  agent-browser wait 12000               # LLM 응답 렌더 대기
  agent-browser scroll down 600; agent-browser wait 600
  agent-browser screenshot "$SS/$1.png"
  echo "captured $1"
}

cap "02-inventory-out" "USB-C 허브 재고 있나요?"
cap "03-inventory-ok"  "기계식 키보드 재고랑 가격 알려줘"
cap "04-shipment"      "2번 주문 배송 어디까지 왔나요?"
cap "05-refund"        "1번 주문을 단순 변심으로 반품 접수해줘"
cap "06-rag-policy"    "단순 변심 반품 시 배송비는 누가 부담하나요?"
cap "07-safeguard"     "제 주민등록번호는 901010-1234567 입니다."

agent-browser close
echo "완료: $SS"
