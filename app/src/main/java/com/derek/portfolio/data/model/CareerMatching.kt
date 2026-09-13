package com.derek.portfolio.data.model

/**
 * [career] 회사에서 진행한 프로젝트를 목록 순서 그대로 골라낸다.
 *
 * 회사명은 자유 입력이라 경력과 프로젝트의 표기가 어긋나기 쉽다. 예를 들어
 * 경력은 '예시커머스 (ExampleShop)', 프로젝트는 '예시커머스' 로 들어갈 수 있다.
 * 그래서 괄호 부연과 공백을 걷어낸 뒤 비교한다.
 *
 * 부분 일치(한쪽이 다른 쪽을 포함)는 허용하지 않는다. 지금 데이터는 정규화만으로
 * 일곱 경력이 모두 제 짝을 찾고, 부분 일치는 오탐만 만든다. 못 찾으면 모달이 비는
 * 데서 그치지만, 잘못 붙으면 하지 않은 일을 그 회사에서 한 것처럼 보여 준다.
 */
fun projectsForCareer(career: CareerHistory, projects: List<ProjectData>): List<ProjectData> {
    val key = companyKey(career.company)
    if (key.isEmpty()) return emptyList()
    return projects.filter { companyKey(it.company) == key }
}

private val Parenthetical = Regex("""\([^)]*\)""")
private val Whitespace = Regex("""\s+""")

private fun companyKey(company: String): String = company
    .replace(Parenthetical, "")
    .replace(Whitespace, "")
    .lowercase()
