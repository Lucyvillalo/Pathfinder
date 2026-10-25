package com.example.pathfinder

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Ink = Color(0xFF081426)
private val Panel = Color(0xFF10243D)
private val Panel2 = Color(0xFF152C49)
private val Violet = Color(0xFF7657FF)
private val Mint = Color(0xFF36D6B0)
private val Orange = Color(0xFFFFB454)
private val Muted = Color(0xFF9AAFC8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, PathfinderDatabase::class.java, "pathfinder.db").addMigrations(MIGRATION_1_2).build()
        val auth = FirebaseApp.getApps(applicationContext).firstOrNull()?.let { FirebaseAuth.getInstance(it) }
        setContent { PathfinderApp(db.dao(), applicationContext, auth) }
    }
}

@Composable
private fun PathfinderApp(dao: PathfinderDao, context: android.content.Context, auth: FirebaseAuth?) {
    val scope = rememberCoroutineScope()
    val prefs by context.pathfinderPrefs.data.collectAsStateWithLifecycle(initialValue = emptyPreferences())
    val profile by dao.profile().collectAsStateWithLifecycle(initialValue = null)
    val progress by dao.progress().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by dao.experience().collectAsStateWithLifecycle(initialValue = emptyList())
    val lessonProgress by dao.lessons().collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by dao.favorites().collectAsStateWithLifecycle(initialValue = emptyList())
    val readingHistory by dao.readingHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    val dark = when (prefs[PrefKeys.theme]) { "claro" -> false; "oscuro" -> true; else -> isSystemInDarkTheme() }
    var tab by rememberSaveable { mutableStateOf("Inicio") }
    var onboardingStep by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var selectedGoals by rememberSaveable { mutableStateOf(setOf("Ser más productiva")) }
    var level by rememberSaveable { mutableStateOf("Principiante") }
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    var chosenMission by remember { mutableStateOf<Mission?>(null) }
    var selectedArticle by remember { mutableStateOf<String?>(null) }
    var selectedLesson by remember { mutableStateOf<Lesson?>(null) }
    var showLessons by rememberSaveable { mutableStateOf(false) }
    var accountScreen by rememberSaveable { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val authUserState = remember(auth) { mutableStateOf(auth?.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { authUserState.value = it.currentUser }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    val authUser = authUserState.value
    val activity = context as? Activity
    val done = progress.filter { it.completed }.map { it.missionId }.toSet()
    val xp = transactions.sumOf { it.xp }
    val currentLevel = PathfinderRules.levelFor(xp)
    val streak = PathfinderRules.currentStreak(transactions.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet())
    val missionOfDay = PathfinderRules.missionForDay(LocalDate.now(), done, profile?.goals?.split("|")?.toSet() ?: emptySet())
    val appColor = if (dark) Ink else Color(0xFFF4F6FC)

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Violet, secondary = Mint, background = appColor, surface = Panel, onBackground = Color.White, onSurface = Color.White) else lightColorScheme(primary = Violet, secondary = Mint, background = appColor, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = appColor) {
            if (profile?.onboarded != true) {
                Onboarding(
                    step = onboardingStep, name = name, goals = selectedGoals, level = level, minutes = minutes,
                    onName = { name = it }, onGoals = { selectedGoals = if (it in selectedGoals) selectedGoals - it else selectedGoals + it },
                    onLevel = { level = it }, onMinutes = { minutes = it },
                    onNext = { if (onboardingStep < 6) onboardingStep++ else scope.launch {
                        val safeName = name.trim().ifBlank { "Pathfinder" }
                        dao.saveProfile(ProfileEntity(name = safeName, goals = selectedGoals.joinToString("|"), minutes = minutes, level = level, onboarded = true))
                        context.pathfinderPrefs.edit { it[PrefKeys.name] = safeName; it[PrefKeys.onboarding] = true; it[PrefKeys.dailyMinutes] = minutes; it[PrefKeys.goalIds] = selectedGoals.joinToString("|") }
                    } }, onBack = { onboardingStep = (onboardingStep - 1).coerceAtLeast(0) }, onSkip = { onboardingStep = 4 }
                )
            } else if (accountScreen) {
                FirebaseAccountPage(
                    configured = auth != null,
                    user = authUser,
                    busy = authBusy,
                    error = authError,
                    onBack = { accountScreen = false; authError = null },
                    onGoogleSignIn = {
                        if (auth != null && activity != null) scope.launch {
                            authBusy = true; authError = null
                            try { firebaseGoogleSignIn(activity, auth) } catch (e: Exception) { authError = authErrorMessage(e) } finally { authBusy = false }
                        }
                    },
                    onSignOut = {
                        auth?.signOut()
                        scope.launch { runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) } }
                    }
                )
            } else if (selectedArticle != null) {
                val title = selectedArticle!!
                ArticlePage(title, title in favorites.map { it.title }, { scope.launch { if (title in favorites.map { it.title }) dao.removeFavorite(title) else dao.saveFavorite(FavoriteArticleEntity(title, LocalDate.now().toString())) } }, { selectedArticle = null })
            } else if (showLessons) {
                LessonsCatalog(lessonProgress, { selectedLesson = it; showLessons = false }, { showLessons = false })
            } else if (selectedLesson != null) {
                LessonPage(selectedLesson!!, lessonProgress.firstOrNull { it.lessonId == selectedLesson!!.id }, { selectedLesson = null }, { score -> scope.launch { val old = lessonProgress.firstOrNull { it.lessonId == selectedLesson!!.id }; dao.saveLesson(LessonProgressEntity(selectedLesson!!.id, score == 100 || old?.completed == true, maxOf(score, old?.score ?: 0), if(score == 100) LocalDate.now().toString() else old?.completedDate)) } })
            } else if (chosenMission != null) {
                MissionPage(chosenMission!!, chosenMission!!.id in done, xp, { chosenMission = null }, { scope.launch {
                    val m = chosenMission!!
                    dao.awardMission(MissionProgressEntity(missionId = m.id, completed = true, completedDate = LocalDate.now().toString()), ExperienceEntity(missionId = m.id, xp = m.xp, date = LocalDate.now().toString()))
                    chosenMission = null
                } })
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            "Inicio" -> HomeScreen(profile?.name ?: "", currentLevel, xp, streak, missionOfDay, done, { chosenMission = it }, { tab = "Rutas" }, { tab = "Biblioteca" }, { showLessons = true })
                            "Rutas" -> RoutesScreen(done, { chosenMission = it }, { selectedArticle = "Biblioteca" })
                            "Estadísticas" -> StatisticsScreen(done.size, xp, progress)
                            "Perfil" -> ProfileScreen(profile?.name ?: "", xp, currentLevel, done.size, streak, profile?.minutes ?: (prefs[PrefKeys.dailyMinutes] ?: 15), prefs[PrefKeys.theme] ?: "oscuro", authUser?.email, { accountScreen = true }, { value -> scope.launch { context.pathfinderPrefs.edit { it[PrefKeys.theme] = value } } }, { newName -> scope.launch { dao.saveProfile((profile ?: ProfileEntity()).copy(name = newName)); context.pathfinderPrefs.edit { it[PrefKeys.name] = newName } } }, { value -> scope.launch { dao.saveProfile((profile ?: ProfileEntity()).copy(minutes = value)); context.pathfinderPrefs.edit { it[PrefKeys.dailyMinutes] = value } } }, { scope.launch { dao.clearProfile(); dao.clearProgress(); dao.clearExperience(); dao.clearLessons(); dao.clearFavorites(); dao.clearReadingHistory(); context.pathfinderPrefs.edit { it.clear() } } })
                            else -> LibraryScreen(favorites.map { it.title }.toSet(), readingHistory.map { it.title }, { title -> scope.launch { dao.recordRead(ReadingHistoryEntity(title, LocalDate.now().toString())); selectedArticle = title } }, { title -> scope.launch { if (title in favorites.map { it.title }) dao.removeFavorite(title) else dao.saveFavorite(FavoriteArticleEntity(title, LocalDate.now().toString())) } })
                        }
                    }
                    NavigationBar(containerColor = if (dark) Color(0xFF0D1D32) else Color.White) {
                        listOf("Inicio" to Icons.Filled.Home, "Rutas" to Icons.Filled.Add, "Estadísticas" to Icons.Filled.Info, "Perfil" to Icons.Filled.AccountCircle).forEach { (label, icon) ->
                            NavigationBarItem(selected = tab == label, onClick = { tab = label }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Onboarding(step: Int, name: String, goals: Set<String>, level: String, minutes: Int, onName: (String)->Unit, onGoals:(String)->Unit, onLevel:(String)->Unit, onMinutes:(Int)->Unit, onNext:()->Unit, onBack:()->Unit, onSkip:()->Unit) {
    val titles = listOf("Pathfinder", "Descubre tu potencial", "Pequeños pasos, grandes cambios", "Tu esfuerzo tiene recompensa", "¿Cómo te llamamos?", "¿Qué quieres mejorar?", "Elige tu ritmo")
    val descriptions = listOf("Tu camino, tus metas", "Aprende, mejora tus hábitos y avanza hacia la vida que quieres.", "Misiones diarias, rutas de aprendizaje y seguimiento de tu progreso.", "Gana experiencia, desbloquea logros y celebra cada avance.", "Tu progreso se guardará en este dispositivo.", "Elige uno o varios objetivos para personalizar tu ruta.", "Puedes cambiar esta preferencia más adelante.")
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF09162B), Color(0xFF171B48)))).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { LogoMark(); Spacer(Modifier.width(10.dp)); Text("Pathfinder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            Spacer(Modifier.height(36.dp)); Text("${(step + 1).coerceAtMost(7)} / 7", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp)); Text(titles[step], color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Spacer(Modifier.height(8.dp)); Text(descriptions[step], color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(30.dp))
            when (step) {
                0, 1, 2, 3 -> PathfinderIllustration(step, Modifier.fillMaxWidth().height(190.dp))
                4 -> { Text("Tu progreso se guarda localmente; puedes empezar sin crear una cuenta.", color = Color.White); Spacer(Modifier.height(10.dp)); OutlinedTextField(name, onName, Modifier.fillMaxWidth(), placeholder = { Text("Tu nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
                5 -> listOf("Ser más productiva", "Mejorar mis hábitos", "Aprender algo nuevo", "Reducir el estrés").forEach { SelectRow(it, it in goals) { onGoals(it) } }
                6 -> Column { Text("Nivel actual", color = Color.White, fontWeight = FontWeight.Bold); listOf("Principiante", "Intermedio", "Avanzado").forEach { SelectRow(it, it == level) { onLevel(it) } }; Spacer(Modifier.height(14.dp)); Text("Tiempo diario disponible", color = Color.White, fontWeight = FontWeight.Bold); listOf(15,30,45).forEach { SelectRow("$it minutos al día", it == minutes) { onMinutes(it) } } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if(step in 1..3) TextButton(onClick = onSkip) { Text("Omitir") }
            if (step > 0) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Volver") }
            Button(onClick = onNext, enabled = when(step) { 4 -> name.isNotBlank(); 5 -> goals.isNotEmpty(); else -> true }, modifier = Modifier.weight(2f).height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(when(step) { 0 -> "Comenzar"; 3 -> "Empezar en modo local"; 6 -> "Crear mi ruta"; else -> "Continuar" }) }
        }
    }
}

@Composable private fun HomeScreen(name: String, level: Int, xp: Int, streak: Int, mission: Mission, done: Set<Int>, open: (Mission)->Unit, routes:()->Unit, library:()->Unit, lesson:()->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Hola, $name 👋", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Un paso pequeño también cuenta.", color = Muted, fontSize = 14.sp) }
        item { PathfinderIllustration(2, Modifier.fillMaxWidth().height(142.dp)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.weight(1.5f)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(CircleShape).background(Violet.copy(.2f)), Alignment.Center) { Text("✦", color = Mint, fontSize = 27.sp) }; Spacer(Modifier.width(10.dp)); Column { Text("Nivel $level", color = Color.White, fontWeight = FontWeight.Bold); Text("Explorador", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(7.dp)); LinearProgressIndicator({ (xp % 100) / 100f }, Modifier.fillMaxWidth(.84f), color = Mint, trackColor = Color.White.copy(.1f)); Text("$xp XP", color = Muted, fontSize = 11.sp) } } }
            CardBox(Modifier.weight(1f)) { Text("🔥  Racha", color = Orange, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("$streak días", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Sigue a tu ritmo", color = Muted, fontSize = 11.sp) }
        } }
        item { SectionTitle("Misión de hoy", "Tu siguiente paso") }
        item { CardBox(Modifier.fillMaxWidth(), accent = Violet) { Row(verticalAlignment = Alignment.CenterVertically) { Text("◈", color = Mint, fontSize = 24.sp); Spacer(Modifier.width(8.dp)); Text(mission.category.uppercase(), color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("+${mission.xp} XP", color = Orange, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(8.dp)); Text(mission.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(mission.description, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(14.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("◷ ${mission.minutes} min", color = Muted, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Button(onClick = { open(mission) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if (mission.id in done) "Ver misión" else "Comenzar") } } } }
        item { SectionTitle("Tu progreso", "Basado en actividad real") }
        item { CardBox(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Misiones completadas", color = Muted, fontSize = 13.sp); Text("${done.size} / ${missions.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }; Column(horizontalAlignment = Alignment.End) { Text("Experiencia", color = Muted, fontSize = 13.sp); Text("$xp XP", color = Mint, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.height(10.dp)); LinearProgressIndicator({ done.size.toFloat() / missions.size }, Modifier.fillMaxWidth(), color = Mint, trackColor = Color.White.copy(.1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { QuickCard("Explorar rutas", "9 categorías", Icons.Filled.Add, Modifier.weight(1f), routes); QuickCard("Biblioteca", "Ideas para avanzar", Icons.Filled.Favorite, Modifier.weight(1f), library) } }
        item { CardBox(Modifier.fillMaxWidth(), accent = Orange) { Text("Continúa aprendiendo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Lecciones breves con ejercicios y preguntas para practicar.", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); Button(onClick = lesson, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Abrir lecciones") } } }
    }
}

@Composable private fun RoutesScreen(done:Set<Int>, open:(Mission)->Unit, library:()->Unit) {
    val categories = listOf("Productividad", "Hábitos saludables", "Desarrollo personal", "Creatividad", "Finanzas personales", "Liderazgo", "Idiomas", "Programación", "Negocios")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Rutas", color = MaterialTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Construye habilidades con pasos concretos.", color = Muted) }
        items(categories) { cat ->
            val items = missions.filter { if (cat == "Hábitos saludables") it.category == "Hábitos" else it.category == cat }
            val available = items.isNotEmpty()
            CardBox(Modifier.fillMaxWidth(), accent = if (available) Mint else Muted) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (available) Mint.copy(.17f) else Muted.copy(.12f)), Alignment.Center) { Icon(if (available) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (available) Mint else Muted) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(cat, color = Color.White, fontWeight = FontWeight.Bold); Text(if (available) "${items.size} misiones · ${items.count { it.id in done }} completadas" else "Próximamente", color = Muted, fontSize = 12.sp) }; Text(if(available) "›" else "🔒", color = Mint, fontSize = 22.sp) }
                if (available) { Spacer(Modifier.height(10.dp)); items.take(2).forEach { m -> TextButton(onClick = { open(m) }, contentPadding = PaddingValues(0.dp)) { Text("• ${m.title}   +${m.xp} XP", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
            }
        }
        item { OutlinedButton(onClick = library, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Favorite, null); Spacer(Modifier.width(8.dp)); Text("Abrir biblioteca") } }
    }
}

@Composable private fun MissionPage(m: Mission, completed: Boolean, xp: Int, back:()->Unit, finish:()->Unit) {
    var checked by remember(m.id) { mutableStateOf(setOf<Int>()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Filled.ArrowBack, "Volver") }; Text("Misión", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            PathfinderIllustration(m.id % 4, Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(18.dp))
            Text(m.category.uppercase(), color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); Text(m.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp); Spacer(Modifier.height(8.dp)); Text(m.description, color = Muted); Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag("◷ ${m.minutes} min"); Tag("✦ +${m.xp} XP") }
            Spacer(Modifier.height(26.dp)); Text("Pasos de la misión", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(10.dp))
            m.steps.forEachIndexed { index, text -> CardBox(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = index in checked || completed, onCheckedChange = { checked = if (index in checked) checked - index else checked + index }, enabled = !completed, colors = CheckboxDefaults.colors(checkedColor = Mint)); Text(text, Modifier.weight(1f), color = Color.White) } } }
            if(completed) { Spacer(Modifier.height(15.dp)); Text("Misión completada · recompensa registrada", color = Mint, fontWeight = FontWeight.Bold) }
        }
        Button(onClick = if(completed) back else finish, enabled = completed || checked.size == m.steps.size, modifier = Modifier.fillMaxWidth().padding(18.dp).height(54.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if(completed) "Volver" else "Completar y ganar ${m.xp} XP") }
    }
}

@Composable private fun StatisticsScreen(done: Int, xp: Int, progress: List<MissionProgressEntity>) {
    val last7 = progress.count { it.completedDate?.let { d -> runCatching { !LocalDate.parse(d).isBefore(LocalDate.now().minusDays(6)) }.getOrDefault(false) } == true }
    val last30 = progress.count { it.completedDate?.let { d -> runCatching { !LocalDate.parse(d).isBefore(LocalDate.now().minusDays(29)) }.getOrDefault(false) } == true }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Estadísticas", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold); Text("Tu avance registrado", color = Muted) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatTile("Misiones", "$done", Icons.Filled.CheckCircle, Modifier.weight(1f)); StatTile("XP total", "$xp", Icons.Filled.Add, Modifier.weight(1f)) } }
        item { CardBox(Modifier.fillMaxWidth(), accent = Violet) { Text("Últimos 30 días", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("$last30 misiones completadas", color = Muted); Spacer(Modifier.height(10.dp)); (3 downTo 0).forEach { week -> val start = LocalDate.now().minusDays((week * 7 + 6).toLong()); val end = LocalDate.now().minusDays((week * 7).toLong()); val count = progress.count { row -> row.completedDate?.let { d -> runCatching { val date = LocalDate.parse(d); !date.isBefore(start) && !date.isAfter(end) }.getOrDefault(false) } == true }; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("Semana ${4-week}", color = Muted, modifier = Modifier.width(76.dp)); LinearProgressIndicator({ (count / 7f).coerceIn(0f,1f) }, Modifier.weight(1f), color = Mint); Text("  $count", color = Color.White) } } } }
        item { CardBox(Modifier.fillMaxWidth()) { Text("Últimos 7 días", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("$last7 misiones completadas", color = Muted); Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) { (6 downTo 0).forEach { offset -> val count = progress.count { it.completedDate == LocalDate.now().minusDays(offset.toLong()).toString() }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.width(22.dp).height((14 + count * 16).dp).clip(RoundedCornerShape(6.dp)).background(if(count > 0) Mint else Panel2)); Spacer(Modifier.height(6.dp)); Text(LocalDate.now().minusDays(offset.toLong()).dayOfWeek.name.take(1), color = Muted, fontSize = 10.sp) } } } } }
        item { CardBox(Modifier.fillMaxWidth()) { Text("Actividad reciente", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); val recent = missions.filter { m -> progress.any { it.missionId == m.id && it.completed } }; if (recent.isEmpty()) Text("Cuando completes misiones, verás aquí tu historial.", color = Muted) else recent.forEach { Text("✓  ${it.title}", color = Mint, modifier = Modifier.padding(vertical = 5.dp)) } } }
        item { CardBox(Modifier.fillMaxWidth()) { Text("Racha actual", color = Color.White, fontWeight = FontWeight.Bold); Text(if(last7 > 0) "Actividad registrada en los últimos 7 días" else "Completa una misión para iniciar tu racha.", color = Muted) } }
    }
}

@Composable private fun ProfileScreen(name: String, xp: Int, level: Int, missionsDone: Int, streak: Int, minutes: Int, theme:String, accountEmail:String?, openAccount:()->Unit, setTheme:(String)->Unit, rename:(String)->Unit, setMinutes:(Int)->Unit, clear:()->Unit) {
    var editing by remember { mutableStateOf(false) }; var newName by remember(name) { mutableStateOf(name) }; var confirm by remember { mutableStateOf(false) }; var editingMinutes by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("Perfil", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) }
        item { CardBox(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(60.dp).clip(CircleShape).background(Violet.copy(.24f)), Alignment.Center) { Text(name.take(1).uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(14.dp)); Column { Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text("Nivel $level · $xp XP", color = Muted) }; Spacer(Modifier.weight(1f)); IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Add, "Editar nombre", tint = Mint) } } } }
        item { CardBox(Modifier.fillMaxWidth(), accent = Violet) { Text("Cuenta", color = Color.White, fontWeight = FontWeight.Bold); Text(accountEmail ?: "Modo local · sin cuenta conectada", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = openAccount) { Text(if(accountEmail == null) "Conectar con Google" else "Administrar cuenta") } } }
        item { CardBox(Modifier.fillMaxWidth()) { Text("Apariencia", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); listOf("claro" to "Claro", "oscuro" to "Oscuro", "automático" to "Automático").forEach { (v,label) -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(theme == v, { setTheme(v) }); Text(label, color = Color.White) } } } }
        item { CardBox(Modifier.fillMaxWidth()) { Text("Plan diario", color = Color.White, fontWeight = FontWeight.Bold); Text("Disponibilidad: $minutes minutos al día", color = Muted); TextButton(onClick = { editingMinutes = true }) { Text("Cambiar disponibilidad") } } }
        item { CardBox(Modifier.fillMaxWidth()) { Text("Datos locales", color = Color.White, fontWeight = FontWeight.Bold); Text("Tu progreso se guarda en este dispositivo.", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { confirm = true }) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(7.dp)); Text("Borrar mis datos") } } }
        item { CardBox(Modifier.fillMaxWidth(), accent = Orange) { Text("Desafíos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Completa 3 misiones · ${missionsDone.coerceAtMost(3)} / 3", color = Muted); LinearProgressIndicator({ (missionsDone.coerceAtMost(3) / 3f) }, Modifier.fillMaxWidth(), color = Mint); Spacer(Modifier.height(10.dp)); Text("Insignias", color = Color.White, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) { listOf(Triple("🌱", "Primer paso", missionsDone >= 1), Triple("🔥", "Constancia", streak >= 3), Triple("✦", "Exploradora", missionsDone >= 10)).forEach { (icon, title, unlocked) -> Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(if(unlocked) icon else "🔒", fontSize = 24.sp); Text(title, color = if(unlocked) Mint else Muted, fontSize = 10.sp) } } }; Text("Nivel ${level} · ${xp} XP", color = Muted, fontSize = 12.sp) } }
        item { Text("Pathfinder · Tu camino, tus metas", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    if(editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Editar nombre") }, text = { OutlinedTextField(newName, { newName = it }, label = { Text("Nombre") }, singleLine = true) }, confirmButton = { TextButton(onClick = { rename(newName.trim().ifBlank { name }); editing = false }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } })
    if(editingMinutes) AlertDialog(onDismissRequest = { editingMinutes = false }, title = { Text("Tiempo diario") }, text = { Column { listOf(15,30,45).forEach { value -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(minutes == value, { setMinutes(value) }); Text("$value minutos") } } } }, confirmButton = { TextButton(onClick = { editingMinutes = false }) { Text("Listo") } })
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("¿Borrar el progreso local?") }, text = { Text("Se eliminarán tu perfil, misiones completadas y experiencia de este dispositivo.") }, confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("Borrar", color = Color.Red) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

@Composable private fun LessonsCatalog(progress: List<LessonProgressEntity>, open: (Lesson)->Unit, back:()->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { TextButton(onClick = back) { Text("← Volver") }; Text("Lecciones", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold); Text("Aprende, practica y comprueba lo aprendido.", color = Muted) }
        items(lessons) { lesson ->
            val saved = progress.firstOrNull { it.lessonId == lesson.id }
            CardBox(Modifier.fillMaxWidth().clickable { open(lesson) }) { Text(if(saved?.completed == true) "COMPLETADA · ${saved.score}%" else "LECCIÓN ${lesson.id}", color = if(saved?.completed == true) Mint else Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(lesson.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Contenido · ejercicio · cuestionario", color = Muted, fontSize = 13.sp) }
        }
    }
}

private data class ProductivityAlternative(val title: String, val subtitle: String, val description: String, val source: String, val variant: Int)

@Composable private fun LibraryScreen(favorites:Set<String>, history:List<String>, open:(String)->Unit, toggleFavorite:(String)->Unit) {
    val articles = listOf("Hábitos pequeños, cambios sostenibles" to "Empieza con una acción tan breve que sea fácil repetirla. La constancia se construye reduciendo la fricción y vinculando el nuevo hábito con una rutina que ya existe.", "Cómo organizar tu semana" to "Anota compromisos fijos, elige pocas prioridades y reserva tiempo para tareas importantes. Deja espacio libre para imprevistos y revisa el plan cada día.", "Recupera tu atención" to "Identifica qué te distrae, silencia avisos no esenciales y trabaja en bloques breves. Cuando aparezca una interrupción, anótala y vuelve a la tarea elegida.", "Una revisión diaria en cinco minutos" to "Al terminar el día, registra lo que avanzaste y elige una prioridad para mañana. Preparar el primer paso hace que empezar sea más sencillo.", "Priorizar con intención" to "Compara las tareas por impacto y esfuerzo. Empieza por una acción importante y concreta; deja para después lo que no acerca tus objetivos.")
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var webQuery by rememberSaveable { mutableStateOf("") }
    val filtered = articles.filter { it.first.contains(query, true) || it.second.contains(query, true) }
    val alternatives = listOf(
        ProductivityAlternative("Planes si-entonces", "Define cuándo y dónde empezar", "Convierte una intención en un plan concreto: «Si termino de almorzar, entonces dedicaré diez minutos a…».", "https://pmc.ncbi.nlm.nih.gov/articles/PMC8149892/", 1),
        ProductivityAlternative("Pomodoro o Flowtime", "Prueba bloques y descansos", "Elige ciclos con pausas o descansa cuando notes que baja tu atención. Compara ambos formatos y quédate con el que encaje con tu tarea.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12292963/", 3),
        ProductivityAlternative("Una tarea a la vez", "Reduce cambios de contexto", "Agrupa tareas parecidas, prepara lo que necesitas y anota interrupciones para retomarlas después.", "https://www.ics.uci.edu/~gmark/chi08-mark.pdf", 0),
        ProductivityAlternative("Modo de enfoque", "Pausa apps que te distraen", "Android permite pausar temporalmente apps seleccionadas desde Bienestar digital; los pasos dependen del dispositivo.", "https://support.google.com/android/answer/9346420?hl=es", 2)
    )
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Text("Biblioteca", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text("Ideas prácticas para avanzar", color = Muted)
            if(history.isNotEmpty()) Text("Última lectura: ${history.first()}", color = Mint, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar en la biblioteca") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(15.dp))
            if(favorites.isNotEmpty()) Text("Favoritos: ${favorites.size}", color = Muted, fontSize = 12.sp)
        }
        item {
            CardBox(Modifier.fillMaxWidth(), accent = Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Explora más ideas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Busca métodos y compara alternativas en la web.", color = Muted, fontSize = 12.sp)
                    }
                    Text("⌕", color = Mint, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(webQuery, { webQuery = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ej. estudiar con poco tiempo") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val q = webQuery.trim().ifBlank { "alternativas de productividad y técnicas de concentración" }
                    val uri = Uri.Builder().scheme("https").authority("www.google.com").appendPath("search").appendQueryParameter("q", q).build()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("Buscar en internet") }
                Text("La búsqueda se abre en tu navegador. Las ideas de abajo también se pueden leer sin conexión.", color = Muted, fontSize = 11.sp)
            }
        }
        item { SectionTitle("Prueba otros métodos", "Alternativas con referencias para ampliar") }
        items(alternatives) { alternative ->
            CardBox(Modifier.fillMaxWidth(), accent = Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PathfinderIllustration(alternative.variant, Modifier.width(92.dp).height(82.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alternative.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(alternative.subtitle, color = Mint, fontSize = 11.sp)
                        Text(alternative.description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alternative.source))) } }) { Text("Consultar fuente ↗", color = Violet) }
            }
        }
        items(filtered) { a -> CardBox(Modifier.fillMaxWidth()) { Text("LECTURA · 4 MIN", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(a.first, Modifier.clickable { open(a.first) }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(a.second.take(105) + "…", Modifier.clickable { open(a.first) }, color = Muted, maxLines = 2); TextButton(onClick = { toggleFavorite(a.first) }) { Text(if(a.first in favorites) "★ Guardado" else "☆ Guardar", color = Orange) }; Button(onClick = { open(a.first) }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Leer") } } }
    }
}

@Composable private fun ArticlePage(title:String, favorite:Boolean, toggleFavorite:()->Unit, back:()->Unit) {
    val content = when(title) { "Hábitos pequeños, cambios sostenibles" -> "Empieza con una acción tan breve que sea fácil repetirla. La constancia se construye reduciendo la fricción y vinculando el nuevo hábito con una rutina que ya existe.\n\nPor ejemplo, si quieres leer más, deja un libro junto a tu lugar de descanso y lee una página después de preparar una bebida. Registra cada intento sin exigirte una racha perfecta.\n\nCuando el hábito sea estable, puedes aumentar su duración poco a poco."; "Cómo organizar tu semana" -> "Anota primero tus compromisos fijos. Después elige pocas prioridades que de verdad acerquen tus objetivos y reserva bloques realistas para avanzar.\n\nDeja tiempo libre para imprevistos. Al final de cada día, dedica unos minutos a revisar el plan y decidir cuál será tu siguiente paso."; "Recupera tu atención" -> "Observa cuáles son tus principales interrupciones. Silencia avisos que no requieren respuesta inmediata y coloca el teléfono fuera de tu alcance durante un bloque breve.\n\nAntes de empezar, escribe la tarea concreta. Si aparece una distracción, anótala para revisarla después y vuelve a la actividad elegida."; else -> "Al terminar el día, registra lo que avanzaste sin juzgarte. Elige una prioridad alcanzable para mañana y prepara el material que necesitarás.\n\nUna revisión breve ayuda a cerrar asuntos abiertos y hace más fácil comenzar el siguiente día con intención." }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Filled.ArrowBack, "Volver") }; Text("Artículo", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(20.dp)); Text(title, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); Text(content, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, lineHeight = 26.sp); Spacer(Modifier.height(20.dp)); OutlinedButton(onClick = toggleFavorite) { Text(if(favorite) "Quitar de favoritos" else "Guardar en favoritos") } }
}

@Composable private fun PathfinderIllustration(variant: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF25245F), Color(0xFF12334D), Color(0xFF10243D))))) {
        val w = size.width
        val h = size.height
        drawCircle(Color(0xFF6F63FF).copy(alpha = .18f), h * .42f, Offset(w * .82f, h * .27f))
        drawCircle(Color(0xFF36D6B0).copy(alpha = .10f), h * .30f, Offset(w * .20f, h * .22f))
        val distant = Path().apply {
            moveTo(0f, h * .80f); lineTo(w * .26f, h * .37f); lineTo(w * .43f, h * .64f)
            lineTo(w * .62f, h * .25f); lineTo(w * .84f, h * .66f); lineTo(w, h * .43f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(distant, Brush.linearGradient(listOf(Color(0xFF5449B8), Color(0xFF27386C), Color(0xFF1D6070))))
        val near = Path().apply {
            moveTo(0f, h * .88f); lineTo(w * .32f, h * .56f); lineTo(w * .48f, h * .77f)
            lineTo(w * .72f, h * .47f); lineTo(w, h * .83f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(near, Brush.linearGradient(listOf(Color(0xFF4333A3), Color(0xFF173E62), Color(0xFF16796C))))
        val trail = Path().apply {
            moveTo(w * .50f, h * .96f); cubicTo(w * .44f, h * .84f, w * .63f, h * .82f, w * .57f, h * .70f)
            cubicTo(w * .52f, h * .59f, w * .65f, h * .58f, w * .68f, h * .45f)
        }
        drawPath(trail, Color(0xFFB9F5E4).copy(alpha = .82f), style = Stroke(width = (h * .018f).coerceAtLeast(2f), cap = StrokeCap.Round))
        drawCircle(Color(0xFFFFC16F), radius = (h * .035f).coerceAtLeast(3f), center = Offset(w * .68f, h * .45f))
        val stars = listOf(Offset(.14f, .22f), Offset(.34f, .17f), Offset(.52f, .28f), Offset(.89f, .19f))
        stars.forEachIndexed { i, point -> drawCircle(Color.White.copy(alpha = if (i % 2 == 0) .85f else .55f), (h * .012f).coerceAtLeast(1.5f), Offset(w * point.x, h * point.y)) }
        val cx = w * .79f
        val cy = h * .31f
        when (variant.mod(4)) {
            0 -> {
                drawRoundRect(Color(0xFF101F3E), Offset(cx - h * .11f, cy - h * .12f), androidx.compose.ui.geometry.Size(h * .20f, h * .27f), CornerRadius(h * .035f))
                drawRoundRect(Color(0xFF36D6B0), Offset(cx - h * .075f, cy - h * .065f), androidx.compose.ui.geometry.Size(h * .12f, h * .018f), CornerRadius(h * .01f))
                drawRoundRect(Color(0xFF8C7BFF), Offset(cx - h * .075f, cy - h * .005f), androidx.compose.ui.geometry.Size(h * .105f, h * .018f), CornerRadius(h * .01f))
                drawRoundRect(Color(0xFFFFB454), Offset(cx - h * .075f, cy + h * .055f), androidx.compose.ui.geometry.Size(h * .08f, h * .018f), CornerRadius(h * .01f))
            }
            1 -> {
                drawCircle(Color(0xFFFFB454), h * .12f, Offset(cx, cy), style = Stroke(h * .018f))
                drawCircle(Color(0xFF8C7BFF), h * .075f, Offset(cx, cy), style = Stroke(h * .018f))
                drawCircle(Color(0xFF36D6B0), h * .028f, Offset(cx, cy))
            }
            2 -> {
                drawCircle(Color(0xFFFFD39B), h * .055f, Offset(cx, cy - h * .07f))
                drawLine(Color(0xFF152B50), Offset(cx, cy - h * .015f), Offset(cx, cy + h * .11f), h * .045f, cap = StrokeCap.Round)
                drawLine(Color(0xFF36D6B0), Offset(cx, cy + h * .035f), Offset(cx - h * .09f, cy + h * .12f), h * .025f, cap = StrokeCap.Round)
                drawLine(Color(0xFF8C7BFF), Offset(cx, cy + h * .035f), Offset(cx + h * .09f, cy + h * .10f), h * .025f, cap = StrokeCap.Round)
            }
            else -> {
                val cup = Path().apply { moveTo(cx - h * .09f, cy - h * .08f); lineTo(cx + h * .09f, cy - h * .08f); lineTo(cx + h * .06f, cy + h * .02f); quadraticTo(cx, cy + h * .10f, cx - h * .06f, cy + h * .02f); close() }
                drawPath(cup, Brush.linearGradient(listOf(Color(0xFFFFDF78), Color(0xFFFF9B39))))
                drawLine(Color(0xFFFFC86B), Offset(cx, cy + h * .08f), Offset(cx, cy + h * .14f), h * .025f, cap = StrokeCap.Round)
                drawLine(Color(0xFFFFC86B), Offset(cx - h * .07f, cy + h * .14f), Offset(cx + h * .07f, cy + h * .14f), h * .025f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable private fun CardBox(modifier:Modifier, accent:Color=Mint, content:@Composable ColumnScope.()->Unit) { Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, accent.copy(.10f))) { Column(Modifier.padding(16.dp), content = content) } }
@Composable private fun SectionTitle(title:String, subtitle:String) { Column { Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(subtitle, color = Muted, fontSize = 12.sp) } }
@Composable private fun QuickCard(title:String, subtitle:String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier:Modifier, click:()->Unit) { Card(modifier.clickable(onClick = click), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(14.dp)) { Icon(icon, null, tint = Mint); Spacer(Modifier.height(10.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) } } }
@Composable private fun StatTile(title:String, value:String, icon:androidx.compose.ui.graphics.vector.ImageVector, modifier:Modifier) { CardBox(modifier) { Icon(icon, null, tint = Mint); Spacer(Modifier.height(8.dp)); Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(title, color = Muted, fontSize = 12.sp) } }
@Composable private fun Tag(text:String) { Surface(color = Panel2, shape = RoundedCornerShape(40.dp)) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Muted, fontSize = 12.sp) } }
@Composable private fun SelectRow(title:String, selected:Boolean, click:()->Unit) { Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = click), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if(selected) Violet.copy(.2f) else Panel), border = BorderStroke(1.dp, if(selected) Violet else Color.White.copy(.08f))) { Row(Modifier.padding(horizontal=14.dp, vertical=13.dp), verticalAlignment=Alignment.CenterVertically) { Text(title, Modifier.weight(1f), color=Color.White, fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal); if(selected) Icon(Icons.Filled.CheckCircle,null,tint=Mint) else Icon(Icons.Filled.Info,null,tint=Muted) } } }
@Composable private fun LogoMark() { Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet,Mint))), Alignment.Center) { Text("⌃", color=Color.White, fontSize=28.sp, fontWeight=FontWeight.Bold) } }
