package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PriorityLevel
import com.example.ui.theme.PriorityHigh
import com.example.ui.theme.PriorityHighContainer
import com.example.ui.theme.PriorityLow
import com.example.ui.theme.PriorityLowContainer
import com.example.ui.theme.PriorityMedium
import com.example.ui.theme.PriorityMediumContainer
import com.example.ui.theme.PriorityUrgent
import com.example.ui.theme.PriorityUrgentContainer

@Composable
fun PriorityBadge(
    priority: PriorityLevel,
    urgencyScore: Int? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, dotColor) = when (priority) {
        PriorityLevel.URGENT -> Triple(PriorityUrgentContainer, PriorityUrgent, PriorityUrgent)
        PriorityLevel.HIGH -> Triple(PriorityHighContainer, PriorityHigh, PriorityHigh)
        PriorityLevel.MEDIUM -> Triple(PriorityMediumContainer, PriorityMedium, PriorityMedium)
        PriorityLevel.LOW -> Triple(PriorityLowContainer, PriorityLow, PriorityLow)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            val text = if (urgencyScore != null && urgencyScore > 0) {
                "${priority.label} ($urgencyScore)"
            } else {
                priority.label
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
