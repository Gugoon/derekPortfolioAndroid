package com.derek.portfolio.resume

// 포트폴리오 콘텐츠(SiteContent)를 A4 이력서 PDF 로 조판한다.
//
// 화면용 카드 레이아웃을 그대로 옮기지 않고 인쇄물 관례(좌측 라벨 / 우측 본문, 얇은
// 구분선, 넉넉한 행간)에 맞춰 다시 짰다. 웹 앱의 resume_pdf.dart 와 같은 구성이다.
//
// android.graphics.pdf.PdfDocument 는 캔버스에 직접 그리는 저수준 API 라, 줄바꿈은
// StaticLayout 으로 하고 페이지 나눔은 아래의 작은 블록 배치기로 한다. 폰트는 앱에
// 번들한 서브셋 TTF 를 그대로 쓴다.
//
// 웹 판과 다른 점 하나: PdfDocument 는 링크 주석을 만들 수 없다. 그래서 링크는
// 라벨만 두지 않고 주소를 함께 적는다. 누를 수 없는 '서비스 링크' 글자만 남으면
// 종이로 뽑았을 때 아무 정보도 되지 않는다.

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.derek.portfolio.R
import com.derek.portfolio.data.model.CareerHistory
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.data.model.SkillGroup
import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.max

// 사이트 테마와 같은 잉크 색.
private val INK = 0xFF1E1C1A.toInt()
private val INK_SOFT = 0xFF48443F.toInt()
private val INK_MUTED = 0xFF868077.toInt()
private val PRIMARY = 0xFF1F3A60.toInt()
private val RULE = 0xFFD9D3C7.toInt()
private val RULE_LIGHT = 0xFFEFEAE1.toInt()
private val CHIP_BG = 0xFFF3F0E9.toInt()

// A4 (pt). PdfDocument 의 좌표 단위가 1/72 인치라 그대로 쓴다.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN_H = 42f
private const val MARGIN_V = 40f
private const val FOOTER_HEIGHT = 26f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_H * 2
private const val BODY_HEIGHT = PAGE_HEIGHT - MARGIN_V * 2 - FOOTER_HEIGHT

/** 본문 좌측 라벨 열의 폭. 좌측 눈금이 섹션마다 어긋나면 시선이 튀므로 공유한다. */
private const val LABEL_WIDTH = 104f

/** 기술 분류명은 '하드웨어 통신 & 솔루션 공통'처럼 길어 조금 더 넉넉히 준다. */
private const val SKILL_LABEL_WIDTH = 146f

/** 라벨과 본문 사이 홈통. */
private const val LABEL_GUTTER = 12f

/** 제목 뒤에 따라와야 할 내용이 한 페이지보다 크면, 최소 이만큼은 같은 페이지에 붙인다. */
private const val MIN_FOLLOWING = 60f

private val LineBreaks = Regex("""\s*\n\s*""")
private val ParagraphBreaks = Regex("""\n\s*\n""")

internal class ResumeFonts(
    val regular: Typeface,
    val medium: Typeface,
    val bold: Typeface,
    val serifBold: Typeface,
) {
    companion object {
        fun load(context: Context) = ResumeFonts(
            regular = context.font(R.font.noto_sans_kr_400),
            medium = context.font(R.font.noto_sans_kr_500),
            bold = context.font(R.font.noto_sans_kr_700),
            serifBold = context.font(R.font.noto_serif_kr_700),
        )

        private fun Context.font(@FontRes id: Int): Typeface =
            ResourcesCompat.getFont(this, id) ?: Typeface.DEFAULT
    }
}

internal class ResumePdfComposer(private val fonts: ResumeFonts) {

