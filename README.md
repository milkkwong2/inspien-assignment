# inspien-assignment

인스피언 코딩테스트 과제: 서비스(REST API) 연계 및 데이터 변환 처리.
요구사항 정리는 [docs/requirements.md](docs/requirements.md)를 참고한다.

- Java 25, Spring Boot 4.1, Gradle (Wrapper 포함)

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
| Oracle Free 23 | `localhost:1521/FREEPDB1`, 계정 `applicant` | 테이블은 다른 스키마 소유, 앱 계정은 SELECT·INSERT·UPDATE만 (DELETE·DDL 불가). 다른 지원자의 행이 들어 있는 공용 테이블 |
| FTP (vsftpd) | `localhost:2121`, 계정 `recruit`, 경로 `Recruit/2026` | 목록 조회와 신규 업로드만. 덮어쓰기·다운로드·삭제·이름 변경 불가. 다른 지원자의 파일이 있는 공용 폴더 |

- 계정과 비밀번호는 [compose.yaml](compose.yaml)과 [local/oracle/init](local/oracle/init)에 있다. 로컬 전용 값이라 저장소에 둔다.
- 다른 지원자 행: `OTHER-APPLICANT-1`(내 번호와 겹치는 `A001`~), `OTHER-APPLICANT-2`(`RECEIVED` 150건, 스케줄러 한 주기 처리 건수보다 많음).

## 3. 테스트

```bash
./gradlew test
```

실제 과제 API는 호출하지 않는다. 가짜 데이터와 로컬 HTTP 서버로 검증한다.
