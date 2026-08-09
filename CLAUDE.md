# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Spring Boot 4.1 (Java 17) 기반 RESTful API 서버. Gradle, Spring Data JPA, Lombok, H2(runtime) 사용.

## 코드 컨벤션

- 모든 네이밍은 길더라도 의미를 정확히 알 수 있도록 줄여쓰지 않는다. 너무 복잡한 영어보다 직관적으로 알 수 있는 영단어를 사용한다.
- 이름에 중복된 의미가 담기는 경우 생략한다.
- 네이밍 규칙
  1. 변수, 함수, 메소드 - camelCase (예: `userPoint`)
  2. 클래스, Exception - PascalCase (예: `UserRepository`)
  3. ENUM, 상수 - UPPER_CASE (예: `USER_NAME`)
  4. Boolean 변수 - `is` + camelCase (예: `isUserExist`)
- 닫는 중괄호와 같은 줄에 `else`, `catch`, `finally`, `while`을 선언한다.
- DTO의 이너클래스에는 DTO 접미사를 붙이지 않는다 (예: `UserResponseDTO.UserKaKaoSignUpResult`).
- static import만 와일드카드(`*`)를 허용한다.
- 빈 블럭은 새줄 없이 중괄호를 닫는 것을 허용한다: `if (something) {}`
- 메소드 파라미터가 여러 개면 한 줄에 하나씩, 엔터로 구분한다.

```java
@PostMapping("/signup/kakao")
public ApiResponse<UserResponseDTO.UserKaKaoSignUpResultDTO> signUpKakao(
        @RequestHeader("OpenId") String openId,
        @RequestBody UserRequestDTO.SignUpKakaoRequestDTO request
) {
    return ApiResponse.onSuccess(
            userCommandService.signUpKakao(openId, request)
    );
}
```

## 패키지 전략 (DDD 계층 구조)

- 위 계층에서 아래 계층 접근은 가능하지만, 아래 계층에서 위 계층 접근은 불가능하다.
- 한 계층의 관심사와 관련된 것은 다른 계층에 배치하지 않는다. 각 도메인은 서로 철저히 분리하고, 각 레이어는 하나의 관심사에만 집중한다.

최상위 구조:
- `domain` — 엔티티. 외부 변경에 의해 도메인 내부가 변경되는 것을 막아야 한다.
- `infra` — 외부와의 통신 담당 로직 (예: 카카오 인증서버 OAuth).
- `global` — 공통된 응답 처리 등 전역 관심사.

도메인 하위 패키지 (예: `User` 도메인):
1. `entity` — DB와 직접 매핑되는 클래스 (예: `User`).
2. `dto` — 통신 요청/응답 형태 정의. `Request`/`Response` DTO를 각각 하나씩 두고, 내부에 static class를 사용한다 (예: `UserRequestDTO`, `UserResponseDTO`).
3. `controller` — 클라이언트로부터 요청(DTO)을 직접 받는 클래스. API path가 여기서 정의된다. 서비스를 호출하고 최종적으로 DTO를 반환한다 (예: `UserController`).
4. `service` — 비즈니스 로직 처리. Controller에서 받은 DTO로 로직을 수행하고 Repository로 DB 작업을 수행한 뒤 DTO로 결과를 반환한다 (예: `UserService`).
5. `repository` — DB와 상호작용, CRUD 수행. JPA 사용 (예: `UserRepository`).
6. `converter` — DTO ↔ Entity 변환 담당. Controller, Service에서 필요한 형태로 변환할 때 호출한다.

## 커밋 메시지 컨벤션

형식: `{태그}: {커밋 메세지 제목} (#{이슈번호})` — 예: `feat: 깃 커밋 메시지 (#0)`

태그는 영어 소문자로 작성한다.

| 태그 | 의미 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `refactor` | 코드 리팩토링 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `test` | 테스트 코드, 리팩토링 테스트 코드 추가 |
| `chore` | 패키지 매니저 수정, 그 외 기타 수정 (예: .gitignore) |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일 또는 폴더명을 수정하거나 옮기는 작업만인 경우 |
| `remove` | 파일을 삭제하는 작업만 수행한 경우 |
| `breaking change` | 커다란 API 변경의 경우 |
| `hotfix` | 급하게 치명적인 버그를 고쳐야 하는 경우 |
| `environment` | 환경 셋팅 |

기타 규칙:
1. 제목과 본문은 빈 행으로 분리한다. 제목/본문은 한글로 작성하며, 본문에는 어떻게보다 무엇을 왜 변경했는지 설명한다.
2. 제목 첫 글자는 대문자로, 끝에는 `.`를 찍지 않는다.
3. 제목은 50자 이내로 작성한다.
4. 코드가 직관적으로 파악된다고 가정하지 않고 충분히 설명한다.
5. 여러 항목이 있으면 글머리 기호로 가독성을 높인다.

```
feat: Login implementation [#10]

- 변경 내용 1
- 변경 내용 2
- 변경 내용 3
```
