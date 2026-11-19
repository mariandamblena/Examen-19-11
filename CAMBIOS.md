# Jornadas del Sur · Problemas encontrados y soluciones

**Repositorio con la solución:** https://github.com/mariandamblena/Examen-19-11

Proyecto: `android/Jornadas` (Kotlin, Jetpack Compose, Room, Retrofit). Consigna: [`README.md`](README.md).

## Resumen

| # | Problema | Tipo | Solución |
|---|---|---|---|
| 0 | El proyecto no compilaba | Mala práctica: dependencia usada sin declarar | Declarar OkHttp 4.12.0 |
| 41 | Ante una falla desaparecía la lista; no se distinguía "nunca descargué" de "edición vacía" | Antipatrón: convertir un error en una pantalla vacía | Constancia persistente de la edición recibida y estado de pantalla que conserva los datos |
| 42 | Cada descarga borraba todas las marcas y nunca retiraba charlas | Antipatrón: la sincronización pisa datos del usuario | Transacción que aplica la instantánea sin escribir la marca |
| 43 | Los filtros se perdían al abrir una ficha, al rotar o al morir el proceso | Mala práctica: estado guardado en el lugar equivocado | Filtros en el ViewModel con `SavedStateHandle` |
| 44 | Se descargaba al rotar, al volver de una ficha y al cambiar de pestaña; sin freno al doble toque | Mala arquitectura: dependencias creadas por el ViewModel y descargas disparadas por la vista | Instancia única de servicios por proceso y control de descargas en el repositorio |
| — | Al actualizar la app había que conservar los datos | Requisito de la consigna | Migración de Room 1 → 2 sin pérdida |

## 0. El proyecto no compilaba

**Problema.** `./gradlew assembleDebug` fallaba en `lab/ServicioLocal.kt` con `Unresolved reference: Companion`, `toMediaType` y `toResponseBody`.

**Causa.** El código usa clases de OkHttp directamente (`OkHttpClient`, `Interceptor`) y con la API de la versión 4, pero el proyecto no declaraba OkHttp. Llegaba de forma transitiva por Retrofit 2.11.0, que trae la versión 3.14.9.

**Solución.** Una línea en [`app/build.gradle.kts`](android/Jornadas/app/build.gradle.kts): `implementation("com.squareup.okhttp3:okhttp:4.12.0")`. No se modificó el servicio de prueba.

## Pedido 41 · Programa sin señal

**Problema.** Con una falla de red la lista desaparecía, aunque los datos estaban guardados. Además, una edición válida con cero charlas (v4) era indistinguible de no haber descargado nunca.

**Causa.**
- `Catalogo` mostraba el aviso **o** la lista: `if (aviso.isNotEmpty()) { aviso + Reintentar } else { Grilla(...) }`.
- El estado eran banderas sueltas (`aviso`, `cargando`) sin relación con lo guardado.
- "Tabla `charlas` vacía" tenía dos significados posibles.

**Solución.**
- Entidad nueva `Edicion` (tabla `edicion`, una sola fila): constancia de la última instantánea recibida con éxito, con su fecha y la cantidad de charlas. Sin fila, nunca hubo descarga; con `cantidad = 0`, la edición es válida y está vacía.
- El repositorio publica el estado de la descarga (`Ninguna`, `EnCurso`, `Completa`, `Fallida`). Una falla no toca la base.
- El ViewModel combina edición, charlas, descarga y filtros en un único estado (`Agenda`). La pantalla decide con eso:

| Situación | Qué se muestra |
|---|---|
| Hay edición y la descarga falló | Lista, ficha y Mi agenda intactas, más el aviso "No se pudo actualizar el programa. Se muestran los datos guardados del dd/MM HH:mm." |
| No hay edición y la descarga falló | "No se pudo descargar el programa…" con el botón "Reintentar" |
| Edición con cero charlas | "Esta edición llegó sin charlas programadas." (también al reabrir sin señal) |
| Hay charlas pero el filtro no coincide | "Ninguna charla coincide con los filtros elegidos." |

