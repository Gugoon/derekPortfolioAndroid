package com.derek.portfolio.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.derek.portfolio.data.repository.AuthState
import com.derek.portfolio.data.repository.AuthUser
import com.derek.portfolio.ui.components.AppSnackbarHost
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.showAppMessage
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 방문 기록 탭의 위치. 시드 동작을 감출 탭을 가리키는 데 쓴다. */
private const val VISITS_TAB = 2

private val TabTitles = listOf("프로필 · 경력 · 스킬", "프로젝트", "방문 기록")

/** 콘텐츠 관리 화면. 로그인 → 권한 확인 → 편집기 순으로 갈아끼운다. */
@Composable
fun AdminScreen(
    onBackToSite: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var seedRequest by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> launch { snackbar.showAppMessage(message) } }
    }

    val user = (authState as? AuthState.SignedIn)?.user
    val ready = gate as? AdminGate.Ready

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            AdminTopBar(
                user = user,
                // 시드 동작은 콘텐츠를 다루는 탭에서만 의미가 있다. 방문 기록 탭에서까지
                // '초기화' 가 보이면 무엇을 지우는 버튼인지 헷갈린다.
                showSeedActions = user != null && ready != null && selectedTab != VISITS_TAB,
                onBack = onBackToSite,
                onSignOut = viewModel::signOut,
                onSeed = { seedRequest = it },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            when (val auth = authState) {
                AuthState.Restoring -> AdminBusy("인증 상태 확인 중")
                AuthState.SignedOut -> SignInForm(
                    form = viewModel.signInForm,
                    onSignIn = viewModel::signIn,
                    onResetPassword = viewModel::sendPasswordReset,
                )
                is AuthState.SignedIn -> when (val current = gate) {
                    AdminGate.Loading -> AdminBusy("콘텐츠 불러오는 중")
                    is AdminGate.Error -> AdminErrorView(
                        icon = Icons.Rounded.CloudOff,
                        message = current.message,
                        onRetry = viewModel::reload,
                        onSignOut = viewModel::signOut,
                    )
                    is AdminGate.NotAdmin -> AdminErrorView(
                        icon = Icons.Outlined.Lock,
                        message = "이 계정(${auth.user.email.orEmpty()})에는 편집 권한이 없습니다.\n" +
                            "Firestore 의 admins 컬렉션에 문서 ID \"${auth.user.uid}\" 를 추가하세요.",
                        onRetry = viewModel::reload,
                        onSignOut = viewModel::signOut,
                    )
                    is AdminGate.Ready -> AdminTabs(
                        ready = current,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    seedRequest?.let { replaceExisting ->
        AlertDialog(
            onDismissRequest = { seedRequest = null },
            containerColor = AppColors.SurfaceCard,
            title = { Text(if (replaceExisting) "기본 데이터로 덮어쓰기" else "기본 데이터 가져오기") },
            text = {
                Text(
                    if (replaceExisting) {
                        "현재 Firestore 의 프로젝트 문서를 모두 지우고, 코드에 내장된 기본 데이터로 다시 채웁니다. 되돌릴 수 없습니다."
                    } else {
                        "코드에 내장된 기본 프로필과 프로젝트를 Firestore 에 넣습니다. 이미 프로젝트가 있으면 아무것도 하지 않습니다."
                    },
                    style = AppFonts.sans(14f, color = AppColors.TextSecondary, height = 1.6f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        seedRequest = null
                        viewModel.seed(replaceExisting)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (replaceExisting) AppColors.Accent else AppColors.Primary,
                    ),
                ) { Text(if (replaceExisting) "덮어쓰기" else "가져오기") }
            },
            dismissButton = {
                TextButton(onClick = { seedRequest = null }) { Text("취소") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTopBar(
    user: AuthUser?,
    showSeedActions: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onSeed: (replaceExisting: Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "포트폴리오 콘텐츠 관리",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = AppFonts.serif(18f, FontWeight.ExtraBold, AppColors.TextPrimary),
                    )
                    user?.email?.let { email ->
                        Text(
                            email,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = AppFonts.mono(11.5f, color = AppColors.TextMuted),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "사이트 보기", tint = AppColors.Primary)
                }
            },
            actions = {
                if (user != null) {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "로그아웃", tint = AppColors.TextMuted)
                    }
                }
                if (showSeedActions) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "기본 데이터 메뉴", tint = AppColors.TextMuted)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = AppColors.SurfaceCard,
                        ) {
                            DropdownMenuItem(
                                text = { Text("기본 데이터 가져오기") },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = AppColors.Primary) },
                                onClick = {
                                    menuOpen = false
                                    onSeed(false)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("초기화", color = AppColors.Accent) },
                                leadingIcon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = AppColors.Accent) },
                                onClick = {
                                    menuOpen = false
                                    onSeed(true)
                                },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppColors.SurfaceCard,
                scrolledContainerColor = AppColors.SurfaceCard,
            ),
        )
        HorizontalDivider(thickness = 1.dp, color = AppColors.Border)
    }
}

