package xyz.lilsus.raylsuite.feature.paymentui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.feature.paymentui.LnurlPayDisplay
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTestTags
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.lnurl_payment_image_description
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.lnurl_payment_recipient

@Composable
internal fun LnurlPayReviewSection(display: LnurlPayDisplay, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(PaymentTestTags.LNURL_PAY_DETAILS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        display.image?.let { image ->
            val bitmap = remember(image) {
                runCatching { image.copyEncodedBytes().decodeToImageBitmap() }.getOrNull()
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription =
                        stringResource(Res.string.lnurl_payment_image_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                    alignment = Alignment.Center
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Res.string.lnurl_payment_recipient, display.domain),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = display.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
