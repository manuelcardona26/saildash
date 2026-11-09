# SailDash: launcher personal para el Chevrolet Sail

Nombre de trabajo; se puede cambiar. Tema visual: espacio / agujero negro, inspirado en *Interstellar*, con arte propio y sin usar imágenes de la película.

---

## 0. ¿Qué se puede y qué no? (resumen)

| Lo que pediste | ¿Se puede? | Cómo |
|---|---|---|
| CarPlay en esta pantalla | **Solo con un adaptador** (unos US$65) | Ningún software ni ROM puede agregarlo: Apple exige un chip MFi. La forma real es el adaptador Carlinkit CPC200-CCPA con la app AutoKit, que da CarPlay inalámbrico. SailDash le pone un botón en el dock. |
| Spotify, Waze y Maps | **Sí, por CarPlay** | Corren en tu iPhone y se ven en la pantalla. Instalados directamente en la pantalla, Waze y Maps ya no funcionan en Android 7.1. |
| Bluetooth rápido, que no se pegue | Parcial | El Bluetooth lo maneja el chip y la app de fábrica. SailDash muestra la canción y controla play y siguiente. La reconexión depende del chip XR829. |
| Radio bonita, con emisoras guardadas y cambio rápido | **Probable, falta el diagnóstico** | Primero hay que descubrir cómo se le dan órdenes a la radio de tu pantalla (§5). Si no se logra, SailDash usa la radio de fábrica, que se sigue escuchando igual. |
| Ecualizador pro (techno con bajos, voces claras) | **Probable, falta el diagnóstico** | Depende de dónde está el ecualizador: en Android o en la MCU (§5). |
| Tema Interestelar | **Hecho (v1.0.0)** | Fondo de estrellas con agujero negro y colores de espacio. |
| Modo noche automático | **Sí, ya viene** | Por la hora del sol o por los faros. |
| Tu carro en la pantalla | Sí | Por ahora, dibujo genérico con nombre y color. El Sail en 3D se puede agregar como imagen. |
| Taller: aceite, SOAT, tecnomecánica | **Sí, ya viene** | Ajustes → Tu auto → Recordatorios. |
| Temperatura del motor, voltaje y RPM | **Sí, con un ELM327** (unos COP 40–80 mil) | Tu pantalla no tiene caja CAN, así que el carro no le manda datos por sí solo. |
| Modo "solo velocidad" | Sí | Widget de velocímetro por GPS u OBD. Pendiente un modo de pantalla completa. |
| Pico y placa con aviso al encender | **Hecho (v1.0.0)** | Bogotá, Medellín, Cali u otra ciudad configurada a mano. Se salta los festivos. |
| Cámara de reversa sin fallas | Sí | La maneja la MCU, por encima de todo. SailDash no la toca. |
| Música en reversa (silenciar, bajar o no tocar) | **Probable, falta el diagnóstico** | Hay que saber si la pantalla avisa a las apps cuando entra la reversa (§5). Si no, se usa el ajuste de fábrica. |
| YouTube liviano | Sí | NewPipe, que funciona en Android 6 o más nuevo. El YouTube oficial ya no corre en 7.1. |
| Multiventana redimensionable | **Por probar** | Android 7 la trae, pero el fabricante pudo desactivarla. Si no está, plan B: mapa y música como widgets en el inicio. |
| Botón de actualizar desde GitHub | **Hecho** | Ajustes → Sistema → Actualizaciones. Se activa cuando el repositorio esté en tu GitHub. |
| Que sea súper rápido | Sí, dentro de lo que da la T3L | El APK pesa 1.9 MB y todo viene apagado de fábrica. La guía de ajustes del sistema está en §7. |
| Android más nuevo o "Android 13" | **No** | Para esta placa no existe de verdad. Intentarlo puede dejar la pantalla inservible. |

---

## 1. Diagnóstico de tu pantalla