    fun compose(content: SiteContent): ByteArray {
        val profile = content.profile
        // 사이트 목록과 같은 순서(order 오름차순, 동률이면 원래 순서). sortedBy 는 안정 정렬이다.
        val projects = content.projects.sortedBy { it.order }
        val pages = paginate(buildBlocks(profile, projects))

        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, placed ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = document.startPage(info)
                val canvas = page.canvas
                placed.forEach { it.block.draw(canvas, MARGIN_H, MARGIN_V + it.top, CONTENT_WIDTH) }
                drawFooter(canvas, profile, index + 1, pages.size)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun buildBlocks(profile: ProfileData, projects: List<ProjectData>): List<PdfBlock> = buildList {
        add(HeaderBlock(fonts, profile))

        if (profile.bio.isNotBlank()) {
            add(sectionTitle("소개"))
            // bio 는 화면 줄바꿈 기준으로 \n 이 박혀 있다. PDF 폭은 다르므로 문단(\n\n)만
            // 살리고 문단 안의 줄바꿈은 공백으로 풀어 자연스럽게 흐르게 한다.
            profile.bio.split(ParagraphBreaks)
                .map { it.replace(LineBreaks, " ").trim() }
                .filter { it.isNotEmpty() }
                .forEach { add(TextBlock(it, PdfText(fonts.regular, 10f, INK_SOFT, lineSpacing = 3.2f), marginBottom = 7f)) }
        }

        if (profile.careers.isNotEmpty()) {
            add(sectionTitle("경력 사항"))
            profile.careers.forEach { add(CareerRowBlock(fonts, it)) }
        }

        val direct = profile.directSkillGroups.filter { it.skills.isNotEmpty() }
        val ai = profile.aiSkillGroups.filter { it.skills.isNotEmpty() }
        if (direct.isNotEmpty() || ai.isNotEmpty()) {
            add(sectionTitle("보유 기술"))
            if (direct.isNotEmpty()) {
                add(skillsSubtitle("직접 개발 부문", topGap = 0f))
                direct.forEach { add(skillGroupRow(it)) }
            }
            if (ai.isNotEmpty()) {
                add(skillsSubtitle("AI 활용 부문", topGap = if (direct.isEmpty()) 0f else 8f))
                ai.forEach { add(skillGroupRow(it)) }
            }
        }

        if (profile.certificate.isNotBlank()) {
            add(sectionTitle("자격증"))
            add(
                LabeledRowBlock(
                    fonts = fonts,
                    label = "공인 자격증",
                    value = profile.certificate,
                    valueStyle = PdfText(fonts.regular, 10f, INK_SOFT, lineSpacing = 2.5f),
                ),
            )
        }

        if (projects.isNotEmpty()) {
            add(sectionTitle("주요 프로젝트"))
            projects.forEachIndexed { index, project ->
                add(ProjectBlock(fonts, project, isLast = index == projects.lastIndex))
            }
        }
    }

    private fun sectionTitle(title: String) =
        SectionTitleBlock(title, PdfText(fonts.bold, 11.5f, PRIMARY, letterSpacing = 0.3f))

    private fun skillsSubtitle(text: String, topGap: Float) = TextBlock(
        text = text,
        style = PdfText(fonts.bold, 9.5f, INK, letterSpacing = 0.4f),
        marginTop = topGap,
        marginBottom = 6f,
        keepWithNext = true,
    )

    private fun skillGroupRow(group: SkillGroup) = LabeledRowBlock(
        fonts = fonts,
        label = group.category,
        value = group.skills.joinToString("  ·  "),
        valueStyle = PdfText(fonts.regular, 9.5f, INK_SOFT, lineSpacing = 2.6f),
        labelWidth = SKILL_LABEL_WIDTH,
        bottomGap = 6f,
    )

    /**
     * 블록을 페이지에 나눠 담는다.
     *
     * 프로젝트 블록은 통째로 옮겨 제목만 페이지 끝에 덩그러니 남지 않게 하고, 한
     * 페이지보다 큰 블록만 조각내 흘린다. 섹션 제목은 뒤따르는 내용과 같은 페이지에 둔다.
     */
    private fun paginate(blocks: List<PdfBlock>): List<List<Placed>> {
        val pages = mutableListOf(mutableListOf<Placed>())
        val queue = ArrayDeque(blocks)
        var y = 0f

        while (queue.isNotEmpty()) {
            val block = queue.removeFirst()
            val height = block.measure(CONTENT_WIDTH)

            if (height > BODY_HEIGHT) {
                val parts = block.split()
                if (parts != null) {
                    parts.asReversed().forEach(queue::addFirst)
                    continue
                }
            }

            // 제목 뒤에 부제목처럼 또 붙어 가야 할 블록이 이어질 수 있다('보유 기술' →
            // '직접 개발 부문' → 첫 기술 줄). 한 칸만 보면 부제목만 다음 쪽으로 넘어가고
            // 제목이 앞 쪽 끝에 홀로 남으므로, 붙어 가는 묶음 전체와 그 뒤 첫 내용까지 잰다.
            var required = height
            if (block.keepWithNext) {
                for (next in queue) {
                    val nextHeight = next.measure(CONTENT_WIDTH)
                    if (next.keepWithNext) {
                        required += nextHeight
                        continue
                    }
                    required += if (nextHeight <= BODY_HEIGHT) nextHeight else MIN_FOLLOWING
                    break
                }
            }

            if (y > 0f && y + required > BODY_HEIGHT) {
                pages.add(mutableListOf())
                y = 0f
            }
            pages.last().add(Placed(block, y))
            y += height
        }
        return pages
    }

    /**
     * 쪽마다 반복되는 유일한 줄. 넘겨 읽다 문서를 잃어버리지 않을 만큼의 신원은
     * 이름·이메일 한 줄로 충분하다.
     */
    private fun drawFooter(canvas: Canvas, profile: ProfileData, pageNumber: Int, pageCount: Int) {
        val identityPaint = PdfText(fonts.regular, 8f, INK_MUTED).paint()
        val numberPaint = PdfText(fonts.medium, 8f, INK_MUTED).paint()
        val baseline = PAGE_HEIGHT - MARGIN_V - identityPaint.fontMetrics.descent

        // 둘 중 하나가 비어 있을 수 있어 그대로 이으면 구분점만 덩그러니 남는다.
        val identity = listOf(profile.name, profile.email)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("  ·  ")
        canvas.drawText(identity, MARGIN_H, baseline, identityPaint)

        val number = "$pageNumber / $pageCount"
        canvas.drawText(number, PAGE_WIDTH - MARGIN_H - numberPaint.measureText(number), baseline, numberPaint)
    }

    private class Placed(val block: PdfBlock, val top: Float)
}

// ---------------------------------------------------------------
// 조판 기본 조각
// ---------------------------------------------------------------

internal class PdfText(
    val typeface: Typeface,
    val size: Float,
    val color: Int,
    /** 줄 사이에 더하는 간격(pt). */
    val lineSpacing: Float = 0f,
    /** 글자 사이 간격(pt). */
    val letterSpacing: Float = 0f,
) {
    fun paint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.typeface = typeface
        it.textSize = size
        it.color = color
        it.letterSpacing = letterSpacing / size
    }

