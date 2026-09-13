package com.derek.portfolio.data.model

// ========================================
// 샘플 프로필 및 프로젝트 데이터
//
// Firestore 를 읽지 못했을 때의 폴백이자, 관리자 화면 '기본 데이터 가져오기' 의 시드다.
// 공개 저장소에 올리므로 실제 인물·회사·연락처가 아닌 가짜 값만 넣는다.
// 실제 콘텐츠는 Firestore(site/profile, projects)에서 관리한다.
//
// 주의: 관리자 화면에서 '기본 데이터 가져오기' / '초기화' 를 누르면 이 샘플이
// Firestore 에 들어간다.
// ========================================

val DefaultProfile = ProfileData(
    name = "홍길동",
    title = "Mobile & Full-Stack Developer",
    experience = "8년",
    bio = "iOS와 Android 네이티브 앱부터 크로스 플랫폼까지\n" +
        "다양한 모바일 서비스를 만들어 온 개발자입니다.\n\n" +
        "사용자 경험을 해치지 않는 안정적인 앱을 목표로\n" +
        "설계부터 배포, 운영까지 전 과정을 함께합니다.\n" +
        "(이 내용은 샘플 데이터입니다.)",
    email = "hello@example.com",
    phone = "010-0000-0000",
    github = "https://github.com/example",
    notion = "https://www.notion.so/example",
    certificate = "정보처리기사 (샘플)",
    directSkillGroups = listOf(
        SkillGroup(
            category = "iOS 개발",
            skills = listOf("Swift", "SwiftUI", "UIKit", "Combine", "MVVM"),
        ),
        SkillGroup(
            category = "Android 개발",
            skills = listOf("Kotlin", "Jetpack Compose", "Coroutine", "Ktor", "Hilt (DI)", "MVVM"),
        ),
        SkillGroup(
            category = "크로스 플랫폼 (Cross-Platform)",
            skills = listOf("Flutter", "Dart"),
        ),
        SkillGroup(
            category = "협업 & 도구",
            skills = listOf("Git / GitHub", "Jira", "Figma", "Firebase"),
        ),
    ),
    aiSkillGroups = listOf(
        SkillGroup(
            category = "LLM 서비스 연동",
            skills = listOf("OpenAI API", "Function Calling", "프롬프트 엔지니어링"),
        ),
        SkillGroup(
            category = "AI 활용 풀스택 & 인프라",
            skills = listOf("FastAPI (Python)", "Next.js", "PostgreSQL", "AWS", "Docker"),
        ),
    ),
    skills = listOf(
        "Swift",
        "SwiftUI",
        "Kotlin",
        "Jetpack Compose",
        "Flutter",
        "OpenAI API",
        "FastAPI",
        "PostgreSQL",
        "AWS",
    ),
    careers = listOf(
        CareerHistory(
            company = "샘플모바일",
            period = "2023.01 - 2025.12",
            duration = "3년",
            role = "정규직 / Lead Mobile Developer",
        ),
        CareerHistory(
            company = "예시커머스 (ExampleShop)",
            period = "2020.03 - 2022.12",
            duration = "2년 10개월",
            role = "정규직 / iOS & Android Developer",
        ),
        CareerHistory(
            company = "데모랩 & 테스트웍스",
            period = "2018.01 - 2020.02",
            duration = "2년 2개월",
            role = "정규직 / Android Developer",
        ),
    ),
)

