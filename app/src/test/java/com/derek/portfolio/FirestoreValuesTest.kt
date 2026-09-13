package com.derek.portfolio

import com.derek.portfolio.data.model.DefaultProfile
import com.derek.portfolio.data.model.DefaultProjects
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.data.model.ProjectCategory
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.data.remote.FirestoreValues
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreValuesTest {

    @Test
    fun `프로젝트는 인코딩했다 되읽어도 같다`() {
        DefaultProjects.forEachIndexed { index, original ->
            val project = original.copy(order = index)
            val decoded = FirestoreValues.decodeFields(FirestoreValues.encodeFields(project.toMap()))
            assertEquals(project.copy(id = "doc"), ProjectData.fromMap("doc", decoded))
        }
    }

    @Test
    fun `프로필은 인코딩했다 되읽어도 같다`() {
        val decoded = FirestoreValues.decodeFields(FirestoreValues.encodeFields(DefaultProfile.toMap()))
        assertEquals(DefaultProfile, ProfileData.fromMap(decoded))
    }

    @Test
    fun `정수는 문자열로, 빈 배열은 values 없이 보낸다`() {
        assertEquals("""{"integerValue":"3"}""", FirestoreValues.encode(3).toString())
        assertEquals("""{"arrayValue":{}}""", FirestoreValues.encode(emptyList<String>()).toString())
        assertEquals("""{"nullValue":null}""", FirestoreValues.encode(null).toString())
    }

    @Test
    fun `REST 응답 모양을 그대로 읽는다`() {
        val fields = Json.parseToJsonElement(
            """
            {
              "title": {"stringValue": "제목"},
              "order": {"integerValue": "2"},
              "imageUrl": {"nullValue": null},
              "category": {"stringValue": "backend"},
              "techStack": {"arrayValue": {"values": [{"stringValue": "Kotlin"}, {"stringValue": " "}]}},
              "createdAt": {"timestampValue": "2026-09-10T05:22:11.123456Z"}
            }
            """.trimIndent(),
        ) as JsonObject

        val map = FirestoreValues.decodeFields(fields)
        assertEquals(Instant.parse("2026-09-10T05:22:11.123456Z"), map["createdAt"])

        val project = ProjectData.fromMap("abc", map)
        assertEquals(2, project.order)
        assertEquals(ProjectCategory.BACKEND, project.category)
        assertEquals(listOf("Kotlin"), project.techStack)
        assertNull(project.imageUrl)
    }

    @Test
    fun `알 수 없는 카테고리는 기타로 흡수한다`() {
        assertEquals(ProjectCategory.OTHER, ProjectCategory.fromName("unknown"))
        assertEquals(ProjectCategory.MOBILE, ProjectCategory.fromName("mobile"))
    }
}
