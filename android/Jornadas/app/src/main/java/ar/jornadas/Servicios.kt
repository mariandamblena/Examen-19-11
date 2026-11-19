package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Una sola instancia por proceso. Antes cada ViewModel creaba la suya: una base Room,
 * un Retrofit y un contador de peticiones nuevos por pantalla.
 */
class Servicios private constructor(context: Context) {
    val mesa = ServicioLocal(context)
    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db")
        .addMigrations(MIGRACION_1_2) // conserva las charlas y marcas de la versión instalada
        .build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    val agenda = AgendaRepository(api, archivo.cartelera())

    companion object {
        @Volatile private var instancia: Servicios? = null

        fun de(context: Context): Servicios = instancia ?: synchronized(this) {
            // applicationContext: un objeto que vive todo el proceso no debe retener una Activity.
            instancia ?: Servicios(context.applicationContext).also { instancia = it }
        }
    }
}
