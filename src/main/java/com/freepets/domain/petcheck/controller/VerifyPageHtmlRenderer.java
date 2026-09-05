package com.freepets.domain.petcheck.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.web.util.HtmlUtils;

import com.freepets.domain.petcheck.dto.PetCheckResponseDTO.VerifyPage;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO.VerifyPetInfo;
import com.freepets.domain.petcheck.entity.PetCheckResult;

/**
 * GET /verify/{code}가 내려주는 HTML을 직접 조립한다.
 *
 * <p>이 리포에는 템플릿 엔진(Thymeleaf 등)이 없고, 페이지 하나짜리 정적 화면이라 새로 들이는
 * 것도 과하다 — 문자열 조립으로 충분하다.
 *
 * <p>시설명·조건 원문·반려동물 이름은 전부 사용자/외부 데이터라 그대로 삽입하면 저장형 XSS가
 * 된다({@link HtmlUtils#htmlEscape}로 전부 이스케이프한다). 인증 없는 공개 페이지라 더 조심해야 한다.
 */
final class VerifyPageHtmlRenderer {

    private VerifyPageHtmlRenderer() {}

    private static final String RESULT_LABEL_ALLOWED = "가능";
    private static final String RESULT_LABEL_CONDITIONAL = "조건부";
    private static final String RESULT_LABEL_DENIED = "불가";

    // "2026. 8. 23. 13:32" — FE의 formatIssuedAt(passport.ts)와 같은 문구 규칙을 따른다.
    private static final DateTimeFormatter ISSUED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy. M. d. HH:mm", Locale.KOREA);

    static String render(VerifyPage page) {
        Tone tone = toneOf(page.result());

        String petSection = page.pet() != null
                ? petSection(page.pet())
                : """
                  <div class="box muted">판별 당시 정보가 더 이상 없습니다 — 반려동물이 이후 삭제됐어요.</div>
                  """;

        String conditionsSection = renderConditions(page.conditions());
        String rawSection = renderRawCondition(page.petConditionRaw(), page.confirmedAt());

        String body = """
                <div class="page">
                  <div class="card">
                    <div class="brand-bar">
                      <span class="brand">🐾 프리펫츠 동반 출입증</span>
                      <span class="code">%s</span>
                    </div>

                    <div class="verdict" style="background:%s">
                      <div class="verdict-text">
                        <div class="headline" style="color:%s">%s</div>
                        <div class="facility-name">%s</div>
                      </div>
                      <div class="result-chip" style="background:%s">%s</div>
                    </div>

                    %s

                    %s

                    %s

                    <div class="reason">%s</div>
                  </div>

                  <div class="issued">%s</div>
                  <div class="footer">출입증은 판별 시점의 정보로 만들어집니다. 시설이 조건을 바꿨을 수 있으니 현장 안내문과 다르면 제보해 주세요.</div>
                </div>
                """.formatted(
                escape(page.verifyCode()),
                tone.soft(),
                tone.color(),
                escape(tone.headline()),
                escape(page.facilityName()),
                tone.color(),
                tone.label(),
                petSection,
                conditionsSection,
                rawSection,
                escape(page.reason()),
                formatIssuedAt(page.issuedAt())
        );

        return wrapPage(body);
    }

    static String renderError(String message) {
        String body = """
                <div class="page">
                  <div class="card error-card">
                    <div class="brand-bar">
                      <span class="brand">🐾 프리펫츠 동반 출입증</span>
                    </div>
                    <div class="box muted">%s</div>
                  </div>
                </div>
                """.formatted(escape(message));

        return wrapPage(body);
    }