    fun layout(text: CharSequence, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint(), max(1, floor(width).toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacing, 1f)
            .setIncludePad(false)
            .build()

    fun width(text: String): Float = paint().measureText(text)

    fun lineHeight(): Float = paint().fontMetrics.let { it.descent - it.ascent }
}

internal abstract class PdfBlock {
    /** true 면 다음 블록과 같은 페이지에 놓는다(제목류). */
    open val keepWithNext: Boolean get() = false

    abstract fun measure(width: Float): Float

    abstract fun draw(canvas: Canvas, left: Float, top: Float, width: Float)

    /** 한 페이지에 다 들어가지 않을 때 나눌 수 있으면 조각을 돌려준다. */
    open fun split(): List<PdfBlock>? = null
}

/** 측정 → 배치 → 그리기에서 같은 폭으로 여러 번 재므로 마지막 결과를 들고 있는다. */
private class LayoutCache(private val build: (width: Float) -> StaticLayout) {
    private var cachedWidth = Float.NaN
    private var cached: StaticLayout? = null

    operator fun get(width: Float): StaticLayout {
        val hit = cached
        if (hit != null && width == cachedWidth) return hit
        return build(width).also {
            cachedWidth = width
            cached = it
        }
    }
}

private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) {
    save()
    translate(x, y)
    layout.draw(this)
    restore()
}

private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).also {
    it.color = color
    it.style = Paint.Style.FILL
}

private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).also {
    it.color = color
    it.style = Paint.Style.STROKE
    it.strokeWidth = width
}

/** 링크 텍스트가 줄을 먹지 않도록 스킴과 www 를 걷어낸다. */
private fun shortUrl(url: String): String = url
    .replaceFirst(Regex("^https?://"), "")
    .replaceFirst(Regex("^www\\."), "")
    .removeSuffix("/")

