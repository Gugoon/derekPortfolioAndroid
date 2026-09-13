package com.derek.portfolio

import com.derek.portfolio.data.visit.VisitGeoInfo
import com.derek.portfolio.data.visit.VisitRecord
import com.derek.portfolio.data.visit.clipVisitText
import com.derek.portfolio.data.visit.describeUserAgent
import com.derek.portfolio.data.visit.isValidVisitorId
import com.derek.portfolio.data.visit.newVisitorId
import com.derek.portfolio.data.visit.visitDocId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitModelsTest {

    @Test
    fun `문서 ID 는 규칙이 검사하는 모양이다`() {
        repeat(20) {
            val id = newVisitorId()
            assertTrue(id, isValidVisitorId(id))
        }
        assertEquals("0123456789abcdef_175700000", visitDocId("0123456789abcdef", 1_757_000_000_123))
    }

    @Test
    fun `이름에 bot 이 든 기기는 봇이 아니다`() {
        val cubot = "Mozilla/5.0 (Linux; Android 10; CUBOT X30) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
        assertEquals("Chrome · Android", describeUserAgent(cubot))
        assertEquals("봇/크롤러", describeUserAgent("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"))
    }

    @Test
    fun `앱과 브라우저를 구분한다`() {
        assertEquals(
            "포트폴리오 앱 · Android",
            describeUserAgent("DerekPortfolioApp/1.0.0 (Linux; Android 16; Google Pixel 9)"),
        )
        assertEquals(
            "Edge · Windows",
            describeUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36 Edg/120"),
        )
        assertEquals("", describeUserAgent(""))
    }

    @Test
    fun `ipwho_is 응답을 읽는다`() {
        val geo = VisitGeoInfo.tryParse(
            """{"ip":"1.2.3.4","success":true,"country":"South Korea","country_code":"KR",
               "region":"Seoul","city":"Seoul","connection":{"isp":"LG POWERCOMM"},
               "timezone":{"id":"Asia/Seoul"}}""",
        )!!
        assertEquals("1.2.3.4", geo.ip)
        assertEquals("LG POWERCOMM", geo.org)
        assertEquals("Asia/Seoul", geo.timezone)
    }

    @Test
    fun `geojs 응답을 읽는다`() {
        val geo = VisitGeoInfo.tryParse(
            """{"ip":"5.6.7.8","country":"Japan","country_code":"JP","region":"Tokyo","city":"Tokyo",
               "organization_name":"NTT","timezone":"Asia/Tokyo"}""",
        )!!
        assertEquals("NTT", geo.org)
        assertEquals("Asia/Tokyo", geo.timezone)
    }

    @Test
    fun `조회 실패 응답은 버린다`() {
        assertNull(VisitGeoInfo.tryParse("""{"success":false,"message":"limit"}"""))
        assertNull(VisitGeoInfo.tryParse("not json"))
        assertNull(VisitGeoInfo.tryParse("""{"country":"KR"}"""))
    }

    @Test
    fun `위치는 겹치는 이름을 한 번만 적는다`() {
        val record = VisitRecord.fromMap(
            "id",
            mapOf("country" to "South Korea", "region" to "Seoul", "city" to "Seoul", "referrer" to "https://www.google.com/search?q=x"),
        )
        assertEquals("South Korea · Seoul", record.location)
        assertEquals("www.google.com", record.source)
        assertEquals("직접 방문", VisitRecord.fromMap("id", emptyMap()).source)
    }

    @Test
    fun `긴 문자열은 잘라서 넣는다`() {
        assertEquals(300, clipVisitText("a".repeat(1000)).length)
        assertEquals("", clipVisitText(null))
    }
}
