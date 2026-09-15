package dev.kevinmuka.orariunimi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class DegreePalette(val accent: Color, val container: Color, val content: Color)

@Composable
fun degreePalette(type: DegreeType): DegreePalette {
    val dark = isSystemInDarkTheme()
    return when (type) {
        DegreeType.BACHELOR -> if (dark) DegreePalette(Color(0xFFA9C5FF), Color(0xFF1C355C), Color.White)
            else DegreePalette(Color(0xFF315BB7), Color(0xFFE4EDFF), Color(0xFF17356E))
        DegreeType.MASTER -> if (dark) DegreePalette(Color(0xFF8DDCCE), Color(0xFF17443F), Color.White)
            else DegreePalette(Color(0xFF087B71), Color(0xFFDCF5EF), Color(0xFF15584F))
        DegreeType.SINGLE_CYCLE -> if (dark) DegreePalette(Color(0xFFD9B4F5), Color(0xFF493259), Color.White)
            else DegreePalette(Color(0xFF8651AE), Color(0xFFF0E5FA), Color(0xFF5B3576))
        DegreeType.OTHER -> DegreePalette(MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DegreeBadge(type: DegreeType) {
    val palette = degreePalette(type)
    Surface(color = palette.container, shape = RoundedCornerShape(8.dp)) {
        Text(type.label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = palette.content, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CourseDetailScreen(
    course: SearchItem, year: String, favorite: Boolean, savedSubjects: List<SavedSubject>,
    onToggleFavorite: () -> Unit, onOpenCalendar: () -> Unit,
    onOpenTeaching: (CourseTeaching) -> Unit, onToggleTeaching: (CourseTeaching) -> Unit
) {
    val type = course.degreeType ?: DegreeType.OTHER
    val palette = degreePalette(type)
    val visiblePaths = course.coursePaths.filter { it.teachings.isNotEmpty() }
    val count = visiblePaths.flatMap { it.teachings }.distinctBy { it.code }.size
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                DegreeBadge(type)
                Text(course.name, Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${yearLabel(year)} · $count insegnamenti disponibili",
                    Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Apri calendario del corso")
                }
                FilledTonalButton(onClick = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(if (favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(if (favorite) "Salvato nei preferiti" else "Salva corso nei preferiti")
                }
                Text(if (course.coursePaths.any { it.code.startsWith("catalog:") }) "INSEGNAMENTI"
                    else "INSEGNAMENTI PER PERCORSO", Modifier.padding(top = 25.dp),
                    style = MaterialTheme.typography.labelMedium, color = palette.accent,
                    fontWeight = FontWeight.Bold)
            }
        }
        if (count == 0) item {
            Text("Il portale non elenca insegnamenti per questo corso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visiblePaths.forEachIndexed { pathIndex, path ->
            item(key = "path:$pathIndex:${path.code}") {
                Text(path.name.ifBlank { "Percorso" }, Modifier.padding(top = 9.dp, bottom = 3.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            itemsIndexed(path.teachings, key = { teachingIndex, teaching ->
                "$pathIndex:$teachingIndex:${teaching.code}"
            }) { _, teaching ->
                val isSaved = savedSubjects.any { it.year == year && it.code == teaching.code }
                Card(onClick = { onOpenTeaching(teaching) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null,
                            tint = palette.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(teaching.name, style = MaterialTheme.typography.titleSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOf(teaching.code, teaching.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onToggleTeaching(teaching) }) {
                            Icon(if (isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Rimuovi ${teaching.name} dai miei orari"
                                    else "Salva ${teaching.name} nei miei orari")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteCoursesScreen(
    favorites: List<FavoriteCourse>, onOpen: (FavoriteCourse) -> Unit,
    onRemove: (FavoriteCourse) -> Unit, onExplore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Column {
                Text("Corsi preferiti", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Ritrova i corsi di laurea e tutti gli insegnamenti dei loro percorsi.",
                    Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onExplore, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Icon(Icons.Outlined.School, contentDescription = null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Cerca un corso di laurea")
                }
            }
        }
        if (favorites.isEmpty()) item {
            Text("Nessun corso preferito. Cerca un corso e tocca il segnalibro per salvarlo.",
                Modifier.padding(top = 26.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(favorites, key = { "${it.year}:${it.code}" }) { favorite ->
            val palette = degreePalette(favorite.degreeType)
            Card(onClick = { onOpen(favorite) }, shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.container)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Outlined.School, contentDescription = null, tint = palette.accent,
                        modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(favorite.name, style = MaterialTheme.typography.titleMedium,
                            color = palette.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${favorite.degreeType.label} · ${yearLabel(favorite.year)}",
                            style = MaterialTheme.typography.labelMedium, color = palette.content)
                    }
                    IconButton(onClick = { onRemove(favorite) }) {
                        Icon(Icons.Outlined.Bookmark, contentDescription = "Rimuovi ${favorite.name} dai preferiti",
                            tint = palette.accent)
                    }
                }
            }
        }
    }
}

private fun yearLabel(year: String): String = year.toIntOrNull()?.let { "$it/${it + 1}" } ?: year