internal class TextBlock(
    text: String,
    style: PdfText,
    private val marginTop: Float = 0f,
    private val marginBottom: Float = 0f,
    override val keepWithNext: Boolean = false,
) : PdfBlock() {
    private val layout = LayoutCache { width -> style.layout(text, width) }

    override fun measure(width: Float) = marginTop + layout[width].height + marginBottom

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) =
        canvas.drawLayout(layout[width], left, top + marginTop)
}

private class SpacerBlock(private val height: Float) : PdfBlock() {
    override fun measure(width: Float) = height
    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) = Unit
}

/** '경력 사항 ───────' 처럼 제목 오른쪽으로 가는 선이 이어지는 섹션 제목. */
private class SectionTitleBlock(private val title: String, style: PdfText) : PdfBlock() {
    override val keepWithNext: Boolean get() = true
    private val layout = LayoutCache { width -> style.layout(title, width) }

    override fun measure(width: Float) = MARGIN_TOP + layout[width].height + MARGIN_BOTTOM

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        val text = layout[width]
        canvas.drawLayout(text, left, top + MARGIN_TOP)
        val ruleLeft = left + text.getLineWidth(0) + 10f
        val centerY = top + MARGIN_TOP + text.height / 2f
        if (ruleLeft < left + width) {
            canvas.drawRect(ruleLeft, centerY - 0.4f, left + width, centerY + 0.4f, fillPaint(RULE))
        }
    }

    private companion object {
        const val MARGIN_TOP = 20f
        const val MARGIN_BOTTOM = 10f
    }
}

/** 좌측 라벨 + 우측 본문 한 줄. 기술/자격증이 이 형태를 공유한다. */
private class LabeledRowBlock(
    fonts: ResumeFonts,
    label: String,
    value: String,
    valueStyle: PdfText,
    private val labelWidth: Float = LABEL_WIDTH,
    private val bottomGap: Float = 8f,
) : PdfBlock() {
    private val labelStyle = PdfText(fonts.medium, 9f, INK_MUTED, lineSpacing = 1.5f)
    private val labelLayout = LayoutCache { _ -> labelStyle.layout(label, labelWidth) }
    private val valueLayout = LayoutCache { width -> valueStyle.layout(value, width - labelWidth - LABEL_GUTTER) }

    override fun measure(width: Float) =
        max(labelLayout[width].height, valueLayout[width].height) + bottomGap

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        canvas.drawLayout(labelLayout[width], left, top)
        canvas.drawLayout(valueLayout[width], left + labelWidth + LABEL_GUTTER, top)
    }
}

/** 첫 장 머리의 이름 블록. 왼쪽에 이름·직함, 오른쪽에 연락처를 바닥선에 맞춰 둔다. */
private class HeaderBlock(fonts: ResumeFonts, private val profile: ProfileData) : PdfBlock() {
    private class Contact(val label: String, val value: String, val emphasized: Boolean)

    private val eyebrow = PdfText(fonts.medium, 9f, INK_MUTED, letterSpacing = 1f)
    private val nameStyle = PdfText(fonts.serifBold, 26f, INK)
    private val titleStyle = PdfText(fonts.medium, 10.5f, PRIMARY)
    private val contactLabel = PdfText(fonts.medium, 7.5f, INK_MUTED, letterSpacing = 0.5f)
    private val contactLink = PdfText(fonts.medium, 9.5f, PRIMARY)
    private val contactPlain = PdfText(fonts.medium, 9.5f, INK_SOFT)

    private val contacts = buildList {
        if (profile.email.isNotBlank()) add(Contact("Email", profile.email, emphasized = true))
        if (profile.phone.isNotBlank()) add(Contact("Phone", profile.phone, emphasized = false))
        profile.github?.let { add(Contact("GitHub", shortUrl(it), emphasized = true)) }
        // Notion 상세 보기는 넣지 않는다. 이력서는 그 자체로 완결된 문서여야 하고,
        // 앱과 사이트에는 그대로 남아 있다.
    }

    private val titleLine =
        if (profile.experience.isBlank()) profile.title else "${profile.title}  ·  경력 ${profile.experience}"

    private val nameLayout = LayoutCache { width -> nameStyle.layout(profile.name, width) }
    private val titleLayout = LayoutCache { width -> titleStyle.layout(titleLine, width) }

