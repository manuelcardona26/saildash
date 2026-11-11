# Cambios de SailDash

SailDash es una versión personal de [LiteDash](https://github.com/IrvingGuerra/litedash) (GPL-3.0)
para un Chevrolet Sail con pantalla Android T3L. Lo de LiteDash está en `CHANGELOG-LITEDASH.md`.

## [1.2.0] - 2026-11-11

### Agregado
- Zona de sonido (Ajustes → Sonido → Zona de sonido): tu Sail visto desde arriba con sus cuatro parlantes; arrastras el punto o eliges Todo el carro, Conductor, Copiloto, Adelante, Atrás o Mi zona. Muestra el balance y el adelante/atrás.
- El Diagnóstico ahora pide mover el balance de la app Sonido y lo resalta en el reporte, para que una próxima versión lo aplique directo al chip de sonido del carro. Mientras tanto, el botón "Aplicar en el de fábrica" abre el sonido de fábrica.

## [1.1.2] - 2026-11-10

### Cambiado
- La foto del Sail viene incluida en todas las versiones, también en la que se descarga de GitHub.

## [1.1.1] - 2026-11-10

### Cambiado
- Los botones de Bluetooth (inicio, Carro y Mapa) abren la app de Bluetooth de tu pantalla (Teléfono / Música BT), no los ajustes de Android. Si abre otra, elígela en Ajustes → Apps → Música → Bluetooth.

## [1.1.0] - 2026-11-10

### Diseño nuevo: "Hielo y órbita"
- Inicio como tu foto de referencia: tu Sail con faros y reflejo, velocidad con órbita, clima con su cielo, Wi-Fi y Bluetooth, telemetría, música, destinos y pico y placa.
- Páginas con flechas: Carro, Carretera (tu carro andando a tu velocidad) y Mapa. Dock abajo con nombres y la hora.
- Fondos con un agujero negro renderizado con física real. Ajustes, apps y paneles en el mismo estilo.
- Bienvenida sin teclado: carro, ciudad y placa para pico y placa.

### Agregado
- Modo galaxia (solo velocidad): la vía hacia Gargantúa con la Vía Láctea, velocidad gigante y alerta de límite.
- Velocímetro Sport.
- Radio FM con emisoras guardadas (sintonía directa pendiente del diagnóstico de tu pantalla).
- Ecualizador para las apps con presets, "Bajo protegido" y grupo Urbano: Reggaetón, Techno y Electrónica.
- Taller sin OBD: computador de viaje, odómetro por GPS, tanqueadas (km/galón, $/km) y bitácora; aceite por km.
- Reversa: bajar, silenciar o no tocar la música; vista de reversa con líneas calibrables si la pantalla deja ver la cámara.
- Pantalla dividida en Android 7 (mapa + música), tarjeta de Bluetooth, apps recomendadas.
- Diagnóstico de tu pantalla con grabación de eventos y exportación a la USB.
- Recordatorios a la colombiana: SOAT, técnico-mecánica, impuesto, extintor, licencia.

## [1.0.0] - 2026-11-09

### Agregado
- **Tema Interestelar**: fondo de estrellas con un agujero negro y su disco dorado, dibujado una sola vez (no gasta procesador).
- **Pico y placa**: Bogotá, Medellín, Cali u "Otra ciudad" a mano. Aviso en el inicio al encender ("Hoy tienes pico y placa") y la noche anterior. Festivos de Colombia calculados solos.
- **Liviano**: el APK pesa 1.9 MB y todo viene apagado de fábrica. La guía para acelerar la pantalla (animaciones, apps que sobran) está en PLAN.md.

### Cambiado
- Nombre, ícono y paquete propios (se instala junto a LiteDash sin chocar).
- Sin conteo anónimo ni reportes a servidores ajenos.
