package com.example.pathfinder

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "profile")
data class ProfileEntity(@PrimaryKey val id: Int = 1, val name: String = "Rosa", val goals: String = "", val minutes: Int = 15, val level: String = "Principiante", val onboarded: Boolean = false)

@Entity(tableName = "mission_progress")
data class MissionProgressEntity(@PrimaryKey val missionId: Int, val completed: Boolean = false, val completedDate: String? = null)

@Entity(tableName = "experience_transactions")
data class ExperienceEntity(@PrimaryKey val missionId: Int, val xp: Int, val date: String)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(@PrimaryKey val lessonId: Int, val completed: Boolean, val score: Int = 0, val completedDate: String? = null)

@Entity(tableName = "favorite_articles")
data class FavoriteArticleEntity(@PrimaryKey val title: String, val addedDate: String)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(@PrimaryKey val title: String, val openedDate: String)

@Dao interface PathfinderDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun profile(): Flow<ProfileEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: ProfileEntity)
    @Query("SELECT * FROM mission_progress") fun progress(): Flow<List<MissionProgressEntity>>
    @Query("SELECT * FROM experience_transactions") fun experience(): Flow<List<ExperienceEntity>>
    @Query("SELECT * FROM lesson_progress") fun lessons(): Flow<List<LessonProgressEntity>>
    @Query("SELECT * FROM favorite_articles") fun favorites(): Flow<List<FavoriteArticleEntity>>
    @Query("SELECT * FROM reading_history ORDER BY openedDate DESC") fun readingHistory(): Flow<List<ReadingHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLesson(progress: LessonProgressEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFavorite(favorite: FavoriteArticleEntity)
    @Query("DELETE FROM favorite_articles WHERE title = :title") suspend fun removeFavorite(title: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun recordRead(history: ReadingHistoryEntity)
    @Query("SELECT EXISTS(SELECT 1 FROM experience_transactions WHERE missionId = :id)") suspend fun rewarded(id: Int): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun complete(progress: MissionProgressEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun reward(xp: ExperienceEntity): Long
    @Transaction suspend fun awardMission(progress: MissionProgressEntity, experience: ExperienceEntity): Boolean {
        if (reward(experience) < 0L) return false
        complete(progress)
        return true
    }
    @Query("DELETE FROM profile") suspend fun clearProfile()
    @Query("DELETE FROM mission_progress") suspend fun clearProgress()
    @Query("DELETE FROM experience_transactions") suspend fun clearExperience()
    @Query("DELETE FROM lesson_progress") suspend fun clearLessons()
    @Query("DELETE FROM favorite_articles") suspend fun clearFavorites()
    @Query("DELETE FROM reading_history") suspend fun clearReadingHistory()
}

@Database(entities = [ProfileEntity::class, MissionProgressEntity::class, ExperienceEntity::class, LessonProgressEntity::class, FavoriteArticleEntity::class, ReadingHistoryEntity::class], version = 2, exportSchema = true)
abstract class PathfinderDatabase : RoomDatabase() { abstract fun dao(): PathfinderDao }

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS lesson_progress (lessonId INTEGER NOT NULL, completed INTEGER NOT NULL, score INTEGER NOT NULL, completedDate TEXT, PRIMARY KEY(lessonId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS favorite_articles (title TEXT NOT NULL, addedDate TEXT NOT NULL, PRIMARY KEY(title))")
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_history (title TEXT NOT NULL, openedDate TEXT NOT NULL, PRIMARY KEY(title))")
    }
}

val Context.pathfinderPrefs by preferencesDataStore("pathfinder_preferences")
object PrefKeys {
    val theme = stringPreferencesKey("theme")
    val name = stringPreferencesKey("name")
    val onboarding = booleanPreferencesKey("onboarding_complete")
    val dailyMinutes = intPreferencesKey("daily_minutes")
    val goalIds = stringPreferencesKey("goal_ids")
}

data class Mission(val id: Int, val title: String, val description: String, val steps: List<String>, val minutes: Int, val category: String, val xp: Int)

val missions = listOf(
    Mission(1,"Organiza tu espacio de trabajo","Prepara un lugar que te invite a concentrarte.", listOf("Retira lo que no necesitas","Agrupa los objetos por categoría","Deja a mano solo tu próxima tarea"),15,"Productividad",30),
    Mission(2,"Planifica tu semana","Convierte tus prioridades en un plan realista.",listOf("Anota tus compromisos fijos","Elige tres prioridades","Reserva bloques para cada una"),20,"Planificación",35),
    Mission(3,"Diseña un hábito pequeño","Haz que empezar sea tan sencillo que no puedas posponerlo.",listOf("Elige un hábito que quieras crear","Reduce la acción a dos minutos","Decide cuándo y dónde comenzar"),10,"Hábitos",25),
    Mission(4,"Protege 15 minutos de enfoque","Dedica un bloque breve a una sola tarea.",listOf("Elige una tarea concreta","Silencia avisos durante 15 minutos","Trabaja sin cambiar de tarea"),15,"Enfoque",30),
    Mission(5,"Haz una revisión diaria","Cierra el día con claridad y prepara el siguiente.",listOf("Anota qué terminaste","Elige la prioridad de mañana","Deja listo lo que necesitarás"),8,"Hábitos",20),
    Mission(6,"Encuentra tus horas de energía","Reconoce cuándo te resulta más fácil concentrarte.",listOf("Revisa cómo te sentiste hoy","Marca tu tramo de mayor energía","Reserva una tarea importante para ese horario"),10,"Autoconocimiento",25),
    Mission(7,"Vacía tu lista mental","Saca pendientes de tu cabeza y llévalos a un lugar confiable.",listOf("Escribe todo lo que recuerdes","Agrupa asuntos similares","Elige un siguiente paso pequeño"),12,"Organización",25),
    Mission(8,"Prepara tu mañana","Reduce las decisiones que tendrás que tomar al despertar.",listOf("Consulta tu calendario","Elige una prioridad","Prepara ropa o materiales"),7,"Hábitos",20),
    Mission(9,"Ordena tus notificaciones","Recupera atención reduciendo interrupciones innecesarias.",listOf("Revisa las apps que más interrumpen","Desactiva avisos no esenciales","Define dos momentos para revisar mensajes"),12,"Enfoque",25),
    Mission(10,"Divide una tarea difícil","Haz que un proyecto grande tenga un primer paso claro.",listOf("Escribe el resultado que buscas","Divide el trabajo en pasos","Empieza por una acción de menos de 10 minutos"),15,"Productividad",30),
    Mission(11,"Toma una pausa consciente","Recarga tu atención con una pausa breve y sin pantalla.",listOf("Aléjate de la pantalla","Estira el cuerpo un minuto","Respira con calma durante dos minutos"),5,"Bienestar",15),
    Mission(12,"Crea una señal para tu hábito","Conecta una acción nueva con una rutina que ya existe.",listOf("Elige una rutina cotidiana","Añade tu hábito justo después","Prepara una señal visible"),10,"Hábitos",25),
    Mission(13,"Prioriza con claridad","Distingue lo urgente de lo que realmente importa.",listOf("Anota tus pendientes","Marca el impacto de cada uno","Empieza por el pendiente de mayor impacto"),12,"Planificación",25),
    Mission(14,"Cierra una pestaña pendiente","Termina una tarea breve que llevas tiempo aplazando.",listOf("Elige un pendiente de menos de 15 minutos","Define qué significa terminarlo","Complétalo y regístralo"),15,"Productividad",30),
    Mission(15,"Haz espacio para aprender","Reserva un momento recurrente para desarrollar una habilidad.",listOf("Elige qué quieres aprender","Encuentra tres espacios posibles","Agenda el primer bloque"),10,"Aprendizaje",25),
    Mission(16,"Revisa tu entorno digital","Haz más sencillo encontrar tus archivos de trabajo.",listOf("Elimina archivos duplicados visibles","Crea una carpeta para lo activo","Archiva un elemento terminado"),15,"Organización",30),
    Mission(17,"Practica decir no con respeto","Protege tiempo para tus prioridades sin dejar de ser amable.",listOf("Identifica un compromiso que te sobrecarga","Define tu límite","Escribe una respuesta clara y respetuosa"),12,"Límites",25),
    Mission(18,"Reconoce un avance","Refuerza la constancia observando lo que sí has hecho.",listOf("Recuerda un esfuerzo reciente","Anota qué te ayudó","Elige cómo repetir esa condición"),6,"Reflexión",20),
    Mission(19,"Prepara una lista de compras","Evita decisiones apresuradas para una tarea cotidiana.",listOf("Revisa lo que ya tienes","Anota lo que necesitas","Agrupa la lista por tipo"),8,"Organización",20),
    Mission(20,"Prueba una sesión sin interrupciones","Observa cómo cambia tu trabajo al proteger tu atención.",listOf("Elige una tarea de una sola parte","Deja el teléfono fuera de alcance","Trabaja 20 minutos y anota el resultado"),20,"Enfoque",35),
    Mission(21,"Define tu siguiente paso","Empieza un proyecto sin esperar a tener el plan perfecto.",listOf("Escribe el resultado deseado","Identifica qué te detiene","Define una acción que puedas hacer hoy"),10,"Productividad",25),
    Mission(22,"Planifica una pausa","Incluye descanso para sostener tu energía durante el día.",listOf("Detecta cuándo sueles cansarte","Elige una pausa de cinco minutos","Añádela a tu agenda"),7,"Bienestar",20),
    Mission(23,"Ordena tus prioridades del mes","Conecta tus tareas semanales con lo que te importa.",listOf("Anota un objetivo del mes","Define cómo medir avance","Elige una acción para esta semana"),15,"Planificación",30),
    Mission(24,"Lee durante diez minutos","Haz una sesión breve de lectura sin presión por terminar.",listOf("Elige un texto útil o interesante","Deja el teléfono en silencio","Lee durante diez minutos"),10,"Aprendizaje",25),
    Mission(25,"Simplifica una rutina","Quita pasos innecesarios de una actividad frecuente.",listOf("Elige una rutina diaria","Observa dónde pierdes tiempo","Prueba una versión más simple"),12,"Hábitos",25),
    Mission(26,"Haz una revisión de gastos","Mira tus movimientos recientes con curiosidad y sin juicio.",listOf("Reúne tus gastos de esta semana","Agrúpalos por categoría","Elige una pregunta para revisar"),15,"Finanzas",30),
    Mission(27,"Cuida tu postura al trabajar","Ajusta tu espacio para que resulte más cómodo.",listOf("Apoya los pies en el suelo","Ajusta la pantalla a una altura cómoda","Programa una pausa para moverte"),6,"Bienestar",20),
    Mission(28,"Escribe una intención para hoy","Empieza con una dirección clara y alcanzable.",listOf("Respira un momento","Escribe qué importa hoy","Elige una acción inicial"),5,"Reflexión",15),
    Mission(29,"Haz una tarea en modo avión","Comprueba si reducir estímulos facilita avanzar.",listOf("Elige una tarea pequeña","Activa modo avión durante 15 minutos","Anota cómo te sentiste"),15,"Enfoque",30),
    Mission(30,"Celebra una semana de constancia","Reconoce el trabajo que sostiene tu progreso.",listOf("Revisa tus acciones de la semana","Nombra una dificultad que superaste","Elige qué quieres mantener la próxima semana"),10,"Reflexión",35)
)
