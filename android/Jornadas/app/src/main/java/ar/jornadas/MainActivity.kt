package ar.jornadas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    private val model: ProgramaModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.hasExtra("servicio_disponible")) {
            model.mesa.disponible = intent.getBooleanExtra("servicio_disponible", true)
            intent.removeExtra("servicio_disponible")
        }
        // Después de aplicar el extra, para que la primera petición vea esa disponibilidad.
        // Reemplaza a la recarga que había en onStart: onCreate también corre al rotar,
        // pero el repositorio solo descarga la primera vez de cada proceso.
        model.cargaAutomatica()
        setContent { Portada(model) }
    }
}