| Dato | Lo que dice la pantalla | Lo que probablemente es |
|---|---|---|
| Modelo | A9 | Genérica china, placa **K2001N** de Allwinner |
| Procesador | T3L 1.5 GHz | 4 núcleos Cortex-A7, de **32 bits** y muy limitado |
| RAM / almacenamiento | 2 GB / 16 GB | Correcto |
| Android | "12" | **Casi seguro es Android 7.1 (Nougat)**. La "N" de K2001**N** indica Nougat y el menú de Ajustes tiene el diseño de Android 7. El "12" es un número falso que pone el fabricante. |
| Firmware | JYT, plataforma K20N (oct 2021) | Variante JYT de la familia T3, sin documentación pública |
| MCU | V4.0-FF01-20200213 | Chip aparte que maneja radio, encendido, botones y reversa |
| Caja CAN | "máquina universal {0-0-0-0-0}" | **No hay caja CAN**: la pantalla no recibe datos del carro (temperatura, RPM, etc.) |
| Bluetooth | XR829 | Chip Bluetooth/Wi-Fi de Allwinner |

**Cómo confirmar la versión real:** instala *CPU-Z* o *AIDA64* desde la Play Store y busca "API level". Si dice 25, es Android 7.1.

### Qué implica Android 7.1

- **No funcionan** las versiones actuales de Waze (pide Android 10), Google Maps ni YouTube oficial. Por eso tu pantalla "no deja" entrar a los servicios de Google y todo va lento.
- **Sí funcionan:**
  - NewPipe, para YouTube sin anuncios (pide Android 6).
  - Organic Maps y OsmAnd, mapas offline que gastan poco.
  - LiteDash, que pide Android 6.
  - AutoKit, la app del adaptador CarPlay.
- La pantalla dividida existe desde Android 7, pero el fabricante pudo haberla desactivado. Hay que probarla.
- Android 7.1 no tiene `DynamicsProcessing`, la herramienta moderna de Android para ecualizar.

### Lo que NO conviene hacer

- **No flashear ROMs de "Android 13".** Para esta placa no existen de verdad: son la misma versión con otro número. Además, un update.zip de otra variante puede dañar la pantalla sin arreglo.
- **No actualizar la MCU** sin el archivo exacto. Si falla, la pantalla puede no volver a encender o perder los botones.
- Antes de cualquier experimento fuerte, pídele al vendedor la **imagen completa (.img)** de tu pantalla. Con ella y PhoenixCard o PhoenixSuit se puede recuperar si algo sale mal.
- El **reset de fábrica sí es seguro** (Ajustes → Restaurar ajustes de fábrica). Borra apps y datos, pero no toca la MCU.
- En "Los ajustes de fábrica" (piden clave; las más comunes en T3 son 16176699, 1617, 8888 y 123456) están la cámara de reversa, la región de la radio, los botones del volante y el amplificador. **Toma foto de cada pantalla antes de cambiar algo.**

### Tranquilidad sobre el sonido

Lo que vamos a cambiar es solo el **software de la pantalla de inicio (launcher)**. El cableado, el amplificador y la antena siguen iguales. El riesgo de que la radio "suene feo" aparece cuando se cambia el aparato físico o el arnés, y aquí no se toca nada de eso.

---

## 2. CarPlay: la verdad

- **No se puede activar CarPlay solo con software.** Apple exige un chip de autenticación (MFi) dentro del hardware, y ninguna actualización o ROM lo agrega.
- **La solución real es un adaptador USB**, el **Carlinkit CPC200-CCPA** (unos US$65), con la app **AutoKit**:
  - Funciona en Android 4.4 o más nuevo, así que sirve en tu pantalla.
  - El iPhone se conecta sin cables y en la pantalla ves **CarPlay de verdad**, con Waze, Google Maps, Spotify y WhatsApp corriendo en el iPhone.
  - Como el trabajo lo hace el iPhone, **resuelve también que tu pantalla no pueda instalar Waze o Maps**.
  - Contras: en una T3L de 2 GB puede tener algo de retraso y desfase de audio, un problema conocido de este adaptador. El iPhone se carga por el USB de la pantalla.