Archivos: [`data/Programa.kt`](android/Jornadas/app/src/main/java/ar/jornadas/data/Programa.kt), [`data/AgendaRepository.kt`](android/Jornadas/app/src/main/java/ar/jornadas/data/AgendaRepository.kt), [`ui/ProgramaModel.kt`](android/Jornadas/app/src/main/java/ar/jornadas/ui/ProgramaModel.kt), [`ui/Catalogo.kt`](android/Jornadas/app/src/main/java/ar/jornadas/ui/Catalogo.kt), [`ui/Portada.kt`](android/Jornadas/app/src/main/java/ar/jornadas/ui/Portada.kt).

## Pedido 42 · Agenda personal

**Problema.** Después de cualquier descarga se perdían todas las marcas de "Mi agenda". Las charlas retiradas del programa seguían apareciendo para siempre.

**Causa.**
- `CarteleraDao.guardar` usaba `@Insert(onConflict = REPLACE)`: borra la fila y la inserta de nuevo.
- El repositorio construía cada `Charla` sin indicar `favorita`, así que la fila nueva entraba con `favorita = false`.
- No existía ninguna operación que eliminara las charlas ausentes de la instantánea.

**Solución.** `CarteleraDao.reemplazarPrograma`, con `@Transaction`, aplica la instantánea completa:

1. Retira las charlas que ya no se publican. Se van con su marca, así que si regresan lo hacen sin marcar.
2. Actualiza las vigentes con un `UPDATE` que **no nombra la columna `favorita`**: título, sala y demás datos cambian, la marca no.
3. Inserta las nuevas sin marca.
4. Registra la edición (fecha y cantidad).

Como la sincronización nunca escribe la marca, un cambio hecho por el usuario mientras la petición está en curso se respeta cuando llega la respuesta. Al ser una transacción, no puede quedar una mezcla de dos ediciones.

Archivo: [`data/Programa.kt`](android/Jornadas/app/src/main/java/ar/jornadas/data/Programa.kt).

## Pedido 43 · Volver al punto de lectura

**Problema.** Al abrir una ficha y volver, o al rotar, se perdía el filtro de tema. Si Android mataba el proceso, se perdían los dos filtros.

**Causa.** El estado de la pantalla estaba repartido en tres mecanismos:
- El tema, en `remember` dentro de `Catalogo`: se pierde cuando el composable sale de la composición (al abrir una ficha) y al rotar.
- El día, en un `MutableStateFlow` del ViewModel: sobrevive a la rotación, pero no a la muerte del proceso. Además la vista lo modificaba directamente (`model.dia.value = it`).
- Sección y ficha abierta, en `rememberSaveable` (esto ya funcionaba).

**Solución.** Los dos filtros pasan al ViewModel y se guardan en `SavedStateHandle`. Sobreviven a salir de la lista, a la rotación y a la recuperación de la tarea tras la muerte del proceso; una tarea nueva no trae estado guardado y arranca sin filtros. La vista ya no modifica el estado: llama a `elegirDia` y `elegirTema`.

Archivos: [`ui/ProgramaModel.kt`](android/Jornadas/app/src/main/java/ar/jornadas/ui/ProgramaModel.kt), [`ui/Catalogo.kt`](android/Jornadas/app/src/main/java/ar/jornadas/ui/Catalogo.kt).

## Pedido 44 · Control de descargas

**Problema.** Al abrir la app se hacían dos peticiones. Rotar, volver de una ficha o cambiar de pestaña hacían más. Dos toques en "Actualizar" lanzaban dos peticiones. El contador de Mesa podía reiniciarse sin que muriera el proceso.

**Causa.**
- `MainActivity.onStart()` llamaba a `model.recargar()`: se ejecuta en cada rotación y al volver del fondo.
- `Catalogo` tenía `LaunchedEffect(Unit) { model.recargar() }`: se ejecuta cada vez que el composable entra en la composición.
- `recargar()` no comprobaba si ya había una petición en curso.
- `ProgramaModel` creaba su propio `Servicios(app)`: una base Room, un Retrofit y un contador por cada ViewModel, no por proceso.

