package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

// DTO: campos anulables porque Gson no respeta los tipos no nulos de Kotlin
// (un campo ausente llegaría como null o como 0 sin avisar).
data class FichaRemota(val id: String?, val title: String?, val track: String?, val day: Int?,
    val time: String?, val room: String?, val speaker: String?, val summary: String?)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/**
 * Constancia de la última instantánea recibida con éxito (una sola fila, id = 0).
 * Si no hay fila, nunca hubo una descarga. Si hay fila con cantidad = 0, la edición
 * vigente es válida y está vacía. `recibida` = 0 significa "fecha desconocida".
 */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 0, val recibida: Long, val cantidad: Int)

@Dao
interface CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT * FROM edicion WHERE id = 0")
    fun observarEdicion(): Flow<Edicion?>

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)

    @Query("SELECT id FROM charlas")
    suspend fun idsVigentes(): List<String>

    @Query("DELETE FROM charlas WHERE id IN (:ids)")
    suspend fun retirar(ids: List<String>)

    // No nombra la columna `favorita`: la sincronización no puede pisar la marca del usuario.
    @Query("UPDATE charlas SET titulo = :titulo, tema = :tema, dia = :dia, hora = :hora, " +
        "sala = :sala, ponente = :ponente, resumen = :resumen WHERE id = :id")
    suspend fun actualizarFicha(id: String, titulo: String, tema: String, dia: Int, hora: String,
        sala: String, ponente: String, resumen: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun agregar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarEdicion(edicion: Edicion)

    /**
     * Aplica una instantánea completa del programa en una sola transacción: o queda
     * toda la edición nueva o queda la anterior, nunca una mezcla.
     */
    @Transaction
    suspend fun reemplazarPrograma(fichas: List<Charla>, recibida: Long) {
        val vigentes = idsVigentes().toSet()
        val publicadas = fichas.map { it.id }.toSet()
        // Las que ya no se publican se retiran con su marca: si regresan, regresan sin marcar.
        (vigentes - publicadas).chunked(500).forEach { retirar(it) }
        // Las que siguen vigentes solo cambian sus datos de programa (título, sala...).
        fichas.filter { it.id in vigentes }.forEach {
            actualizarFicha(it.id, it.titulo, it.tema, it.dia, it.hora, it.sala, it.ponente, it.resumen)
        }
        // Las nuevas entran sin marca.
        agregar(fichas.filter { it.id !in vigentes })
        registrarEdicion(Edicion(recibida = recibida, cantidad = publicadas.size))
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

/**
 * La versión 1 ya está instalada con charlas y marcas guardadas: se agrega la tabla
 * nueva sin tocar `charlas`. Quien ya tenía charlas ya tenía una edición descargada,
 * así que se le deja constancia para que la siga viendo sin señal.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, " +
            "`recibida` INTEGER NOT NULL, `cantidad` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        val guardadas = db.query("SELECT COUNT(*) FROM charlas").use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (guardadas > 0) {
            db.execSQL("INSERT INTO `edicion` (`id`, `recibida`, `cantidad`) VALUES (0, 0, $guardadas)")
        }
    }
}
