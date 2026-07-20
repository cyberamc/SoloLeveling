package com.sololeveling.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class LunaCard(
    val title: String,
    val lines: List<String>,
    val bg: Color,
    val titleColor: Color,
    val bodyColor: Color
)

@Composable
private fun LunaCardBox(card: LunaCard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(card.bg, shape = RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(
            text = card.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = card.titleColor
        )
        Spacer(modifier = Modifier.height(6.dp))
        card.lines.forEach { line ->
            Text(
                text = line,
                fontSize = 13.sp,
                color = card.bodyColor,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun LunaSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
fun LunaScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val green = LunaCard(
        title = "Weekly rice batch",
        lines = listOf(
            "1¼ cups dry rice",
            "= about 3½ cups cooked",
            "Fridge, sealed. Use within 5 days.",
            "Cook fresh each week."
        ),
        bg = Color(0xFF14432A),
        titleColor = Color(0xFFB6F2CE),
        bodyColor = Color(0xFF8FD9B0)
    )

    val blue = LunaCard(
        title = "Daily total",
        lines = listOf(
            "1 cup dry food",
            "½ cup cooked rice",
            "Split into two meals:",
            "½ cup kibble + ¼ cup rice each"
        ),
        bg = Color(0xFF16386B),
        titleColor = Color(0xFFBBD6FF),
        bodyColor = Color(0xFF93B8E8)
    )

    val perMeal = listOf(
        LunaCard(
            title = "1. Warm",
            lines = listOf("¼ cup rice", "20–30 sec, splash water"),
            bg = Color(0xFF2B2B2B),            titleColor = Color.White, bodyColor = Color(0xFFB0B0B0)
        ),
        LunaCard(
            title = "2. Add grease",
            lines = listOf("½ tsp, off the heat", "Beef better than bacon"),
            bg = Color(0xFF6B4A12),            titleColor = Color(0xFFFFD98A), bodyColor = Color(0xFFF0C36A)
        ),
        LunaCard(
            title = "3. Mix kibble",
            lines = listOf("½ cup, stir in warm", "Rest 1 min"),
            bg = Color(0xFF2B2B2B),            titleColor = Color.White, bodyColor = Color(0xFFB0B0B0)
        ),
        LunaCard(
            title = "4. Serve",
            lines = listOf("Warm, not hot", "Test with finger"),
            bg = Color(0xFF14432A),            titleColor = Color(0xFFB6F2CE), bodyColor = Color(0xFF8FD9B0)
        )
    )

    val boosters = listOf(
        LunaCard(
            title = "Egg",
            lines = listOf("Crack into hot rice, stir", "Cheapest protein add"),
            bg = Color(0xFF3B2F6B),            titleColor = Color(0xFFD6CBFF), bodyColor = Color(0xFFB3A6E8)
        ),
        LunaCard(
            title = "Bouillon",
            lines = listOf("¼ cube per cup dry rice", "Cook rice in it, not after"),
            bg = Color(0xFF3B2F6B),            titleColor = Color(0xFFD6CBFF), bodyColor = Color(0xFFB3A6E8)
        ),
        LunaCard(
            title = "Canned fish liquid",
            lines = listOf("1 tbsp of the water", "Scents the whole bowl"),
            bg = Color(0xFF3B2F6B),            titleColor = Color(0xFFD6CBFF), bodyColor = Color(0xFFB3A6E8)
        )
    )

    val watchOut = LunaCard(
        title = "Watch out for",
        lines = listOf(
            "Onion and garlic powder in bouillon — toxic. Check the label.",
            "Too much grease causes pancreatitis. Half a teaspoon, no more.",
            "Rice stays the minority. Kibble is what keeps her nutritionally complete."
        ),
        bg = Color(0xFF5C1B1B),        titleColor = Color(0xFFFFC2C2), bodyColor = Color(0xFFE9A0A0)
    )

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "←",
                    fontSize = 24.sp,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
                )
                Text(
                    text = "Luna — daily feeding card",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Approx. 20–25 lb dog. Adjust if she gains or loses weight.",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Weekly batch + daily total, side by side
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LunaCardBox(green, modifier = Modifier.weight(1f))
                    LunaCardBox(blue, modifier = Modifier.weight(1f))
                }
            }

            item { LunaSectionHeader("Per meal") }

            // Per-meal steps, two per row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    perMeal.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { c -> LunaCardBox(c, modifier = Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { LunaSectionHeader("Flavor boosters — pick one") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    boosters.forEach { b ->
                        LunaCardBox(b, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item { LunaCardBox(watchOut, modifier = Modifier.fillMaxWidth()) }
        }
    }
}
