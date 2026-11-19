package ar.jornadas.data

import android.util.Log
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Estado del pedido de descarga en esta sesión (vive lo que vive el proceso).
 * Ninguna: todavía no se pidió nada. Completa / Fallida: cómo terminó el último pedido.
 */
enum class Descarga { Ninguna, EnCurso, Completa, Fallida }

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val edicion = dao.observarEdicion()

    private val _descarga = MutableStateFlow(Descarga.Ninguna)
    val descarga: StateFlow<Descarga> = _descarga.asStateFlow()

    private val automaticaPendiente = AtomicBoolean(true)

    /** Carga automática: una sola por proceso, aunque la pantalla se recree muchas veces. */
    suspend fun sincronizarAlAbrir() {
        if (automaticaPendiente.compareAndSet(true, false)) sincronizar()
    }

    /**
     * Descarga la instantánea vigente y la aplica. Si ya hay una petición en curso no
     * inicia otra (dos toques cuentan como un intento). Si falla, la base no se toca:
     * queda la edición anterior y el estado pasa a [Descarga.Fallida].
     */
    suspend fun sincronizar() {
        val anterior = _descarga.getAndUpdate { Descarga.EnCurso }
        if (anterior == Descarga.EnCurso) return
        var resultado = anterior // si se cancela, el estado vuelve a ser el de antes
        try {
            val fichas = api.consultar().map { it.comoCharla() }
            dao.reemplazarPrograma(fichas, System.currentTimeMillis())
            resultado = Descarga.Completa
        } catch (e: CancellationException) {
            throw e // la pantalla se cerró: no es una falla del servicio
        } catch (e: Exception) {
            // Sin señal (IOException), respuesta HTTP de error o cuerpo ilegible.
            Log.w("Agenda", "No se pudo sincronizar el programa", e)
            resultado = Descarga.Fallida
        } finally {
            _descarga.value = resultado // siempre se libera: después se puede volver a pedir
        }
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}

/** Una ficha incompleta invalida toda la respuesta: no se aplica una instantánea a medias. */
private fun FichaRemota.comoCharla() = Charla(
    id = exigir(id, "id"), titulo = exigir(title, "title"), tema = exigir(track, "track"),
    dia = exigir(day, "day"), hora = exigir(time, "time"), sala = exigir(room, "room"),
    ponente = exigir(speaker, "speaker"), resumen = exigir(summary, "summary"))

private fun <T : Any> exigir(valor: T?, campo: String): T =
    valor ?: throw JsonParseException("Ficha sin el campo $campo")
