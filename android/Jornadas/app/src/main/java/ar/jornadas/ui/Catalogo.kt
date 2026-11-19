package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla
import ar.jornadas.data.Descarga

private val DIAS = listOf("Ambos días", "Día 1", "Día 2")
private val TEMAS = listOf(TODOS, "Android", "Datos", "Calidad")

@Composable
fun Catalogo(model: ProgramaModel, lista: LazyListState = rememberLazyListState(), abrir: (String) -> Unit) {
    // Sin LaunchedEffect: entrar a esta pantalla (rotar, volver de una ficha, cambiar de
    // pestaña) no debe descargar nada. Solo dibuja el estado que publica el ViewModel.
    val agenda = model.agenda.collectAsStateWithLifecycle().value ?: return
    val edicion = agenda.edicion
    val descargando = agenda.descarga == Descarga.EnCurso
    val hayCharlas = edicion != null && edicion.cantidad > 0
    // Filtros, botón y avisos son el primer elemento de la lista: toda la pantalla se
    // desplaza junta, y con poca altura (horizontal) las charlas siguen al alcance.
    Grilla(if (hayCharlas) agenda.visibles else emptyList(), lista, encabezado = {
        // Los filtros no dependen de la descarga: siguen editables durante la espera.
        Selector(DIAS, agenda.dia, model::elegirDia)
        Selector(TEMAS, TEMAS.indexOf(agenda.tema)) { model.elegirTema(TEMAS[it]) }
        TextButton(onClick = model::recargar, enabled = !descargando) { Text("Actualizar") }
        if (descargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            edicion == null -> SinEdicion(agenda.descarga, model::recargar)
            edicion.cantidad == 0 -> Text("Esta edición llegó sin charlas programadas.")
            agenda.charlas.isNotEmpty() && agenda.visibles.isEmpty() ->
                Text("Ninguna charla coincide con los filtros elegidos.")
        }
    }, abrir = abrir)
}

/** Nunca hubo una descarga exitosa: no hay datos guardados que mostrar. */
@Composable
private fun SinEdicion(descarga: Descarga, reintentar: () -> Unit) {
    when (descarga) {
        // Completa: la descarga terminó y Room todavía está por avisar la edición nueva
        // (lo hace unos milisegundos después de confirmar la transacción).
        Descarga.EnCurso, Descarga.Completa -> Text("Descargando el programa…")
        Descarga.Fallida -> {
            Text("No se pudo descargar el programa y todavía no hay una edición guardada.")
            OutlinedButton(onClick = reintentar) { Text("Reintentar") }
        }
        Descarga.Ninguna -> {
            Text("Todavía no se descargó el programa.")
            OutlinedButton(onClick = reintentar) { Text("Reintentar") }
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, lista: LazyListState = rememberLazyListState(),
    encabezado: (@Composable ColumnScope.() -> Unit)? = null, abrir: (String) -> Unit) {
    // fillMaxWidth: sin esto la lista mide lo que su contenido y, cuando solo se ve el
    // encabezado, no se puede deslizar desde la parte vacía de la pantalla.
    LazyColumn(state = lista, modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        if (encabezado != null) item(key = "__encabezado") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                content = encabezado)
        }
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