- **"Miudrive Screen"**, la app que ya trae tu pantalla, es para duplicar la pantalla de un Android. **No sirve para CarPlay.**
- **Alternativa a considerar:** una pantalla nueva con CarPlay inalámbrico integrado, Android real y 4 GB de RAM (procesador UIS7862 o mejor) puede costar 2 o 3 veces lo que vale el adaptador. A cambio, todo andaría mucho más rápido. Si decides cambiar de pantalla, el launcher que hagamos también sirve en la nueva.

---

## 3. LiteDash: ingeniería inversa

Fuente: github.com/IrvingGuerra/litedash. Usa licencia GPL-3.0, tiene unas 31.000 líneas de Kotlin y Jetpack Compose, y pide como mínimo Android 6.

| Módulo | Qué hace | ¿Sirve en tu Sail? |
|---|---|---|
| dashboard / car | Inicio con widgets que se pueden mover, auto en 3D (13 modelos, no está el Sail) y modo carretera | Sí. Al Sail habría que hacerle sus imágenes. |
| obd | Escáner ELM327 por BT, BLE, Wi-Fi o USB: temperatura, voltaje, RPM, códigos de falla | Sí, con un adaptador ELM327. **Los datos de motor del ejemplo de Facebook salen de un ELM327**, no de la pantalla. |
| tpms | Presión de llantas con kits USB o sensores BT | Opcional |
| music | Spotify y apps de música (MediaSession), música BT de pantallas chinas y USB | Sí. La música BT de tu marca está por verificar. |
| Radio FM | **Solo la controla en pantallas TopWay y NWD.** En las demás solo abre la app de radio de fábrica. | **En la tuya no.** Hay que hacerle un "driver" (ver §5). |
| map | Mapa propio, liviano y por teselas | Sí |
| updater | Busca en GitHub Releases, descarga, verifica el SHA-256 e instala | Sí. Es justo lo que pediste. |
| Pantalla dividida | **Solo funciona desde Android 12L** | **En la tuya no.** Hay que hacerla con el método de Android 7 (ver §5). |
| Recordatorios | Servicio, seguro, placas y verificación, con aviso | Sí. Se adapta a SOAT y tecnomecánica. |
| Día/noche | Por la hora del sol, sensor de luz o un ajuste de la pantalla ligado a los faros | Sí |
| Ecualizador | **No tiene** | Hay que hacerlo |
| Pico y placa | **No tiene** | Hay que hacerlo |
| Reversa | **No la maneja** | Hay que hacerla |

- **Comentarios y forks:**
  - Hay 5 issues. Casi todos piden modelos de auto. Hay una queja de que la música por Bluetooth no muestra la canción en pantallas chinas.
  - Los 13 forks no tienen cambios propios.
  - No encontré otro launcher open source mejor. Los únicos proyectos útiles son de radio para otras marcas: OpenRadioFM, radio-plus-open y openlauncher. Ninguno es compatible con Allwinner T3.

**Recomendación: hacer un fork de LiteDash** y personalizarlo, en vez de empezar desde cero.

- Ya resuelve bien el 70% de lo que pides (OBD, música, mapa, actualizador, recordatorios, widgets) y está optimizado para pantallas lentas.
- Le agregamos lo tuyo: tema espacial, Sail, pico y placa, ecualizador, driver de radio, reversa y pantalla dividida para Android 7.
- Condición de la licencia GPL-3.0: si publicas tu versión, su código también debe ser abierto. Para uso personal no cambia nada, y publicarlo en GitHub es justamente lo que se necesita para las actualizaciones.

---

## 4. Funciones del launcher (lo que pediste)

### Inicio y apariencia

