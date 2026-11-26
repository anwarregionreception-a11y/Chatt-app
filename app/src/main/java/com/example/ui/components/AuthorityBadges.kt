package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import com.example.domain.AuthorityEngine
import com.example.domain.BadgeCount
import com.example.domain.BadgeType

fun badgeColor(type: BadgeType): Color = when (type) {
    BadgeType.WHITE_DIAMOND -> Color(0xFFF8FAFC)
    BadgeType.BLACK_GEM -> Color(0xFF1E293B)
    BadgeType.YELLOW_GEM -> Color(0xFFEAB308)
    BadgeType.PURPLE_GEM -> Color(0xFF9333EA)
    BadgeType.CYAN_GEM -> Color(0xFF06B6D4)
    BadgeType.GREEN_GEM -> Color(0xFF16A34A)
    BadgeType.BLUE_GEM -> Color(0xFF2563EB)
    BadgeType.RED_GEM -> Color(0xFFDC2626)
    BadgeType.DARK_GOLD_STAR -> Color(0xFFB45309)
    BadgeType.SILVER_STAR -> Color(0xFF94A3B8)
    BadgeType.LIGHT_GOLD_STAR -> Color(0xFFFBBF24)
    BadgeType.ORANGE_STAR -> Color(0xFFF97316)
    BadgeType.OUTLINE_STAR -> Color(0xFF0B6E5B)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorityBadgesRow(
    authorityCoins: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val badges = AuthorityEngine.coinsToBadges(authorityCoins)
    if (badges.isEmpty()) {
        Text(
            text = "No Badges Yet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val shown = if (compact) badges.take(4) else badges
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEach { item ->
            BadgeChip(item)
        }
        if (compact && badges.size > 4) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "+${badges.size - 4} more",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun BadgeChip(item: BadgeCount) {
    val tint = badgeColor(item.badge)
    val bgTint = if (item.badge == BadgeType.WHITE_DIAMOND) Color(0xFFE2E8F0) else tint.copy(alpha = 0.14f)
    val textTint = if (item.badge == BadgeType.WHITE_DIAMOND) Color(0xFF0F172A) else tint
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgTint,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = when {
                    item.badge.isGem -> Icons.Filled.Diamond
                    item.badge == BadgeType.OUTLINE_STAR -> Icons.Outlined.StarOutline
                    else -> Icons.Filled.Star
                },
                contentDescription = item.badge.displayName,
                tint = if (item.badge == BadgeType.WHITE_DIAMOND) Color(0xFF475569) else tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "${item.count} ${item.badge.displayName}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textTint
            )
        }
    }
}

@Composable
fun LevelPill(level: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "Level $level",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun RolePill(user: UserEntity, modifier: Modifier = Modifier) {
    val label = when (user.role) {
        UserRoles.OWNER -> "OWNER"
        UserRoles.DEVELOPER -> "DEV L${user.developerLevel}"
        else -> null
    } ?: return
    val bg = if (user.role == UserRoles.OWNER) Color(0xFFFEF3C7) else Color(0xFFDBEAFE)
    val fg = if (user.role == UserRoles.OWNER) Color(0xFF92400E) else Color(0xFF1E40AF)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun UserAvatar(
    name: String,
    imageUri: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "R" }
    val palette = listOf(
        Color(0xFF0B6E5B), Color(0xFF2563EB), Color(0xFF9333EA),
        Color(0xFFD97706), Color(0xFFDC2626), Color(0xFF0D9488)
    )
    val bgColor = palette[kotlin.math.abs(name.hashCode()) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUri.isNullOrBlank()) {
            AsyncImage(
                model = imageUri,
                contentDescription = "$name profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
