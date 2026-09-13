package com.derek.portfolio.ui.portfolio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.derek.portfolio.data.model.CareerHistory
import com.derek.portfolio.data.model.ProjectCategory
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.ui.components.AppSnackbarHost
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.rememberPressState
import com.derek.portfolio.ui.components.screenWidthDp
import com.derek.portfolio.ui.components.showAppMessage
import com.derek.portfolio.ui.resume.ResumePdfDialog
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppFonts
import kotlinx.coroutines.launch

@Composable
fun PortfolioScreen(
    onOpenAdmin: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> launch { snackbar.showAppMessage(message) } }
    }
    // 관리자 화면이 닫히고 이 화면이 다시 앞으로 나온 시점. 뒤로 가기 버튼으로
    // 나오든 '사이트 보기' 로 나오든 같은 자리로 모인다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { AppSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { innerPadding ->
        val content = state.content
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (content == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    color = AppColors.Primary,
                    strokeWidth = 2.4.dp,
                )
            } else {
                PortfolioContent(
                    content = content,
                    isRefreshing = state.isRefreshing,
                    onOpenAdmin = if (viewModel.isAdminAvailable) {
                        {
                            viewModel.onAdminOpened()
                            onOpenAdmin()
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun PortfolioContent(
    content: SiteContent,
    isRefreshing: Boolean,
    onOpenAdmin: (() -> Unit)?,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val widthDp = screenWidthDp()
    val isWide = widthDp > 900
    val columns = if (widthDp > 600) 2 else 1
    val horizontalPadding = if (isWide) 80.dp else 20.dp

    // LazyColumn 은 화면 밖으로 나간 항목을 버리므로, 항목 안의 선택 상태는 여기로 끌어올린다.
    var skillsTab by rememberSaveable { mutableIntStateOf(0) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryName?.let { name -> ProjectCategory.entries.firstOrNull { it.name == name } }
    val heroAppear = remember { MutableTransitionState(false).apply { targetState = true } }

    var careerDialog by remember { mutableStateOf<Pair<CareerHistory, List<ProjectData>>?>(null) }
    var showResume by rememberSaveable { mutableStateOf(false) }

    val filteredRows = remember(content.projects, category, columns) {
        content.projects
            .filter { category == null || it.category == category }
            .chunked(columns)
    }
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 400.dp.roundToPx() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets.systemBars.asPaddingValues(),
        ) {
            item(key = "hero") {
                HeroSection(
                    profile = content.profile,
                    isWide = isWide,
                    appearState = heroAppear,
                    onViewProjects = { scope.launch { listState.animateScrollToItem(PROJECTS_HEADER_INDEX) } },
                )
            }
            item(key = "skills") {
                SkillsSection(
                    profile = content.profile,
                    projects = content.projects,
                    isWide = isWide,
                    selectedTab = skillsTab,
                    onSelectTab = { skillsTab = it },
                    onOpenCareer = { career, projects -> careerDialog = career to projects },
                )
            }
            item(key = "projects-header") {
                ProjectsHeader(
                    projects = content.projects,
                    selected = category,
                    onSelect = { categoryName = it?.name },
                    horizontalPadding = horizontalPadding,
                )
            }
            items(
                items = filteredRows,
                key = { row -> "project-" + (row.first().id ?: row.first().title) },
            ) { row ->
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .background(AppColors.Background)
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    row.forEach { project -> ProjectSheet(project, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "projects-footer") {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(AppColors.Background),
                )
            }
            item(key = "contact") { ContactSection(content.profile, isWide) }
            item(key = "footer") { FooterSection(onAdminClick = onOpenAdmin) }
        }

        // 관리자 화면에서 돌아와 다시 읽는 중임을 알리는 가는 띠. 보고 있던 내용을
        // 그대로 두고 갱신하므로, 이것마저 없으면 아무 일도 일어나지 않는 것처럼 보인다.
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = AppColors.Primary,
                trackColor = Color.Transparent,
            )
        }

        FloatingActions(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp),
            showScrollToTop = showScrollToTop,
            showResumeLabel = widthDp >= 720,
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
            onOpenResume = { showResume = true },
        )
    }

    careerDialog?.let { (career, projects) ->
        CareerProjectsDialog(career = career, projects = projects, onDismiss = { careerDialog = null })
    }
    if (showResume) {
        ResumePdfDialog(content = content, onDismiss = { showResume = false })
    }
}

/** hero, skills 다음 항목. '프로젝트 열람하기' 가 스크롤해 갈 자리. */
private const val PROJECTS_HEADER_INDEX = 2

/** 이력서 PDF · 맨 위로. */
@Composable
private fun FloatingActions(
    modifier: Modifier,
    showScrollToTop: Boolean,
    showResumeLabel: Boolean,
    onScrollToTop: () -> Unit,
    onOpenResume: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        // 스크롤을 내렸을 때만 보인다. 사라질 때도 자리는 남겨 두어야 아래 이력서
        // 버튼이 위아래로 흔들리지 않는다.
        val alpha by animateFloatAsState(if (showScrollToTop) 1f else 0f, tween(300), label = "toTopAlpha")
        val offsetY by animateFloatAsState(if (showScrollToTop) 0f else 22f, tween(300), label = "toTopOffset")
        Box(
            Modifier
                .offset(y = offsetY.dp)
                .alpha(alpha)
                .size(44.dp)
                .paperShadow(CircleShape)
                .background(AppColors.SurfaceCard, CircleShape)
                .border(1.5.dp, AppColors.Primary, CircleShape)
                .clip(CircleShape)
                .clickable(enabled = showScrollToTop, onClickLabel = "맨 위로", role = Role.Button, onClick = onScrollToTop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = "맨 위로", tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        ResumePdfButton(showLabel = showResumeLabel, onClick = onOpenResume)
    }
}

/** 이력서 PDF 미리보기를 여는 버튼. 좁은 화면에서는 원형 아이콘만 남겨 본문을 가리지 않게 한다. */
@Composable
private fun ResumePdfButton(showLabel: Boolean, onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(24.dp)
    val background by animateColorAsState(
        if (pressed) AppColors.PrimaryLight else AppColors.Primary,
        tween(200),
        label = "resumeButton",
    )
    Row(
        Modifier
            .height(48.dp)
            .paperShadow(shape, glow = pressed)
            .background(background, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, onClickLabel = "이력서 PDF 미리보기", role = Role.Button, onClick = onClick)
            .padding(horizontal = if (showLabel) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "이력서 PDF", tint = Color.White, modifier = Modifier.size(20.dp))
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text("이력서 PDF", style = AppFonts.sans(13.5f, FontWeight.SemiBold, Color.White))
        }
    }
}
