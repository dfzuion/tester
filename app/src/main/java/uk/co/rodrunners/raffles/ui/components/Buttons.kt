package uk.co.rodrunners.raffles.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes

/**
 * One filled button style in the whole app, and it is gold. Anything that isn't
 * the single most important action on a screen uses the outline or text variant,
 * which is what keeps the gold meaningful.
 */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        enabled = enabled && !loading,
        shape = RrrShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = RrrColors.Gold,
            contentColor = RrrColors.Ink,
            disabledContainerColor = RrrColors.Hairline,
            disabledContentColor = RrrColors.Slate,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = RrrColors.Ink,
            )
            Spacer(Modifier.width(10.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    danger: Boolean = false,
) {
    val accent = if (danger) RrrColors.Danger else RrrColors.Bone
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        enabled = enabled,
        shape = RrrShapes.medium,
        border = BorderStroke(1.dp, if (!enabled) RrrColors.Hairline else if (danger) RrrColors.Danger.copy(alpha = 0.6f) else RrrColors.Gold),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accent,
            disabledContentColor = RrrColors.Slate,
        ),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = RrrColors.Gold)
    }
}

/** Compact "View" pill used on ticket rows. */
@Composable
fun ViewPill(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "View") {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RrrShapes.small,
        border = BorderStroke(1.dp, RrrColors.Hairline),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RrrColors.Bone),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