    // render()/renderError() 둘 다 같은 문서 뼈대(doctype·head·style)를 쓴다 — 여기서만 관리해
    // 둘이 따로 드리프트하지 않게 한다.
    private static String wrapPage(String bodyHtml) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>프리펫츠 동반 출입증 검증</title>
                <style>
                %s
                </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(CSS, bodyHtml);
    }

    private static String petSection(VerifyPetInfo pet) {
        String vaccineBadge = pet.isVaccinated()
                ? "접종 완료" + (pet.vaccinationDate() != null ? " · " + pet.vaccinationDate() : "")
                : "접종 미완료";

        return """
                <div class="pet-row">
                  <div class="pet-name">%s</div>
                  <div class="pet-meta">%s · %s%s · %s</div>
                  <div class="mini-badge">%s</div>
                </div>
                """.formatted(
                escape(pet.name()),
                escape(pet.species()),
                pet.weight().stripTrailingZeros().toPlainString(),
                "kg",
                escape(pet.breedSizeLabel()),
                escape(vaccineBadge)
        );
    }

    private static String renderConditions(List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }

        StringBuilder items = new StringBuilder();
        for (String condition : conditions) {
            items.append("<li>").append(escape(condition)).append("</li>");
        }

        return """
                <div class="box">
                  <div class="box-label">확인해야 할 조건</div>
                  <ul class="conditions">%s</ul>
                </div>
                """.formatted(items);
    }

    private static String renderRawCondition(
            String petConditionRaw,
            LocalDateTime confirmedAt
    ) {
        if (petConditionRaw == null || petConditionRaw.isBlank()) {
            return "";
        }

        String freshness = freshnessText(confirmedAt);
        // petConditionRaw는 관광공사 원문을 그대로 옮긴 값이 아니라 사람이 확인해 정리한 문장이다
        // (Facility.petConditionRaw 주석 참고) — "원문"이라고 표시하면 실제로는 편집된 문장을
        // 정부 데이터 그대로인 것처럼 오해하게 만든다.
        String source = "출처 · 한국관광공사 반려동물 동반여행 데이터를 바탕으로 정리" + (freshness != null ? " · " + freshness : "");

        return """
                <div class="box">
                  <div class="box-label">시설 게시 조건 안내</div>
                  <div class="raw-text">%s</div>
                  <div class="raw-source">%s</div>
                </div>
                """.formatted(escape(petConditionRaw), escape(source));
    }

    // result 하나당 화면에 필요한 문구·색을 한곳에 묶는다 — result별 스위치가 4개로 흩어져
    // 있으면 결과값을 추가/변경할 때 하나를 빠뜨려도 컴파일은 되고 화면만 어긋난다.
    private record Tone(
            String label,
            String headline,
            String color,
            String soft
    ) {}

    private static Tone toneOf(PetCheckResult result) {
        return switch (result) {
            case ALLOWED -> new Tone(RESULT_LABEL_ALLOWED, "동반 입장 가능합니다", "#1E8E5A", "#E7F6EE");
            case CONDITIONAL -> new Tone(RESULT_LABEL_CONDITIONAL, "조건을 확인해 주세요", "#B8720C", "#FBF0DD");
            case DENIED -> new Tone(RESULT_LABEL_DENIED, "동반 입장이 어렵습니다", "#C23B3B", "#FBEAEA");
        };
    }

    // "3일 전 확인" 형태 — FE의 freshnessText(passport.ts)와 같은 문구 규칙을 따른다.
    private static String freshnessText(LocalDateTime confirmedAt) {
        if (confirmedAt == null) {
            return null;
        }

        long days = ChronoUnit.DAYS.between(confirmedAt, LocalDateTime.now());
        if (days <= 0) {
            return "오늘 확인";
        }
        if (days < 30) {
            return days + "일 전 확인";
        }
        long months = days / 30;
        return months + "개월 전 확인";
    }

    // "2026. 8. 23. 13:32 발급" — FE의 formatIssuedAt(passport.ts)와 같은 문구 규칙을 따른다.
    private static String formatIssuedAt(LocalDateTime issuedAt) {
        return ISSUED_AT_FORMAT.format(issuedAt) + " 발급";
    }

    private static String escape(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text);
    }

    private static final String CSS = """
            * { box-sizing: border-box; }
            body {
              margin: 0;
              background: #F4F1EC;
              font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif;
              color: #22201C;
            }
            .page { max-width: 420px; margin: 0 auto; padding: 20px 16px 32px; }
            .card {
              background: #FFFFFF;
              border: 2px solid #E4614B;
              border-radius: 20px;
              padding: 18px;
              display: flex;
              flex-direction: column;
              gap: 14px;
            }
            .error-card { border-color: #C9C3B8; }
            .brand-bar {
              display: flex;
              align-items: center;
              justify-content: space-between;
              padding-bottom: 10px;
              border-bottom: 1px solid #EEE9E0;
              font-weight: 800;
              font-size: 13px;
              color: #E4614B;
            }
            .code { color: #8A8578; font-variant-numeric: tabular-nums; letter-spacing: 0.4px; }
            .verdict {
              display: flex;
              align-items: center;
              justify-content: space-between;
              gap: 12px;
              border-radius: 14px;
              padding: 14px;
            }
            .headline { font-size: 17px; font-weight: 900; }
            .facility-name { font-size: 13px; font-weight: 700; color: #45423B; margin-top: 2px; }
            .result-chip {
              color: #FFFFFF;
              font-size: 12.5px;
              font-weight: 900;
              border-radius: 999px;
              padding: 5px 11px;
              white-space: nowrap;
            }
            .pet-row {
              border: 1px solid #EEE9E0;
              border-radius: 12px;
              padding: 12px 14px;
            }
            .pet-name { font-size: 18px; font-weight: 900; }
            .pet-meta { font-size: 12.5px; color: #6B6759; margin-top: 2px; }
            .mini-badge {
              display: inline-block;
              margin-top: 6px;
              background: #E7F6EE;
              color: #1E8E5A;
              font-size: 11px;
              font-weight: 800;
              border-radius: 999px;
              padding: 3px 9px;
            }
            .box {
              border: 1px solid #EEE9E0;
              border-radius: 12px;
              padding: 12px 14px;
              background: #FAF8F4;
            }
            .box-label { font-size: 10.5px; font-weight: 800; letter-spacing: 0.3px; color: #8A8578; }
            .conditions { margin: 6px 0 0; padding-left: 18px; font-size: 13px; line-height: 1.6; }
            .raw-text { font-size: 12.5px; line-height: 1.55; margin-top: 5px; }
            .raw-source { font-size: 10.5px; color: #8A8578; margin-top: 6px; }
            .reason { font-size: 12.5px; line-height: 1.5; color: #45423B; padding: 0 2px; }
            .muted { color: #6B6759; font-size: 13px; }
            .issued {
              text-align: center;
              font-size: 10.5px;
              color: #8A8578;
              margin-top: 12px;
              font-variant-numeric: tabular-nums;
            }
            .footer {
              text-align: center;
              font-size: 11.5px;
              line-height: 1.5;
              color: #8A8578;
              margin-top: 6px;
              padding: 0 12px;
            }
            """;
}