    private fun valueStyle(contact: Contact) = if (contact.emphasized) contactLink else contactPlain

    private fun contactColumnWidth(width: Float): Float =
        (contacts.maxOfOrNull { contactLabel.width("${it.label}  ") + valueStyle(it).width(it.value) } ?: 0f)
            .coerceAtMost(width * 0.5f)

    private fun leftWidth(width: Float) =
        if (contacts.isEmpty()) width else width - contactColumnWidth(width) - 20f

    private fun leftHeight(width: Float): Float {
        val lw = leftWidth(width)
        return eyebrow.lineHeight() + 6f + nameLayout[lw].height + 5f + titleLayout[lw].height
    }

    private fun contactLineHeight() = max(contactLabel.lineHeight(), contactPlain.lineHeight())

    private fun contactsHeight() = contacts.size * (contactLineHeight() + CONTACT_GAP)

    private fun bodyHeight(width: Float) = max(leftHeight(width), contactsHeight())

    override fun measure(width: Float) = bodyHeight(width) + 14f + 1.6f

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        val body = bodyHeight(width)
        val lw = leftWidth(width)

        var y = top + body - leftHeight(width)
        val eyebrowPaint = eyebrow.paint()
        canvas.drawText("이력서", left, y - eyebrowPaint.fontMetrics.ascent, eyebrowPaint)
        y += eyebrow.lineHeight() + 6f
        canvas.drawLayout(nameLayout[lw], left, y)
        y += nameLayout[lw].height + 5f
        canvas.drawLayout(titleLayout[lw], left, y)

        val right = left + width
        val columnWidth = contactColumnWidth(width)
        val lineHeight = contactLineHeight()
        var lineTop = top + body - contactsHeight()
        contacts.forEach { contact ->
            val labelPaint = contactLabel.paint()
            val valuePaint = valueStyle(contact).paint()
            val baseline = lineTop + (lineHeight - valueStyle(contact).lineHeight()) - valuePaint.fontMetrics.ascent
            val labelText = "${contact.label}  "
            val labelWidth = labelPaint.measureText(labelText)
            val value = TextUtils.ellipsize(
                contact.value,
                valuePaint,
                max(0f, columnWidth - labelWidth),
                TextUtils.TruncateAt.END,
            ).toString()
            val valueWidth = valuePaint.measureText(value)
            canvas.drawText(value, right - valueWidth, baseline, valuePaint)
            canvas.drawText(labelText, right - valueWidth - labelWidth, baseline, labelPaint)
            lineTop += lineHeight + CONTACT_GAP
        }

        val ruleTop = top + body + 14f
        canvas.drawRect(left, ruleTop, right, ruleTop + 1.6f, fillPaint(PRIMARY))
    }

    private companion object {
        const val CONTACT_GAP = 3f
    }
}

/** 경력 한 건. 기간 | 회사 | 재직 기간, 그 아래 역할. */
private class CareerRowBlock(fonts: ResumeFonts, private val career: CareerHistory) : PdfBlock() {
    private val periodStyle = PdfText(fonts.medium, 9.5f, INK_MUTED)
    private val companyStyle = PdfText(fonts.bold, 10.5f, INK)
    private val durationStyle = PdfText(fonts.medium, 9.5f, INK_MUTED)
    private val roleStyle = PdfText(fonts.regular, 9.5f, INK_SOFT)
    private val duration = career.duration.trim()

    private val periodLayout = LayoutCache { _ -> periodStyle.layout(career.period, LABEL_WIDTH) }
    private val companyLayout = LayoutCache { width -> companyStyle.layout(career.company, companyWidth(width)) }
    private val roleLayout = LayoutCache { width -> roleStyle.layout(career.role, width - LABEL_WIDTH) }

    private fun durationWidth() = if (duration.isEmpty()) 0f else durationStyle.width(duration) + 8f

    private fun companyWidth(width: Float) = width - LABEL_WIDTH - durationWidth()

    private fun firstRowHeight(width: Float) = maxOf(
        periodLayout[width].height.toFloat(),
        companyLayout[width].height.toFloat(),
        if (duration.isEmpty()) 0f else durationStyle.lineHeight(),
    )

