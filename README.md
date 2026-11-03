# inspien-assignment

인스피언 코딩테스트 과제: 서비스(REST API) 연계 및 데이터 변환 처리.

- 요구사항: [docs/requirements.md](docs/requirements.md)
- 설계서: [docs/design.md](docs/design.md), 결정 기록: [docs/decisions.md](docs/decisions.md)
- Java 25, Spring Boot 4.1, Spring Data JPA + QueryDSL, H2(처리 이력), Gradle (Wrapper 포함)

| 인터페이스 | 방식 | 소스 → 타깃 |
|---|---|---|
| IF-01 주문 수신 | `POST /orders` (XML 본문) | 주문 XML → 쇼핑몰 DB `ORDER_TB` |
| IF-02 영수증 전송 | 스케줄러 30초 | `ORDER_TB`(`STATUS=N`) → FTP 영수증 CSV |
| IF-03 배송 연계 | 스케줄러 30초 | `ORDER_TB`(`STATUS=N`) → 운송회사 DB `SHIPMENT_TB` |
| 마감 | 스케줄러 30초 | 영수증·배송 모두 완료 → `ORDER_TB.STATUS` N→Y |
| IF-04 처리 추적 | `GET /tracking/...`, `POST /operations/...` | H2 단계 진행·이력 + `ORDER_TB.STATUS` |

`STATUS`는 연계 완료 플래그다. 수신할 때 `N`, 영수증과 배송이 모두 끝나면 `Y`다. 제공 DB의 CHECK 제약이 `N`·`Y`만 받는다. 영수증과 배송은 서로 기다리지 않고 따로 처리하며, 단계별 진행(`PENDING`·`DONE`·`FAILED`)은 H2에 둔다 (D-26).

## 실행

프로필로 접속 대상을 고른다. 프로필 없이 실행하면 지원자 키·이름이 없어 기동하지 않는다.

```bash
# 로컬 컨테이너 대상 (아래 2절)
./gradlew bootRun --args="--spring.profiles.active=local"

# 과제 제공 환경 대상 (운영으로 취급, config/ 필요)
./gradlew bootRun --args="--spring.profiles.active=provided"
```

```bash
# 주문 수신 (송신자처럼 인코딩 표시 없이 EUC-KR로)
curl -X POST http://localhost:8080/orders -H "Content-Type: application/xml" --data-binary @order.xml

# 처리 추적 (주문 목록은 진행 중(N) 주문만, 끝난 주문은 주문번호로)
curl http://localhost:8080/tracking/orders/A001
curl "http://localhost:8080/tracking/orders?receipt=FAILED"
curl "http://localhost:8080/tracking/orders?shipment=PENDING"
curl http://localhost:8080/tracking/messages/{messageId}
curl "http://localhost:8080/tracking/messages?state=FAILED"

# 운영자 재처리 (FAILED 단계를 횟수 0부터 다시)
curl -X POST http://localhost:8080/operations/orders/A001/retry
```

- 단계가 `FAILED`인 주문(재시도 5회 초과, 또는 H2에 수신 기록 없음)은 원인을 고친 뒤 재처리 API를 부르면 다음 주기부터 다시 처리한다. H2의 표시와 횟수만 지우고, 영수증 파일명과 미리 배정한 배송 번호는 그대로 둔다. `FAILED` 단계가 없으면 `409`다.
- 처리 이력 H2는 프로필별 `data/local/`, `data/provided/`에 있다. 중복 판단 정보가 들어 있으니 앱 실행 중 복사하거나 지우지 않는다.

## 1. 접속 정보 받기 (최초 1회)

DB/FTP 접속 정보는 과제 API를 호출해 받는다. Spring 애플리케이션과 분리된 일회성 도구(`bootstrap` 패키지)로 실행한다.

1. `config/bootstrap.properties.example`을 `config/bootstrap.properties`로 복사하고 값을 채운다.
2. 실행한다.

   ```bash
   ./gradlew bootstrap
   ```

3. `config/`에 아래 파일이 생긴다. 모두 Git에서 제외된다.

   | 파일 | 내용 |
   |---|---|
   | `raw-response.json` | 과제 API 원본 응답 |
   | `connection.json` | `APPLICANT_KEY`와 복호화한 접속 정보 (`ORDER_TB_CONN`, `SHIPMENT_TB_CONN`, `FTP_CONN`) |
   | `sample-order.xml` | 샘플 주문 XML (EUC-KR 원본 그대로) |

### 동작 원칙

- **다시 호출하지 않는다.** 재호출 시 `APPLICANT_KEY`가 새로 발급될 수 있으므로, `raw-response.json`이 있으면 호출을 거부한다.
  타임아웃이나 오류가 나도 자동으로 재시도하지 않는다.
