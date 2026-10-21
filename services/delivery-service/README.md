# Delivery service

Java 21 기반의 독립 Spring Boot 프로젝트입니다.  
Spring Boot / Cloud / Gradle 버전은 기존 주문 서비스와 동일하게 맞췄습니다.

## 의존성

- Spring MVC: REST API
- Spring Data JPA + PostgreSQL: 엔티티와 데이터 접근
- Validation: 요청 DTO의 `@NotNull`, `@NotBlank` 등 검증 (`@Valid`와 함께 사용)
- Eureka Client: 서비스 등록
- OpenFeign: 다른 서비스의 API 호출 (`@EnableFeignClients` 설정 포함)
- Lombok: 반복 코드 생성
- Spring Boot Test + H2: 외부 DB와 Eureka 없이 컨텍스트 테스트

## 패키지

기준 패키지는 `logistics.delivery`입니다.   
컨트롤러, 서비스, 엔티티, 저장소, DTO는 이 패키지 아래에 추가하면 기본 컴포넌트 및 JPA 스캔에 포함됩니다.

## 테스트 및 빌드
Git Bash에서 이 디렉터리를 기준으로 실행합니다.

```bash
./gradlew test bootJar
```
PowerShell에서는 `./gradlew.bat test bootJar`를 사용합니다.  
Gradle 실행용 JDK도 21로 설정합니다.

## 실행 환경

필수 환경변수:

```text
DB_SERVER_NAME
DB_SERVER_PORT
POSTGRES_DB
DB_SCHEMA
DB_USERNAME
DB_PASSWORD
DISCOVERY_SERVER_NAME
DISCOVERY_SERVER_PORT
```

`DELIVERY_SERVICE_PORT`의 기본값은 `8080`입니다.  
IntelliJ에서는 Gradle 프로젝트로 열고 `DeliveryApplication` 실행 설정의 환경변수에 위 값을 지정합니다. 
Spring Boot는 별도의 설정 없이 `.env` 파일을 자동으로 읽지 않습니다. 

Docker 실행은 프로젝트 루트에서 환경변수 폴더 경로와 Compose의 `env_file` 경로를 먼저 일치시킨 후 진행합니다.

```bash
docker compose --profile infra --profile delivery up -d
```

컨테이너 간 연결에는 Compose 서비스명(`db-server`, `eureka-server`)과 내부 포트를 사용합니다.  
호스트에서 실행할 경우 호스트에서 접근 가능한 DB 및 Eureka 주소와 포트를 지정해야 합니다.  
현재 Compose는 DB 포트를 호스트에 공개하지 않습니다.

현재 `ddl-auto`는 `update`입니다. 테스트는 H2와 `create-drop`을 별도로 사용합니다.

## IntelliJ 로컬 개발

배송 애플리케이션은 IntelliJ에서 실행하고 PostgreSQL만 Docker로 실행할 수 있습니다.  
`local` 프로필은 DB 주소를 `localhost:5432`, 앱 포트를 `8084`로 설정하고 Eureka 클라이언트를 비활성화합니다.  
API는 `http://localhost:8084`로 직접 호출합니다.  
Gateway / Eureka 연동이 필요한 통합 실행은 위 Docker 실행 방식을 사용합니다. 

프로젝트 루트에서 실행:

```bash
docker compose stop delivery-service
docker compose -f docker-compose.yml -f docker-compose.local.yml --profile infra up -d db-server
```

DB 포트 공개를 위해 DB 컨테이너가 재생성될 수 있지만 기존 `volume-db` 데이터 볼륨은 유지됩니다.  
다른 PostgreSQL이 5432 포트를 사용한다면 Compose 실행 시 `DB_LOCAL_PORT`를 변경하고 IntelliJ 환경변수에도 같은 값을 넣습니다.

IntelliJ에서 `build.gradle`을 Gradle 프로젝트로 연결하고 Gradle JVM과 실행 JRE를 Java 21로 설정합니다.  
`DeliveryApplication`의 실행 설정을 다음처럼 지정합니다.

- Main class: `logistics.delivery.DeliveryApplication`
- Classpath module: 배송 서비스의 main 모듈
- Program arguments: `--spring.profiles.active=local`
- Working directory: `C:/nbcamp/services/delivery-service`
- Environment variables: `POSTGRES_DB`, `DB_SCHEMA`, `DB_USERNAME`, `DB_PASSWORD`

위 네 환경변수는 `logistics-envs/delivery-service.env`의 값을 사용합니다.  
직접 입력할 때 값 바깥의 따옴표는 제외합니다. 해당 파일은 자동으로 읽지 않으며 비밀번호를 YAML에 작성하지 않습니다.  
실행 설정에서 `.env` 로딩 기능을 사용한다면 해당 파일을 지정해도 됩니다.

`DeliveryApplication`의 실행 또는 디버그 버튼을 눌러 기동합니다.  
중단점을 사용하고 코드 변경 후 애플리케이션을 재실행할 수 있습니다.

IntelliJ Database 도구에서 PostgreSQL 데이터소스를 추가하려면 다음을 사용합니다.

- Host: `localhost`
- Port: `5432` (변경 시 `DB_LOCAL_PORT` 값)
- Database: `POSTGRES_DB` 값
- User / Password: `DB_USERNAME` / `DB_PASSWORD` 값
- Schemas: `DB_SCHEMA` 값에 해당하는 스키마 선택

IntelliJ Database 연결은 DB 조회 도구용입니다.  
애플리케이션 DB 연결은 Spring datasource 설정과 실행 환경변수로 별도 구성됩니다.
