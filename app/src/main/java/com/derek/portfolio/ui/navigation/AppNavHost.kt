package com.derek.portfolio.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.derek.portfolio.FirebaseConfig
import com.derek.portfolio.ui.admin.AdminScreen
import com.derek.portfolio.ui.portfolio.PortfolioScreen
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppFonts
import kotlinx.serialization.Serializable

@Serializable
data object PortfolioRoute

@Serializable
data object AdminRoute

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = PortfolioRoute) {
        composable<PortfolioRoute> {
            PortfolioScreen(
                onOpenAdmin = { navController.navigate(AdminRoute) { launchSingleTop = true } },
            )
        }
        composable<AdminRoute> {
            if (FirebaseConfig.isConfigured) {
                AdminScreen(
                    // 새 화면을 얹는 대신 pop 해야 원래 포트폴리오 화면으로 돌아가고,
                    // 그 화면이 여기서 고친 내용을 다시 읽어 반영한다.
                    onBackToSite = {
                        if (!navController.popBackStack()) navController.navigate(PortfolioRoute)
                    },
                )
            } else {
                FirebaseUnavailableScreen()
            }
        }
    }
}

@Composable
private fun FirebaseUnavailableScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Firebase 설정이 비어 있어 관리자 화면을 열 수 없습니다.",
            textAlign = TextAlign.Center,
            style = AppFonts.sans(14f, color = AppColors.TextSecondary),
        )
    }
}