- **원본 응답을 먼저 저장한다.** 복호화가 실패해도 아래 명령으로 API 호출 없이 다시 처리할 수 있다.

  ```bash
  ./gradlew bootstrap --args="--decrypt-only"
  ```

- **비밀 값은 출력하지 않는다.** 콘솔에는 마스킹한 `APPLICANT_KEY`와 접속 정보의 항목 이름만 출력한다.

### 복호화 규칙 (과제 명세)

- AES-128 / ECB / PKCS5Padding
- Key: 휴대폰 번호 문자열(하이픈 포함, 요청에 보낸 그대로)의 UTF-8 바이트 → SHA-1 → 앞 16바이트
- 암호문은 Base64 디코딩 후 복호화

## 2. 로컬 개발 환경

제공 Oracle·FTP는 지울 수도 덮어쓸 수도 없으므로 운영 환경으로 취급한다. 개발과 시행착오는 같은 조건을 재현한 로컬 컨테이너에서 한다 (설계 D-11, D-14).

```bash
docker compose up -d      # 시작 (Oracle은 첫 기동에 1~2분)
docker compose down -v    # 데이터까지 지우고 처음 상태로
```

| 서비스 | 접속 | 재현한 조건 |
|---|---|---|
| Oracle Free 23 | `localhost:1521/FREEPDB1`, 계정 `applicant` | 테이블은 다른 스키마 소유, 앱 계정은 SELECT·INSERT·UPDATE만 (DELETE·DDL 불가). 다른 지원자의 행이 들어 있는 공용 테이블. 제공 환경과 같은 CHECK 제약(`STATUS IN ('N', 'Y')`, 번호 형식)과 `CREATE_TIME`·`CREATE_DATE`(기본값 `SYSDATE`) |
| FTP (vsftpd) | `localhost:2121`, 계정 `recruit`, 경로 `Recruit/2026` | 목록 조회와 신규 업로드만. 덮어쓰기·다운로드·삭제·이름 변경 불가. 다른 지원자의 파일이 있는 공용 폴더 |

- 계정과 비밀번호는 [compose.yaml](compose.yaml)과 [local/oracle/init](local/oracle/init)에 있다. 로컬 전용 값이라 저장소에 둔다.
- 다른 지원자 행: `OTHER-APPLICANT-1`(내 번호와 겹치는 `A001`~), `OTHER-APPLICANT-2`(`N` 150건, 스케줄러 한 주기 처리 건수보다 많음).
- 초기화 스크립트는 컨테이너를 처음 만들 때만 돈다. 스크립트가 바뀌면 `docker compose down -v`로 다시 만든다.

로컬 리허설(2026-11-02, D-26 반영 후): 과제 샘플을 보내 저장 63·거부 7·경고 4, 수신 후 약 47초 안에 영수증 63개와 배송 63건, 63건 모두 `STATUS=Y`(영수증·배송 `DONE`)를 확인했다. `CREATE_TIME`·`CREATE_DATE`는 DB 기본값으로 채워졌고, 다른 지원자의 행은 바뀌지 않았다.

## 3. 테스트

```bash
./gradlew test
```

Docker가 필요하다. 통합 테스트는 로컬 환경과 같은 Oracle·vsftpd 이미지를 Testcontainers로 띄운다. 제공 환경과 과제 API에는 접속하지 않는다.

| 테스트 | 확인하는 것 |
|---|---|
| `ArchitectureTest` | 패키지 의존 규칙, 순환 금지, Oracle 어댑터의 네이티브 SQL 금지 (D-21) |
| `verification` 패키지 | `@TenantId`와 복합 PK, Oracle 오류 분류 (D-25). CHECK 제약 위반, DB 일시 정지로 응답 없음·커밋 중 타임아웃 재현 |
| `ReceiveOrderIntegrationTest` | 재전송, 1분 창, 장애 뒤 이어서 처리, 결과 불명, PK 충돌, 동시 재전송 (D-15·16·24) |
| `ReceiptFlowIntegrationTest`, `ShipmentFlowIntegrationTest` | 연결 실패와 건별 실패 구분, 백오프와 5회째 `FAILED`, 중단 복구, 늦은 커밋 (D-03·10·22) |
| `CloseFlowIntegrationTest` | 영수증·배송 독립 처리, FTP 장애에도 배송 진행, 두 단계 모두 `DONE`일 때만 `Y` (D-26) |
| `OperationsIntegrationTest` | 재처리 API: 조건부 해제, 배송 번호 보존, `409`·`404` (D-26) |
| `HistorySchemaMigrationTest` | 기존 H2 파일에 새 컬럼 추가와 기존 행 채우기 (D-27) |
| `FtpReceiptUploaderTest` | 한글 파일명, 바이너리 크기, 덮어쓰기 거부 |
| `ProvidedSampleTest` | 과제 샘플로 저장 63·거부 7·경고 4 (`config/sample-order.xml`이 있을 때만) |
