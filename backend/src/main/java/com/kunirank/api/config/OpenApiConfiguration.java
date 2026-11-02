package com.kunirank.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean OpenAPI apiDescription() {
        return new OpenAPI().info(new Info().title("K-unirank Spring API").version("v1")
            .description("대학·입결·순위 조회와 회원·투표·AI API입니다. "
                + "운영 서비스는 조회/회원/투표 프로필로 분리됩니다. 내부 API는 기존 BFF 인증이 필요하며 "
                + "문서 열람으로 권한이 부여되지 않습니다. 실제 토큰·쿠키·개인정보를 예시에 입력하지 마세요."))
            .components(new Components()
                .addSecuritySchemes("memberService", header("X-Member-Frontend-Token"))
                .addSecuritySchemes("votingService", header("X-VS-Frontend-Token"))
                .addSecuritySchemes("aiService", header("X-Frontend-Service-Token"))
                .addSecuritySchemes("memberSession", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE).name("kunirank_member")
                    .description("기존 회원 로그인으로 발급되는 HttpOnly 쿠키. 브라우저에서 직접 설정하지 않습니다.")));
    }

    private SecurityScheme header(String name) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
            .name(name).description("서버 간 전용 인증. 운영 비밀값을 브라우저에 입력하지 않습니다.");
    }

    @Bean GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder().group("public")
            .pathsToMatch("/api/v1/universities/**", "/api/v1/admissions/**", "/api/v1/rankings/**",
                "/api/v1/boards/*/ranking", "/api/v1/boards/*/ranking/", "/api/v1/results/**", "/api/v1/seo/**")
            .build();
    }
    @Bean GroupedOpenApi memberApi() {
        return GroupedOpenApi.builder().group("member").pathsToMatch("/api/v1/member/**", "/api/v1/admin/**").build();
    }
    @Bean GroupedOpenApi votingApi() {
        return GroupedOpenApi.builder().group("voting")
            .pathsToMatch("/api/v1/boards/*/next", "/api/v1/boards/*/vote", "/api/v1/boards/*/result").build();
    }
    @Bean GroupedOpenApi aiApi() {
        return GroupedOpenApi.builder().group("ai").pathsToMatch("/api/v1/ai/**").build();
    }

    @Bean GlobalOperationCustomizer apiOperations() {
        Map<String,String> tags=Map.ofEntries(
            Map.entry("UniversityController","대학 정보"), Map.entry("UniversityOverviewController","대학 입결 요약"),
            Map.entry("AdmissionController","입시결과"), Map.entry("GradeComparisonController","내 점수 학과 찾기"),
            Map.entry("RankingDiscoveryController","입결·공시 순위"), Map.entry("RankingController","선호도·대학 투표"),
            Map.entry("PersonalResultController","개인 결과"), Map.entry("SeoController","사이트맵"),
            Map.entry("MemberController","회원 인증"), Map.entry("MemberHistoryController","내 투표 기록"),
            Map.entry("FavoriteUniversityController","관심 대학"), Map.entry("FavoriteUnitController","관심 학과"),
            Map.entry("AdminController","관리자 현황"), Map.entry("AiQuestionController","AI 입결"));
        Map<String,String> summaries=Map.ofEntries(
            Map.entry("search","입시결과 검색"), Map.entry("university","대학별 입시결과"),
            Map.entry("unit","모집단위별 입시결과"), Map.entry("compare","내 등급과 공개 입결 비교"),
            Map.entry("reference","투표 카드용 입결 참고"), Map.entry("overview","요약 및 현황 조회"),
            Map.entry("regions","대학 지역 목록"), Map.entry("catalog","순위 지표 목록"),
            Map.entry("next","다음 대학 대진"), Map.entry("vote","대학 선택 또는 건너뛰기"),
            Map.entry("ranking","대학 선호도 순위"), Map.entry("login","기존 계정 로그인"),
            Map.entry("register","회원가입 및 로그인"), Map.entry("logout","로그아웃 및 세션 철회"),
            Map.entry("me","현재 로그인 사용자"), Map.entry("ask","자연어 입결 검색"),
            Map.entry("read","기존 개인 결과 조회"), Map.entry("create","개인 TOP10 결과 생성"),
            Map.entry("list","목록 조회"), Map.entry("detail","상세 조회"),
            Map.entry("indicator","공시 지표 순위"), Map.entry("admissions","입결 순위"),
            Map.entry("add","관심 등록"), Map.entry("remove","관심 해제"));
        Map<String,String> parameters=Map.ofEntries(
            Map.entry("year","조회 학년도. 생략 시 해당 API의 최신 공개 학년도를 사용합니다."),
            Map.entry("q","검색어. API에 따라 대학·학과·전형명 또는 관심 학과를 검색합니다."),
            Map.entry("page","0부터 시작하는 페이지 번호"), Map.entry("size","페이지당 결과 수 (최대 100)"),
            Map.entry("grade","9등급제 내신 평균등급 (1.0~9.0). 합격 확률이 아닌 과거 컷과 비교합니다."),
            Map.entry("metrics","public: 공개 지표 있는 결과 / all: 전체 결과"),
            Map.entry("kind","four: 4년제 / college: 전문대"),
            Map.entry("phase","SUSI: 수시 / JEONGSI: 정시"),
            Map.entry("track","student: 학생부교과 / holistic: 학생부종합 (일반 입결 검색에서는 csat·essay·practical도 지원)"),
            Map.entry("region","대학 지역 이름. 지역 목록 API의 값을 사용합니다."),
            Map.entry("university","비교 대상 정확한 대학명. 여러 대학은 쉼표로 구분합니다."),
            Map.entry("chart_min","비교 그래프에 포함할 최소 입결 표본 수"));
        return (operation,handler)->{
            String controller=handler.getBeanType().getSimpleName();
            operation.setTags(List.of(tags.getOrDefault(controller,controller)));
            operation.setSummary(summaries.getOrDefault(handler.getMethod().getName(),handler.getMethod().getName()));
            if(operation.getParameters()!=null) operation.getParameters().forEach(parameter->{
                if(parameters.containsKey(parameter.getName())) parameter.setDescription(parameters.get(parameter.getName()));
            });
            String pkg=handler.getBeanType().getPackageName();
            if(pkg.contains(".member.")) {
                operation.addSecurityItem(new SecurityRequirement().addList("memberService"));
                operation.setDescription("private member-api 전용. 서비스 인증과 필요한 로그인·관리자 권한 및 Origin 검사를 적용합니다.");
            } else if(controller.equals("AiQuestionController")) {
                operation.addSecurityItem(new SecurityRequirement().addList("aiService"));
            } else if(List.of("next","vote","create").contains(handler.getMethod().getName())
                    && pkg.contains(".ranking.")) {
                operation.addSecurityItem(new SecurityRequirement().addList("votingService"));
                operation.setDescription("private voting-api 전용. BFF가 서비스/클라이언트 인증·Origin·투표 세션을 전달합니다.");
            }
            return operation;
        };
    }
}
