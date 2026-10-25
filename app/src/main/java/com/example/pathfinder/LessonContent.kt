package com.example.pathfinder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

data class LessonQuestion(val prompt: String, val answers: List<String>, val correct: Int)
data class Lesson(val id: Int, val title: String, val body: String, val exercise: String, val question: LessonQuestion)

val lessons = listOf(
    Lesson(1, "Planifica una semana realista", "Empieza por compromisos fijos. Escoge hasta tres prioridades importantes y asigna bloques concretos. Deja espacio para imprevistos: un plan útil admite cambios.", "Anota una prioridad de esta semana y reserva en tu calendario un bloque de 20 minutos para dar el primer paso.", LessonQuestion("¿Qué ayuda a que un plan semanal sea sostenible?", listOf("Ocupar cada minuto", "Dejar margen para imprevistos", "Cambiar prioridades cada hora"), 1)),
    Lesson(2, "Prioriza con intención", "No todas las tareas tienen el mismo impacto. Define qué resultado importa, estima el esfuerzo y empieza por una acción pequeña que acerque ese resultado.", "Escribe tres pendientes y marca cuál tendría mayor impacto si avanzara hoy.", LessonQuestion("¿Con qué conviene empezar?", listOf("La acción de mayor impacto y concreta", "La lista completa a la vez", "Lo que más distrae"), 0)),
    Lesson(3, "Ordena tu espacio de trabajo", "Un entorno preparado reduce decisiones y fricción. Mantén visible lo necesario para la tarea actual y guarda lo demás en un lugar consistente.", "Dedica cinco minutos a retirar distracciones de tu superficie y deja listo el material de tu siguiente tarea.", LessonQuestion("¿Qué reduce la fricción al empezar?", listOf("Tener todo a la vista", "Preparar lo necesario para la tarea actual", "Añadir más herramientas"), 1)),
    Lesson(4, "Construye hábitos pequeños", "Un hábito es más fácil de repetir cuando la acción inicial es breve y tiene una señal clara. Vincúlalo a una rutina existente y aumenta la dificultad gradualmente.", "Elige una rutina diaria y añade después una acción de dos minutos que quieras repetir.", LessonQuestion("¿Cuál es un buen inicio para un hábito nuevo?", listOf("Una acción breve ligada a una señal", "Un cambio total de un día", "Esperar a tener motivación"), 0)),
    Lesson(5, "Reduce las distracciones", "Antes de un bloque de enfoque, decide una tarea, silencia avisos no urgentes y deja las interrupciones en una lista para revisarlas después.", "Prueba un bloque de 15 minutos con el teléfono fuera de alcance y anota qué te interrumpió.", LessonQuestion("¿Qué hacer si surge una distracción?", listOf("Abandonar la tarea", "Anotarla y volver al siguiente paso", "Abrir varias tareas"), 1)),
    Lesson(6, "Haz una revisión diaria", "Una revisión breve ayuda a cerrar el día: reconoce lo avanzado, identifica un pendiente importante y prepara el primer paso de mañana.", "Escribe una cosa que avanzaste hoy y la primera acción concreta de mañana.", LessonQuestion("¿Qué aporta una revisión diaria breve?", listOf("Un siguiente paso claro", "Garantizar un día perfecto", "Eliminar todo imprevisto"), 0))
)

@Composable
fun LessonPage(lesson: Lesson, saved: LessonProgressEntity?, back: () -> Unit, save: (Int) -> Unit) {
    var answer by remember(lesson.id) { mutableIntStateOf(-1) }
    var submitted by remember(lesson.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = back) { Text("← Volver") }
        Text("LECCIÓN ${lesson.id} DE ${lessons.size}", color = Color(0xFF36D6B0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(lesson.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp)); Text("Contenido", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(6.dp)); Text(lesson.body, fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(20.dp)); Text("Ejercicio práctico", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(6.dp)); Text(lesson.exercise, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(20.dp)); Text("Comprueba lo aprendido", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(8.dp)); Text(lesson.question.prompt, color = MaterialTheme.colorScheme.onBackground)
        lesson.question.answers.forEachIndexed { i, text ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(selected = answer == i, onClick = { answer = i; submitted = false }); Text(text, color = MaterialTheme.colorScheme.onBackground) }
        }
        Button(onClick = { submitted = true; save(if (answer == lesson.question.correct) 100 else 0) }, enabled = answer >= 0, modifier = Modifier.fillMaxWidth()) { Text("Revisar respuesta") }
        if (submitted) Text(if (answer == lesson.question.correct) "¡Correcto! ${if(saved?.completed == true) "Tu lección ya estaba completada." else "Lección completada."}" else "Esa respuesta no es correcta. Revisa el contenido e inténtalo de nuevo.", color = if(answer == lesson.question.correct) Color(0xFF36D6B0) else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        saved?.let { Text("Mejor resultado guardado: ${it.score}%", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp)) }
    }
}