    private fun roleHeight(width: Float) = if (career.role.isBlank()) 0f else 2f + roleLayout[width].height

    override fun measure(width: Float) = firstRowHeight(width) + roleHeight(width) + 9f

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        canvas.drawLayout(periodLayout[width], left, top)
        canvas.drawLayout(companyLayout[width], left + LABEL_WIDTH, top)
        if (duration.isNotEmpty()) {
            val paint = durationStyle.paint()
            canvas.drawText(duration, left + width - paint.measureText(duration), top - paint.fontMetrics.ascent, paint)
        }
        if (career.role.isNotBlank()) {
            canvas.drawLayout(roleLayout[width], left + LABEL_WIDTH, top + firstRowHeight(width) + 2f)
        }
    }
}

/**
 * 프로젝트 한 건.
 *
 * 페이지 경계에서 쪼개지지 않도록 하나의 블록으로 다룬다. 한 페이지보다 커질 때만
 * [split] 으로 조각나 흐른다.
 */
private class ProjectBlock(fonts: ResumeFonts, project: ProjectData, private val isLast: Boolean) : PdfBlock() {
    private val parts: List<PdfBlock> = buildList {
        add(ProjectTitleRow(fonts, project))
        add(ProjectMetaRow(fonts, project))
        if (project.description.isNotBlank()) {
            add(
                TextBlock(
                    text = project.description.replace(LineBreaks, " ").trim(),
                    style = PdfText(fonts.regular, 9.5f, INK_SOFT, lineSpacing = 2.8f),
                    marginTop = 6f,
                ),
            )
        }
        project.achievements.forEachIndexed { index, achievement ->
            add(BulletBlock(fonts, achievement, marginTop = if (index == 0) 6f else 0f))
        }
        if (project.techStack.isNotEmpty()) add(TechRow(fonts, project.techStack))
        projectLinks(project).forEachIndexed { index, (label, url) ->
            add(LinkLine(fonts, label, url, marginTop = if (index == 0) 4f else 1f))
        }
    }

    /** 다음 프로젝트와의 사이: 여백 14 + 구분선 + 여백 14. */
    private val separator = if (isLast) 0f else 14f + 0.8f + 14f

    override fun measure(width: Float) = parts.sumOf { it.measure(width).toDouble() }.toFloat() + separator

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        var y = top
        parts.forEach {
            it.draw(canvas, left, y, width)
            y += it.measure(width)
        }
        if (!isLast) canvas.drawRect(left, y + 14f, left + width, y + 14.8f, fillPaint(RULE_LIGHT))
    }

    override fun split(): List<PdfBlock> = if (isLast) parts else parts + SpacerBlock(separator)
}

private fun projectLinks(project: ProjectData): List<Pair<String, String>> = listOfNotNull(
    project.liveUrl?.let { "서비스 링크" to it },
    project.appStoreUrl?.let { "App Store" to it },
    project.playStoreUrl?.let { "Google Play" to it },
    project.githubUrl?.let { "GitHub" to it },
)

private class ProjectTitleRow(fonts: ResumeFonts, private val project: ProjectData) : PdfBlock() {
    private val titleStyle = PdfText(fonts.bold, 11f, INK, lineSpacing = 1.5f)
    private val periodStyle = PdfText(fonts.medium, 9f, INK_MUTED)
    private val titleLayout = LayoutCache { width ->
        titleStyle.layout(project.title, width - periodStyle.width(project.period) - 12f)
    }

    override fun measure(width: Float) = max(titleLayout[width].height.toFloat(), periodStyle.lineHeight())

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        canvas.drawLayout(titleLayout[width], left, top)
        val paint = periodStyle.paint()
        canvas.drawText(project.period, left + width - paint.measureText(project.period), top - paint.fontMetrics.ascent, paint)
    }
}

/** [카테고리 칩] 회사 · 역할 */
private class ProjectMetaRow(fonts: ResumeFonts, project: ProjectData) : PdfBlock() {
    private val chipStyle = PdfText(fonts.medium, 8f, INK_SOFT)
    private val textStyle = PdfText(fonts.medium, 9.5f, PRIMARY)
    private val chipText = project.category.label
    private val text = listOf(project.company, project.role).filter { it.isNotBlank() }.joinToString("  ·  ")
    private val textLayout = LayoutCache { width -> textStyle.layout(text, width - chipWidth() - 6f) }

