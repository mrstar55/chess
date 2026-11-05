package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuditStatus
import com.example.ui.theme.AppIcons

/**
 * Full-width audit warning banner rendered ONLY when [status] is [AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW].
 * For [AuditStatus.VALID] diagrams, no full-width green banner is rendered above the board; instead,
 * [CompactValidAuditCheckIcon] is placed inside the card header row with zero extra vertical height.
 */
@Composable
fun AuditBadge(
    status: AuditStatus,
    warningText: String? = null,
    modifier: Modifier = Modifier
) {
    if (status == AuditStatus.VALID) {
        return
    }

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        val bgColor = Color(0xFFFFF3E0)
        val contentColor = Color(0xFFD84315)
        val icon = AppIcons.Warning
        val label = "⚠️ نیازمند بازبینی انسانی (BLOCKED)"

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = bgColor,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .testTag("audit_badge_${status.name}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = "جزییات ممیزی",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded || status == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW) {
            val detailMsg = warningText
                ?: "⚠️ اخطار ممیزی شطرنج: این دیاگرام مسدود شده و نیازمند بازبینی انسانی است."
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = detailMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(8.dp),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Compact green check icon rendered inline inside the diagram header row for [AuditStatus.VALID] diagrams.
 * Consumes 0dp extra vertical height relative to the card header.
 */
@Composable
fun CompactValidAuditCheckIcon(
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = AppIcons.CheckCircle,
        contentDescription = "معتبر",
        tint = Color(0xFF2E7D32),
        modifier = modifier
            .size(16.dp)
            .testTag("audit_valid_compact_icon")
    )
}