- Tema **"Horizonte de eventos"**: fondo de estrellas con parallax suave, anillo dorado de agujero negro y acentos ámbar y azul hielo. Animaciones apagadas por defecto para no gastar CPU.
- Modo noche automático: por la hora del sol en tu ciudad o por los faros. Pone el tema oscuro y baja el brillo.
- Tu Sail dibujado, del color de tu carro.
- **Modo "Solo velocidad"**: un velocímetro grande por GPS o por OBD y nada más.

### Taller

- Recordatorios con aviso días antes:
  - Cambio de aceite (por km o por fecha)
  - SOAT
  - Revisión técnico-mecánica
  - Impuesto vehicular
  - Licencia de conducción
  - Filtros, llantas y frenos
- Con un ELM327 (unos COP 40–80 mil):
  - Temperatura del motor, voltaje y RPM
  - Códigos de falla explicados
  - Alertas de recalentamiento o de batería que no carga

### Pico y placa

- Eliges tu ciudad y el último dígito de tu placa.
- **Al encender el carro** sale el aviso: "Hoy tienes pico y placa de 6:00 a 21:00", y te avisa antes de que empiece el horario.
- Las reglas vienen en un archivo `picoyplaca.json` publicado en tu GitHub. Cuando la alcaldía cambia la rotación de cada semestre, se actualiza ese archivo y la app lo descarga, sin reinstalar nada.
- No hay una API gratuita oficial: Medellín y Cali rotan cada semestre y Bogotá usa par/impar. Por eso se mantiene a mano.

### Audio

- **Ecualizador pro**:
  - Curva visual que se arrastra con el dedo y 10 bandas.
  - Presets: Techno/Bajo profundo, Voz clara, Podcast/Noticias, Plano y Personalizado.
  - "Bajo protegido": limita el refuerzo para que el bajo no distorsione.
  - Cambio de preset con un toque.
  - **Cómo se hace depende del diagnóstico (§5):**
    - **(a)** Si la pantalla acepta el ecualizador global de Android, la app lo controla directamente.
    - **(b)** Si el ecualizador está en la MCU, se controla mandando órdenes a la app "Sonido" o a su servicio.
    - **(c)** Si no se puede ninguna de las dos, el ecualizador solo aplica a la música que reproduce el launcher (USB y radio por internet), y para lo demás se abre "Sonido" con un atajo.
- **Radio FM nueva**:
  - Dial horizontal que se arrastra, botones de ±0.1 y búsqueda automática.
  - **Emisoras guardadas con nombre** (La Mega, Olímpica…) y "buscar y guardar todas".
  - Botones grandes para cambiar de emisora rápido.
  - Requiere descubrir cómo habla la radio de tu pantalla (§5).
- **Bluetooth**: reconexión automática al iPhone, acceso rápido para emparejar un teléfono nuevo y la canción con su carátula cuando la pantalla la envía.

### Reversa

Tres opciones a elegir: silenciar la música, bajarla un 50%, bajarla un poco o no tocarla.

- La imagen de la cámara la maneja la MCU y queda siempre encima, así que el launcher no puede dañarla.
- Para bajar o silenciar el volumen la app necesita recibir el aviso de que entró la reversa. Hay que descubrirlo (§5).
- Si no se logra, se usa el ajuste de fábrica "volumen en reversa", si tu pantalla lo tiene.

### Apps y multiventana

- **Apps ligeras**:
  - NewPipe (YouTube)
  - Organic Maps (navegación offline)
  - AutoKit (CarPlay)
  - Spotify Lite, si la tienda lo deja instalar
- Pantalla dividida de Android 7 activada con un servicio de accesibilidad:
  - Mapa a un lado y Spotify o video al otro.
  - El tamaño se cambia arrastrando la barra del medio.
  - Si el fabricante desactivó la multiventana, queda como plan B el widget de mapa y música dentro del inicio, como en el ejemplo de Facebook.

### Actualizaciones

