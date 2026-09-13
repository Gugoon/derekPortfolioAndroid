package com.derek.portfolio

import com.derek.portfolio.data.model.CareerHistory
import com.derek.portfolio.data.model.DefaultProfile
import com.derek.portfolio.data.model.DefaultProjects
import com.derek.portfolio.data.model.projectsForCareer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerMatchingTest {

    @Test
    fun `기본 데이터의 모든 경력이 제 프로젝트를 찾는다`() {
        DefaultProfile.careers.forEach { career ->
            assertTrue(career.company, projectsForCareer(career, DefaultProjects).isNotEmpty())
        }
    }

    @Test
    fun `괄호 부연과 공백을 걷어내고 비교한다`() {
        val career = CareerHistory(company = "예시커머스 (ExampleShop)", period = "", duration = "", role = "")
        assertEquals(listOf("예시커머스"), projectsForCareer(career, DefaultProjects).map { it.company })
    }

    @Test
    fun `부분 일치는 짝으로 보지 않는다`() {
        val career = CareerHistory(company = "데모랩", period = "", duration = "", role = "")
        assertTrue(projectsForCareer(career, DefaultProjects).isEmpty())
    }

    @Test
    fun `목록 순서를 유지한다`() {
        val career = DefaultProfile.careers.first { it.company == "샘플모바일" }
        val expected = DefaultProjects.filter { it.company == "샘플모바일" }.map { it.title }
        assertEquals(2, expected.size)
        assertEquals(expected, projectsForCareer(career, DefaultProjects).map { it.title })
    }
}
