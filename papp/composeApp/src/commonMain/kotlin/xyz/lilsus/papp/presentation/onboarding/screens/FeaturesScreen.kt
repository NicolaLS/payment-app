package xyz.lilsus.papp.presentation.onboarding.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.onboarding_features_continue
import papp.composeapp.generated.resources.onboarding_features_page1_body
import papp.composeapp.generated.resources.onboarding_features_page1_subtitle
import papp.composeapp.generated.resources.onboarding_features_page1_title
import papp.composeapp.generated.resources.onboarding_features_page2_body
import papp.composeapp.generated.resources.onboarding_features_page2_subtitle
import papp.composeapp.generated.resources.onboarding_features_page2_title
import papp.composeapp.generated.resources.onboarding_features_page3_body
import papp.composeapp.generated.resources.onboarding_features_page3_subtitle
import papp.composeapp.generated.resources.onboarding_features_page3_title
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

data class FeaturePageContent(val title: String, val subtitle: String, val body: String)

// Auto-rotation timing
private const val INITIAL_DELAY_MS = 1800L  // Quick first transition to hint it's a carousel
private const val AUTO_ADVANCE_INTERVAL_MS = 5500L  // Comfortable reading time for subsequent pages
private const val PAGE_ANIMATION_MS = 600  // Smooth page transition

@Composable
fun FeaturesScreen(
    currentPage: Int,
    totalPages: Int,
    onPageChanged: (Int) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onRequestCameraPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = currentPage) { totalPages }

    // Track if user has manually interacted with the pager
    var userHasInteracted by remember { mutableStateOf(false) }

    // Detect manual user interaction (swipe/drag)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collectLatest { isScrolling ->
            if (isScrolling && !pagerState.isScrollInProgress) {
                // User initiated a scroll
                userHasInteracted = true
            }
        }
    }

    // Report page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChanged(page)
        }
    }

    // Sync with external page state
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.scrollToPage(currentPage)
        }
    }

    // Auto-rotation effect
    LaunchedEffect(pagerState, userHasInteracted) {
        if (userHasInteracted) return@LaunchedEffect  // Stop auto-rotation after user interaction

        // Initial delay before first auto-advance (shorter to hint it's a carousel)
        delay(INITIAL_DELAY_MS)

        while (!userHasInteracted && pagerState.currentPage < totalPages - 1) {
            // Animate to next page
            pagerState.animateScrollToPage(
                page = pagerState.currentPage + 1,
                animationSpec = tween(durationMillis = PAGE_ANIMATION_MS)
            )

            // Wait before next auto-advance
            if (pagerState.currentPage < totalPages - 1) {
                delay(AUTO_ADVANCE_INTERVAL_MS)
            }
        }
    }

    // Also stop auto-rotation if user drags
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect {
            // If the settled page differs from what auto-rotation would set,
            // the user likely swiped manually
            if (pagerState.isScrollInProgress) {
                userHasInteracted = true
            }
        }
    }

    val pages = listOf(
        FeaturePageContent(
            title = stringResource(Res.string.onboarding_features_page1_title),
            subtitle = stringResource(Res.string.onboarding_features_page1_subtitle),
            body = stringResource(Res.string.onboarding_features_page1_body)
        ),
        FeaturePageContent(
            title = stringResource(Res.string.onboarding_features_page2_title),
            subtitle = stringResource(Res.string.onboarding_features_page2_subtitle),
            body = stringResource(Res.string.onboarding_features_page2_body)
        ),
        FeaturePageContent(
            title = stringResource(Res.string.onboarding_features_page3_title),
            subtitle = stringResource(Res.string.onboarding_features_page3_subtitle),
            body = stringResource(Res.string.onboarding_features_page3_body)
        )
    )

    OnboardingScaffold(
        currentStep = OnboardingStep.Features,
        onBack = onBack
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                FeatureCard(
                    content = pages[page],
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PageIndicator(
                pagerState = pagerState,
                pageCount = totalPages
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // Request camera permission when leaving the scanner feature page
                    onRequestCameraPermission()
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_features_continue))
            }
        }
    }
}

@Composable
private fun FeatureCard(content: FeaturePageContent, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = content.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = content.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PageIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == pagerState.currentPage

            // Animate size change
            val size by animateDpAsState(
                targetValue = if (isSelected) 10.dp else 8.dp,
                animationSpec = tween(durationMillis = 300),
                label = "indicatorSize"
            )

            // Get colors
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (isSelected) activeColor else inactiveColor)
            )
        }
    }
}