**Solución.**
- `Servicios` es una única instancia por proceso (`Servicios.de(context)`, con `applicationContext`).
- El repositorio garantiza una sola carga automática por proceso (`AtomicBoolean`) y una sola petición a la vez: si hay una en curso, el pedido nuevo se ignora. Al terminar, con éxito o con falla, se puede volver a pedir.
- Se quitaron la recarga de `onStart` y el `LaunchedEffect`. La Activity pide la carga automática en `onCreate`, después de aplicar el extra `servicio_disponible`.
- "Actualizar" se deshabilita durante la petición. Los filtros no dependen de la descarga y siguen editables.

Archivos: [`Servicios.kt`](android/Jornadas/app/src/main/java/ar/jornadas/Servicios.kt), [`data/AgendaRepository.kt`](android/Jornadas/app/src/main/java/ar/jornadas/data/AgendaRepository.kt), [`MainActivity.kt`](android/Jornadas/app/src/main/java/ar/jornadas/MainActivity.kt), [`ui/Catalogo.kt`](android/Jornadas/app/src/main/java/ar/jornadas/ui/Catalogo.kt).

## Conservación de los datos al actualizar

Agregar la tabla `edicion` cambia el esquema, y la versión instalada ya tiene charlas y marcas guardadas. La base pasa a la versión 2 con `MIGRACION_1_2`:

- Crea la tabla `edicion` sin tocar `charlas`.
- Si ya había charlas guardadas, deja constancia de edición, para que ese usuario siga viendo su programa sin señal.

No se usa `fallbackToDestructiveMigration` ni se cambia el nombre de la base.

## Otros problemas corregidos

- **Manejo de errores.** Solo se capturaba `IOException`; una respuesta HTTP de error o un JSON ilegible podían cerrar la app. Ahora cualquier falla de la descarga se registra en el log y se informa, y `CancellationException` se relanza.
- **DTO.** `FichaRemota` declaraba campos no nulos, pero Gson no respeta los tipos de Kotlin: un campo ausente llegaba como `null` o como `0` sin avisar. Los campos pasan a ser anulables y una ficha incompleta invalida toda la respuesta, para no aplicar una instantánea a medias.
- **Ficha retirada.** Si la charla abierta se retiraba del programa, la ficha quedaba en blanco. Ahora informa que ya no figura en el programa.
- **Horizontal.** Con el teléfono en horizontal, los filtros y el botón ocupaban todo el alto y la lista no se veía. Ahora forman parte de la lista y se desplazan con ella.

No se modificaron el servicio de prueba (`lab/`), el panel Mesa ni `legacy/`.

## Verificación

Compilación: `./gradlew assembleDebug` correcto (JDK 21, Android SDK 34).

Recorrido en emulador (Pixel 9, Android 17), siguiendo la tabla de la consigna:

| Caso | Resultado observado |
|---|---|
| 41 · `pm clear` y arranque con `--ez servicio_disponible false` | Mensaje de descarga fallida con "Reintentar" |
| 41 · Servicio apagado con una edición previa | Lista, ficha y Mi agenda intactas, con el aviso de datos guardados |
| 41 · v4 y reapertura sin señal | "Esta edición llegó sin charlas programadas." y el aviso; sin "Reintentar" |
| 42 · v1 → v2 con la charla 11 marcada | Conserva la marca; muestra "Compose en producción avanzada" y "Sala Puerto" |
| 42 · v3 | Desaparece la 11 de Programa y de Mi agenda; entra "Clínica de trazas"; las otras marcas siguen |
| 42 · Volver a v1 | La 11 regresa sin marca |
| 42 · Marcar y desmarcar durante una petición de 8 s | Ambos cambios se mantienen al llegar la respuesta |
| 43 · Día 2 + Datos, abrir ficha, volver, rotar | Dos resultados en todos los pasos |
| 43 · Ficha abierta, Inicio, `am kill`, regresar | Se recupera la ficha; al volver, ambos filtros |
| 43 · Tarea nueva | Sin filtros |
| 44 · Contador de Mesa | 1 al abrir; rotar, abrir una ficha y cambiar de pestaña no suman; dos toques suman 1 |
| 44 · Durante la espera | Los filtros responden |
| Actualización sobre la versión original con una charla marcada | Se conservan la lista y la marca |

La app no registró ningún cierre inesperado durante el recorrido.

## Uso de IA

Se usó Claude Code (Anthropic) como asistente para analizar el código