    private fun chipWidth() = chipStyle.width(chipText) + 10f

    private fun chipHeight() = chipStyle.lineHeight() + 3f

    private fun rowHeight(width: Float) = max(chipHeight(), textLayout[width].height.toFloat())

    override fun measure(width: Float) = MARGIN_TOP + rowHeight(width)

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        val rowTop = top + MARGIN_TOP
        val rowHeight = rowHeight(width)

        val chipTop = rowTop + (rowHeight - chipHeight()) / 2f
        val rect = RectF(left, chipTop, left + chipWidth(), chipTop + chipHeight())
        canvas.drawRoundRect(rect, 2.5f, 2.5f, fillPaint(CHIP_BG))
        canvas.drawRoundRect(rect, 2.5f, 2.5f, strokePaint(RULE, 0.5f))
        val chipPaint = chipStyle.paint()
        canvas.drawText(chipText, left + 5f, chipTop + 1.5f - chipPaint.fontMetrics.ascent, chipPaint)

        val layout = textLayout[width]
        canvas.drawLayout(layout, left + chipWidth() + 6f, rowTop + (rowHeight - layout.height) / 2f)
    }

    private companion object {
        const val MARGIN_TOP = 4f
    }
}

private class BulletBlock(fonts: ResumeFonts, text: String, private val marginTop: Float) : PdfBlock() {
    private val style = PdfText(fonts.regular, 9.5f, INK_SOFT, lineSpacing = 2.6f)
    private val layout = LayoutCache { width -> style.layout(text, width - TEXT_LEFT) }

    override fun measure(width: Float) = marginTop + layout[width].height + 3f

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        val y = top + marginTop
        // 첫 줄 글자의 시각적 중심에 점을 맞춘다.
        val centerY = y - style.paint().fontMetrics.ascent - style.size * 0.36f
        canvas.drawCircle(left + 2f + DOT / 2f, centerY, DOT / 2f, fillPaint(PRIMARY))
        canvas.drawLayout(layout[width], left + TEXT_LEFT, y)
    }

    private companion object {
        const val DOT = 2.6f
        const val TEXT_LEFT = 2f + DOT + 6f
    }
}

private class TechRow(fonts: ResumeFonts, stack: List<String>) : PdfBlock() {
    private val labelStyle = PdfText(fonts.medium, 8.5f, INK_MUTED)
    private val textStyle = PdfText(fonts.regular, 8.5f, INK_MUTED, lineSpacing = 2f)
    private val layout = LayoutCache { width -> textStyle.layout(stack.joinToString(", "), width - labelStyle.width(LABEL)) }

    override fun measure(width: Float) = MARGIN_TOP + max(labelStyle.lineHeight(), layout[width].height.toFloat())

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        val y = top + MARGIN_TOP
        val paint = labelStyle.paint()
        canvas.drawText(LABEL, left, y - paint.fontMetrics.ascent, paint)
        canvas.drawLayout(layout[width], left + paint.measureText(LABEL), y)
    }

    private companion object {
        const val LABEL = "Tech  "
        const val MARGIN_TOP = 5f
    }
}

private class LinkLine(
    fonts: ResumeFonts,
    private val label: String,
    private val url: String,
    private val marginTop: Float,
) : PdfBlock() {
    private val labelStyle = PdfText(fonts.medium, 8.5f, PRIMARY)
    private val urlStyle = PdfText(fonts.regular, 8.5f, INK_MUTED)

    override fun measure(width: Float) = marginTop + max(labelStyle.lineHeight(), urlStyle.lineHeight())

    override fun draw(canvas: Canvas, left: Float, top: Float, width: Float) {
        val labelPaint = labelStyle.paint()
        val urlPaint = urlStyle.paint()
        val baseline = top + marginTop - labelPaint.fontMetrics.ascent
        val labelText = "$label  "
        canvas.drawText(labelText, left, baseline, labelPaint)
        val x = left + labelPaint.measureText(labelText)
        val shown = TextUtils.ellipsize(shortUrl(url), urlPaint, max(0f, left + width - x), TextUtils.TruncateAt.END)
        canvas.drawText(shown.toString(), x, baseline, urlPaint)
    }
}