/** 진행 표시가 이만큼 이어지면 '느리다' 는 것을 화면에도 적는다. 읽기 제한 시간(8초)보다 짧아야 한다. */
private const val SLOW_HINT_AFTER_MILLIS = 3_000L

@Composable
private fun AdminBusy(message: String) {
    // 아무 변화 없는 동그라미만 돌면 멈춘 화면과 구분이 되지 않아, 기다릴지 다시
    // 열지 판단할 수 없다.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_HINT_AFTER_MILLIS)
        slow = true
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = AppColors.Primary)
        Spacer(Modifier.height(16.dp))
        Text(message, style = AppFonts.sans(13f, color = AppColors.TextMuted))
        if (slow) {
            Spacer(Modifier.height(6.dp))
            Text("응답이 느립니다. 잠시 더 기다려 주세요.", style = AppFonts.sans(12f, color = AppColors.TextMuted))
        }
    }
}

@Composable
private fun SignInForm(
    form: SignInFormState,
    onSignIn: () -> Unit,
    onResetPassword: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(AppDimens.RadiusMd)
    val submit = {
        focusManager.clearFocus()
        onSignIn()
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .paperShadow(shape)
                .background(AppColors.SurfaceCard, shape)
                .border(1.2.dp, AppColors.Border, shape)
                .padding(32.dp),
        ) {
            Text("관리자 로그인", style = AppFonts.serif(22f, FontWeight.ExtraBold, AppColors.TextPrimary))
            Spacer(Modifier.height(6.dp))
            Text(
                "등록된 관리자 계정만 콘텐츠를 수정할 수 있습니다.",
                style = AppFonts.sans(12.5f, color = AppColors.TextMuted, height = 1.6f),
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = form.email,
                onValueChange = { form.email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username },
                label = { Text("이메일") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = adminTextFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.password,
                onValueChange = { form.password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
                label = { Text("비밀번호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = adminTextFieldColors(),
            )
            form.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(error, style = AppFonts.sans(12.5f, color = AppColors.Accent, height = 1.6f))
            }
            Spacer(Modifier.height(24.dp))
            AdminButton(
                label = "로그인",
                onClick = submit,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Rounded.Login,
                busy = form.busy,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onResetPassword,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextMuted),
            ) { Text("비밀번호 재설정 메일 보내기") }
        }
    }
}

@Composable
private fun AdminErrorView(
    icon: ImageVector,
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(icon, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            modifier = Modifier.widthIn(max = 460.dp),
            textAlign = TextAlign.Center,
            style = AppFonts.sans(13.5f, color = AppColors.TextSecondary, height = 1.7f),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminButton(label = "다시 시도", onClick = onRetry, icon = Icons.Rounded.Refresh, outlined = true)
            AdminButton(
                label = "로그아웃",
                onClick = onSignOut,
                icon = Icons.AutoMirrored.Rounded.Logout,
                outlined = true,
                color = AppColors.TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTabs(
    ready: AdminGate.Ready,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    viewModel: AdminViewModel,
) {
    // 탭을 오가도 편집기 스크롤 위치가 남도록 여기서 들고 있는다.
    val profileScroll = rememberScrollState()
    val projectsScroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = AppColors.SurfaceCard,
            contentColor = AppColors.Primary,
            edgePadding = 8.dp,
            divider = { HorizontalDivider(thickness = 1.dp, color = AppColors.Border) },
        ) {
            TabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onSelectTab(index) },
                    selectedContentColor = AppColors.Primary,
                    unselectedContentColor = AppColors.TextMuted,
                    text = { Text(title, style = AppFonts.sans(14f, FontWeight.SemiBold)) },
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (selectedTab) {
                0 -> ProfileEditor(
                    state = ready.profile,
                    scrollState = profileScroll,
                    onSave = { viewModel.saveProfile(ready.profile) },
                )
                1 -> ProjectsEditor(
                    state = ready.projects,
                    scrollState = projectsScroll,
                    onSaveOne = { viewModel.saveProject(ready.projects, it) },
                    onSaveAll = { viewModel.saveAllProjects(ready.projects) },
                    onDelete = { viewModel.deleteProject(ready.projects, it) },
                    onMove = { index, delta -> viewModel.moveProject(ready.projects, index, delta) },
                )
                else -> {
                    LaunchedEffect(ready.visits) { viewModel.loadVisitsIfNeeded(ready.visits) }
                    VisitsView(
                        state = ready.visits,
                        onReload = { viewModel.loadVisits(ready.visits) },
                        onCleanUp = { viewModel.cleanUpVisits(ready.visits, it) },
                    )
                }
            }
        }
    }
}
