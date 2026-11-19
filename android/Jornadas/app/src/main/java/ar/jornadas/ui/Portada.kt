package ar.jornadas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Descarga
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Portada(model: ProgramaModel) {
    var seccion by rememberSaveable { mutableStateOf("Programa") }
    var detalle by rememberSaveable { mutableStateOf<String?>(null) }
    val agenda = model.agenda.collectAsStateWithLifecycle().value
    // La posición de cada lista se recuerda acá arriba: mientras se muestra una ficha
    // la lista sale de pantalla, y al volver debe seguir donde estaba.
    val listaPrograma = rememberLazyListState()
    val listaAgenda = rememberLazyListState()
    BackHandler(detalle != null || seccion != "Programa") {
        if (detalle != null) detalle = null else seccion = "Programa"
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF185B54), secondary = Color(0xFF995136))) {
        Scaffold(bottomBar = {
            if (detalle == null) NavigationBar {
                listOf("Programa" to "▤", "Mi agenda" to "☆", "Mesa" to "⚙").forEach { (nombre, icono) ->
                    NavigationBarItem(seccion == nombre, { seccion = nombre },
                        icon = { Text(icono) }, label = { Text(nombre) })
                }
            }
        }) { margen ->
            Column(Modifier.fillMaxSize().padding(margen).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(8.dp))
                Text("Jornadas del Sur", style = MaterialTheme.typography.labelLarge)
                if (agenda != null) AvisoDatosGuardados(agenda)
                if (detalle != null) {
                    TextButton(onClick = { detalle = null }) { Text("Volver") }
                    val ficha = agenda?.charlas?.find { it.id == detalle }
                    if (ficha != null) Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(ficha.titulo, style = MaterialTheme.typography.headlineMedium)
                        Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}")
                        Text(ficha.ponente, style = MaterialTheme.typography.titleMedium)
                        Text(ficha.resumen)
                        Button(onClick = { model.marcar(ficha.id) }) {
                            Text(if (ficha.favorita) "Quitar de mi agenda" else "Agregar a mi agenda")
                        }
                    } else if (agenda != null) {
                        // La base ya se leyó y la charla no está: fue retirada del programa.
                        Text("Esta charla ya no figura en el programa.")
                    }
                } else {
                    Text(seccion, style = MaterialTheme.typography.headlineLarge)
                    when (seccion) {
                        "Programa" -> Catalogo(model, listaPrograma) { detalle = it }
                        "Mi agenda" -> if (agenda != null) {
                            val guardadas = agenda.charlas.filter { it.favorita }
                            if (guardadas.isEmpty()) Text("Todavía no agregaste charlas.")
                            Grilla(guardadas, listaAgenda) { detalle = it }
                        }
                        else -> Mesa(model.mesa)
                    }
                }
            }
        }
    }
}

/**
 * La última descarga falló pero hay una edición guardada: se sigue mostrando y se avisa
 * que los datos pueden estar desactualizados, con la fecha en que se recibieron.
 */
@Composable
private fun AvisoDatosGuardados(agenda: Agenda) {
    val edicion = agenda.edicion ?: return
    if (agenda.descarga != Descarga.Fallida) return
    val recibida = remember(edicion.recibida) {
        if (edicion.recibida <= 0L) ""
        else " del " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(edicion.recibida))
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()) {
        Text("No se pudo actualizar el programa. Se muestran los datos guardados$recibida.",
            Modifier.padding(12.dp))
    }
}
