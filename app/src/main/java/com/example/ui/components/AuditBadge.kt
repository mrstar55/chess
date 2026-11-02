package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import com.example.ui.theme.AppIcons
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

@Composable
fun AuditBadge(
    status: AuditStatus,
    warningText: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        val (bgColor, contentColor, icon, label) = when (status) {
            AuditStatus.VALID -> Quadruple(
                Color(0xFFE8F5E9),
                Color(0xFF2E7D32),
                AppIcons.CheckCircle,
                "تأیید ممیزی قوانین (VALID)"
            )
            AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW -> Quadruple(
                Color(0xFFFFF3E0),
                Color(0xFFD84315),
                AppIcons.Warning,
                "⚠️ نیازمند بازبینی انسانی (BLOCKED)"
            )
        }

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
                ?: "این نمودار توسط کتابخانه رسمی شطرنج (chess.js) ممیزی شده و صحت تعداد شاه‌ها، قانونی بودن FEN و حرکات بازپخش تأیید گردیده است."
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (status == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW) Color(0xFFFFEBEE) else Color(0xFFF1F8E9),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = detailMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW) Color(0xFFC62828) else Color(0xFF33691E),
                    modifier = Modifier.padding(8.dp),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
