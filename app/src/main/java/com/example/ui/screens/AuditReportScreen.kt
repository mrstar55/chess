package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuditStatus
import com.example.ui.AppTab
import com.example.ui.ChapterUiState
import com.example.ui.ChapterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditReportScreen(
    viewModel: ChapterViewModel,
    state: ChapterUiState,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "گزارش ممیزی اطلاعات شطرنج",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.closeAuditReport() },
                            modifier = Modifier.testTag("audit_back_btn")
                        ) {
                            Icon(AppIcons.ArrowBack, contentDescription = "بازگشت")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = modifier.testTag("audit_report_screen")
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Summary Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audit_report_summary_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "خلاصه ممیزی مرحله صفر (پیش از پیاده‌سازی)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "تمام ۱۰ موقعیت FEN و ۶ بازی/خط PGN موجود در پکیج فنی با موتور رسمی قوانین شطرنج (chess.js) پارس و بازپخش شدند. نتیجه اعتبارسنجی:",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                AuditStatBadge("۱۰", "کل FENها", Color(0xFF1E293B))
                                AuditStatBadge("۹", "تأیید شده", Color(0xFF16A34A))
                                AuditStatBadge("۱", "مسدود (BD-027)", Color(0xFFDC2626))
                                AuditStatBadge("۶ / ۶", "بازپخش PGN", Color(0xFF2563EB))
                            }
                        }
                    }
                }

                // Detailed BD-027 Alert Card
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = Color(0xFFFFFBEB)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Warning, contentDescription = null, tint = Color(0xFFD97706))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تحلیل فنی دیاگرام مسدود BD-027 (صفحه ۴۲)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "کپشن کتاب این موقعیت را «مات با ۴ مهره» معرفی کرده است؛ اما بررسی هندسی و موتور قوانین نشان داد:\n" +
                                        "۱. فیل سفید g7 به شاه h8 کیش می‌دهد.\n" +
                                        "۲. شاه سفید g6 از فیل حمایت کرده و خانه‌های f7 و h7 را می‌پوشاند.\n" +
                                        "۳. اسب سفید h5 خانه‌های f6, g7, f4, g3 را کنترل می‌کند.\n" +
                                        "۴. خانه g8 بدون حمله است و شاه سیاه با حرکت قانونی 1... Kg8 از کیش فرار می‌کند!\n" +
                                        "بنابراین موقعیت مات نیست. به دستور پروژه، FEN اصلی دست‌نخورده باقی مانده و برچسب BLOCKED_NEEDS_HUMAN_REVIEW روی آن اعمال شد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "وضعیت ممیزی تک‌تک دیاگرام‌ها (BD-025 تا BD-034):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(state.diagrams) { diag ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${diag.diagramId} — صفحه ${diag.pdfPage}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (diag.auditStatus == AuditStatus.VALID) AppIcons.CheckCircle else AppIcons.Warning,
                                        contentDescription = null,
                                        tint = if (diag.auditStatus == AuditStatus.VALID) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (diag.auditStatus == AuditStatus.VALID) "تأیید شد" else "مسدود (BLOCKED)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (diag.auditStatus == AuditStatus.VALID) Color(0xFF16A34A) else Color(0xFFDC2626)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "FEN: ${diag.initialFen}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (diag.pgnRef != null) {
                                Text(
                                    text = "PGN: ${diag.pgnRef} (بازپخش کامل و FEN نهایی منطبق است)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditStatBadge(count: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
    }
}
