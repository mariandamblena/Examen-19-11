package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import ar.jornadas.data.Descarga
import ar.jornadas.data.Edicion
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

const val TODOS = "Todos"

/** Estado de la pantalla en un único valor coherente. */
data class Agenda(
    val edicion: Edicion?,       // null: todavía no hubo ninguna descarga exitosa
    val charlas: List<Charla>,   // edición guardada completa (fichas y Mi agenda)
    val visibles: List<Charla>,  // la misma edición con los filtros aplicados
    val descarga: Descarga,
    val dia: Int,                // 0 = ambos días
    val tema: String,
)

class ProgramaModel(app: Application, private val guardado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    // Filtros en SavedStateHandle: sobreviven a la rotación, a salir de la lista y a que
    // Android mate el proceso y recupere la tarea. Una tarea nueva arranca sin estado guardado.
    private val filtroDia = guardado.getStateFlow(DIA, 0)
    private val filtroTema = guardado.getStateFlow(TEMA, TODOS)

    /** null mientras se lee la base local por primera vez. */
    val agenda: StateFlow<Agenda?> =
        combine(repo.programa, repo.edicion, repo.descarga, filtroDia, filtroTema) { charlas, edicion, descarga, dia, tema ->
            val visibles = charlas.filter { (dia == 0 || it.dia == dia) && (tema == TODOS || it.tema == tema) }
            Agenda(edicion, charlas, visibles, descarga, dia, tema)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegirDia(valor: Int) { guardado[DIA] = valor }
    fun elegirTema(valor: String) { guardado[TEMA] = valor }

    /** Se pide al crear la pantalla; el repositorio garantiza que ocurra una vez por proceso. */
    fun cargaAutomatica() { viewModelScope.launch { repo.sincronizarAlAbrir() } }

    /** Actualización explícita (Actualizar / Reintentar). */
    fun recargar() { viewModelScope.launch { repo.sincronizar() } }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    private companion object {
        const val DIA = "filtro_dia"
        const val TEMA = "filtro_tema"
    }
}
