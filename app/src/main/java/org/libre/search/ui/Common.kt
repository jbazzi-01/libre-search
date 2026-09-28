package org.libre.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import org.libre.search.core.Text as T
import org.libre.search.ui.theme.Brand
import org.libre.search.ui.theme.LocalLibre

@Composable
fun LibreLogo(size: TextUnit = 44.sp) {
    val colors = listOf(Brand.blue, Brand.red, Brand.yellow, Brand.blue, Brand.green)
    val word = "Libre"
    Text(
        buildAnnotatedString {
            word.forEachIndexed { i, ch ->
                withStyle(SpanStyle(color = colors[i % colors.size])) { append(ch) }
            }
        },
        fontSize = size,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
    )
}

/** The rounded search pill used on Home and on the results page. */
@Composable
fun SearchPill(text: String, placeholder: String = "Search", modifier: Modifier = Modifier, height: Dp = 52.dp, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val c = LocalLibre.current
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text.ifBlank { placeholder },
            color = if (text.isBlank()) c.textSecondary else c.text,
            fontSize = 17.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: @Composable (() -> Unit)? = null) {
    val c = LocalLibre.current
    if (url.isNullOrBlank()) {
        Box(modifier.background(c.surface2), contentAlignment = Alignment.Center) { fallback?.invoke() }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        error = { Box(Modifier.background(c.surface2), contentAlignment = Alignment.Center) { fallback?.invoke() } },
    )
}

@Composable
fun Favicon(pageUrl: String, iconUrl: String?, size: Dp = 26.dp) {
    val c = LocalLibre.current
    Box(
        Modifier.size(size).clip(CircleShape).background(if (c.isDark) Color(0xFFE8EAED) else Color(0xFFF1F3F4))
            .border(1.dp, c.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        NetImage(
            iconUrl ?: T.faviconUrl(pageUrl),
            Modifier.size(size * 0.62f),
            ContentScale.Fit,
            fallback = { Icon(Icons.Default.Language, null, tint = Color(0xFF5F6368), modifier = Modifier.size(size * 0.6f)) }
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val c = LocalLibre.current
    Text(text, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Normal, modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalLibre.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
    ) { content() }
}

@Composable
fun ThickDivider() {
    val c = LocalLibre.current
    Box(Modifier.fillMaxWidth().height(8.dp).background(if (c.isDark) Color(0xFF17181A) else Color(0xFFF1F3F4)))
}

@Composable
fun ThinDivider(padding: Dp = 16.dp) {
    val c = LocalLibre.current
    HorizontalDivider(Modifier.padding(horizontal = padding), color = c.outline, thickness = 1.dp)
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalLibre.current.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun Message(title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalLibre.current
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = c.textSecondary, fontSize = 14.sp)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(c.accent).clickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text(action, color = if (c.isDark) Color(0xFF062E6F) else Color.White, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = LocalLibre.current
    Row(
        modifier.clip(RoundedCornerShape(50)).border(1.dp, c.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(6.dp)) }
        Text(text, color = c.link, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Label(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
