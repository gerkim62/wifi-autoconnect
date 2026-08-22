package com.mgeni.autologin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Reusable Material 3 pull-to-refresh wrapper.
 * The pull indicator is completely invisible and unrendered when idle (verticalOffset == 0 and not refreshing),
 * avoiding any top dot/pill artifacts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshLayout(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
            pullRefreshState.endRefresh()
        }
    }

    Box(
        modifier = modifier.nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        content()

        AnimatedVisibility(
            visible = pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PullToRefreshContainer(
                state = pullRefreshState
            )
        }
    }
}