val DefaultProjects: List<ProjectData> = listOf(
    // 1. 샘플모바일 - 모바일 앱
    ProjectData(
        title = "샘플 헬스케어 - 걸음 수 & 건강 기록 앱",
        company = "샘플모바일",
        description = "하루 활동량과 건강 지표를 기록하고 주간 리포트로 보여 주는 헬스케어 앱입니다. (샘플 데이터)",
        period = "2024.03 - 2025.12",
        techStack = listOf("SwiftUI", "Combine", "Kotlin", "Jetpack Compose", "HealthKit", "MVVM"),
        role = "iOS & Android 개발",
        achievements = listOf(
            "iOS · Android 네이티브 앱을 같은 설계로 동시 개발",
            "오프라인에서도 기록이 유실되지 않도록 로컬 저장 후 동기화하는 구조 설계",
            "홈 화면 위젯으로 오늘의 활동량을 바로 확인할 수 있도록 개선",
        ),
        category = ProjectCategory.MOBILE,
        appStoreUrl = "https://example.com/app-store",
        playStoreUrl = "https://example.com/google-play",
    ),

    // 2. 샘플모바일 - AI 기능
    ProjectData(
        title = "샘플 어시스턴트 - LLM 기반 대화형 도우미",
        company = "샘플모바일",
        description = "앱 안에서 자연어로 질문하면 기록을 찾아 요약해 주는 대화형 도우미 기능입니다. (샘플 데이터)",
        period = "2023.01 - 2024.02",
        techStack = listOf("OpenAI API", "Function Calling", "Kotlin", "Coroutine", "Ktor"),
        role = "AI 기능 연동 개발",
        achievements = listOf(
            "Function Calling 으로 앱 데이터를 조회하는 도구 설계",
            "스트리밍 응답을 화면에 끊김 없이 표시하는 UI 구현",
        ),
        category = ProjectCategory.AI,
    ),

    // 3. 예시커머스 - 쇼핑 앱
    ProjectData(
        title = "예시마켓 - 모바일 쇼핑 & 간편 결제",
        company = "예시커머스",
        description = "상품 추천과 간편 결제를 제공하는 모바일 커머스 앱입니다. (샘플 데이터)",
        period = "2020.03 - 2022.12",
        techStack = listOf("Swift", "UIKit", "Kotlin", "Retrofit", "WebView Bridge"),
        role = "iOS & Android 개발",
        achievements = listOf(
            "결제 플로우를 네이티브로 전환해 결제 단계 이탈률 개선",
            "딥링크 기반 화면 라우팅 구조 정리",
            "WebView 와 네이티브 간 브릿지 설계",
        ),
        category = ProjectCategory.MOBILE,
        liveUrl = "https://example.com",
    ),

    // 4. 개인 프로젝트 - 웹
    ProjectData(
        title = "샘플 블로그 - 반응형 개인 웹사이트",
        company = "개인 토이 프로젝트",
        description = "모바일과 데스크톱에 모두 대응하는 정적 블로그 사이트입니다. (샘플 데이터)",
        period = "2024",
        techStack = listOf("Flutter Web", "Dart", "Firebase Hosting", "Responsive UI"),
        role = "1인 개발",
        achievements = listOf(
            "화면 폭에 따라 레이아웃이 바뀌는 반응형 UI 구현",
            "정적 호스팅 기반 배포 파이프라인 구성",
        ),
        category = ProjectCategory.WEB,
        liveUrl = "https://example.com",
    ),

    // 5. 개인 프로젝트 - 백엔드 / 데이터
    ProjectData(
        title = "샘플 데이터 파이프라인 - 로그 수집 & 대시보드",
        company = "개인 프로젝트",
        description = "앱 이벤트 로그를 수집해 적재하고 대시보드로 보여 주는 데이터 파이프라인입니다. (샘플 데이터)",
        period = "2025 - 진행 중",
        techStack = listOf("FastAPI (Python)", "PostgreSQL", "Next.js", "AWS", "Docker Compose"),
        role = "Full-Stack & Data",
        achievements = listOf(
            "FastAPI 기반 수집 API 와 PostgreSQL 적재 구조 설계",
            "Next.js 대시보드로 일별 지표 시각화",
        ),
        category = ProjectCategory.BACKEND,
        githubUrl = "https://github.com/example/sample-pipeline",
    ),

    // 6. 데모랩 & 테스트웍스 - 기타
    ProjectData(
        title = "데모 커뮤니티 - 소셜 피드 & 채팅",
        company = "데모랩 & 테스트웍스",
        description = "게시글 피드와 1:1 채팅을 제공하는 커뮤니티 앱의 Android 클라이언트입니다. (샘플 데이터)",
        period = "2018.01 - 2020.02",
        techStack = listOf("Java", "Kotlin", "RxJava", "Retrofit", "In-App Billing"),
        role = "Android 앱 개발",
        achievements = listOf(
            "실시간 채팅과 게시글 피드 등 핵심 기능 구현",
            "Java 코드베이스에 Kotlin 을 단계적으로 도입",
        ),
        category = ProjectCategory.OTHER,
    ),
)
