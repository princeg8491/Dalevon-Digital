package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.OrbitProfileBadge
import com.example.ui.theme.OrbitPixelBadgeTone
import com.example.ui.theme.rememberOrbitPixelBadgeColors

fun orbitProfileBadgeIcon(badge: OrbitProfileBadge): ImageVector = when (badge) {
    OrbitProfileBadge.CREATOR -> Icons.Outlined.AutoAwesome
    OrbitProfileBadge.YOUTUBER -> Icons.Outlined.SmartDisplay
    OrbitProfileBadge.INFLUENCER -> Icons.Outlined.Campaign
    OrbitProfileBadge.DEVELOPER -> Icons.Outlined.Code
}

fun orbitProfileBadgeTone(badge: OrbitProfileBadge): OrbitPixelBadgeTone = when (badge) {
    OrbitProfileBadge.CREATOR -> OrbitPixelBadgeTone.AMBER
    OrbitProfileBadge.YOUTUBER -> OrbitPixelBadgeTone.PINK
    OrbitProfileBadge.INFLUENCER -> OrbitPixelBadgeTone.PEACH_ORANGE
    OrbitProfileBadge.DEVELOPER -> OrbitPixelBadgeTone.SKY_BLUE
}

@Composable
fun SpaceAdminBadgePill(
    modifier: Modifier = Modifier,
    onDarkOverlay: Boolean = false,
    testTag: String = "space_admin_badge"
) {
    val label = stringResource(R.string.badge_admin)
    val accessibilityDesc = stringResource(R.string.badge_space_admin_desc)
    val containerColor = if (onDarkOverlay) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (onDarkOverlay) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val borderColor = if (onDarkOverlay) {
        Color.White.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(0.6.dp, borderColor),
        modifier = modifier
            .semantics { contentDescription = accessibilityDesc }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AdminPanelSettings,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun OrbitProfileBadgePill(
    badge: OrbitProfileBadge,
    modifier: Modifier = Modifier,
    testTag: String = "profile_badge_${badge.id}"
) {
    val palette = rememberOrbitPixelBadgeColors(orbitProfileBadgeTone(badge))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.container,
        border = BorderStroke(0.6.dp, palette.content.copy(alpha = 0.22f)),
        modifier = modifier
            .semantics { contentDescription = "${badge.label} badge" }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = orbitProfileBadgeIcon(badge),
                contentDescription = null,
                tint = palette.content,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = badge.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.content,
                maxLines = 1
            )
        }
    }
}