- Botón **"Buscar actualización"**. La app descarga el APK nuevo de **tu** GitHub Releases, verifica que no esté dañado e instala con un toque, sin reinstalar desde cero ni perder la configuración.
- GitHub Actions compila el APK en la nube cada vez que se publica una mejora, así que no necesitas Android Studio.

---

## 5. Lo que hay que averiguar de tu pantalla

Sin esto no se puede hacer la radio, el ecualizador ni la reversa. Hay dos caminos:

**A. Con ADB por Wi-Fi.** Es lo mejor.

1. Pon la pantalla y el PC en el mismo Wi-Fi (por ejemplo el hotspot del iPhone).
2. En la pantalla activa "Opciones de desarrollador": Acerca de → tocar 7 veces "Número de compilación". Luego activa "Depuración por red/ADB".
3. Desde el PC se listan las apps de fábrica y se copian las de radio, sonido y reversa para analizarlas.
4. Se escuchan los avisos del sistema mientras pones la reversa, prendes las luces y cambias de emisora.

**B. Sin PC en el carro.**

1. Instala **App Manager** (F-Droid, gratis, funciona en Android 5 o más nuevo).
2. Exporta a una memoria USB los APK de: Radio, Sonido, Mi auto, Info del coche, Música BT y la cámara de reversa.
3. Me los traes al PC y los descompilo para encontrar sus comandos.

**Además:** instala hoy mismo el APK de LiteDash para medir qué tan fluido va en tu pantalla. Si va bien, el fork irá igual.

---

## 6. Fases

1. **Diagnóstico** (§5)
   - Confirmar la versión de Android.
   - Sacar los APK de fábrica y descubrir los comandos de radio, ecualizador y reversa.
2. **Base**
   - Fork de LiteDash en tu GitHub con nuevo nombre y paquete.
   - Actualizador apuntando a tu repositorio.
   - Compilación automática con GitHub Actions.
   - Tema espacial.
   - Sail en el catálogo.
3. **Colombia**
   - Pico y placa con su JSON remoto.
   - Recordatorios de SOAT y tecnomecánica.
   - Textos en español de Colombia.
4. **Audio**
   - Driver de radio para tu pantalla.
   - Pantalla nueva de radio con emisoras guardadas.
   - Ecualizador pro.
   - Comportamiento en reversa.
5. **Multiventana para Android 7 y modo solo velocidad**
6. **CarPlay**: llega el adaptador CCPA, se instala AutoKit y se agrega su acceso en el dock.

---

## 7. Sacarle el máximo rendimiento a la T3L

En la app (ya hecho):
- APK de 1.9 MB, optimizado con R8 y con perfil de arranque.
- Todo viene apagado hasta que lo prendas.
- El fondo Interestelar se dibuja una sola vez.

En la pantalla (lo haces tú, una vez):
1. **Reset de fábrica limpio**: Ajustes → Restaurar ajustes de fábrica. Antes, toma fotos de "Los ajustes de fábrica".
2. **Opciones de desarrollador**: Acerca de → toca 7 veces "Número de compilación". Luego:
   - Las tres escalas de animación en **0.5x** o desactivadas. Es lo que más se nota.
   - "Limitar procesos en segundo plano": **máximo 3 procesos**.
3. **Desactivar lo que no usas**: Ajustes → Aplicaciones → la app → Desactivar. Por ejemplo:
   - Miudrive Screen
   - Salvapantallas
   - Instrucciones
   - El launcher de fábrica, después de poner SailDash como inicio
   - **No desactives** Radio, Sonido, Bluetooth, la cámara de reversa ni "Mi auto"/"Info del coche".
4. **Pocas apps instaladas**: SailDash, AutoKit (CarPlay), NewPipe y Organic Maps. Cada app que corre en segundo plano le quita RAM a todo lo demás.
5. Pon SailDash como pantalla de inicio y activa **"Abrir SailDash al encender"** para que el launcher de fábrica no vuelva a aparecer.
