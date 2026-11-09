# SailDash

Launcher personal para la pantalla Android (Allwinner T3L, Android 7.1) de un **Chevrolet Sail**, con tema
**Interestelar** y funciones para Colombia. Es una versión de [LiteDash](https://github.com/IrvingGuerra/litedash)
de Irving Guerra (GPL-3.0); todo lo bueno de base es trabajo suyo.

Plan, diagnóstico de la pantalla y qué se puede y qué no: [`PLAN.md`](PLAN.md).

## Lo que agrega SailDash
- Tema Interestelar: fondo de estrellas y agujero negro dibujado una sola vez (no gasta procesador).
- Pico y placa (Bogotá, Medellín, Cali u "Otra ciudad") con aviso al encender y festivos de Colombia calculados.
  Las reglas se actualizan desde `app/src/main/res/raw/pico_y_placa.json` en GitHub sin reinstalar.
- Nombre, ícono y paquete propios (`co.saildash.launcher`). Sin conteo anónimo ni reportes a servidores ajenos.

## Compilar
JDK 17 y el SDK de Android (plataforma 37).

```bash
./gradlew assembleGithubRelease
```

La llave de firma vive **fuera** del repositorio (`key.properties`, ignorado por git). Sin ella, firma de depuración.

## Publicar una actualización
1. Cambia `VERSION_NAME` en `version.properties` y agrega su sección en `CHANGELOG.md`.
2. `git tag v1.0.1 && git push --tags`
3. GitHub Actions compila, firma y crea el Release. En el carro: Ajustes → Sistema → Actualizaciones.

Secretos del repositorio que necesita Actions: `SAILDASH_KEYSTORE_BASE64`, `SAILDASH_STORE_PASSWORD`,
`SAILDASH_KEY_ALIAS`, `SAILDASH_KEY_PASSWORD`. En `gradle.properties`, `saildash.repo=usuario/repositorio`.

## Licencia
GPL-3.0 (igual que LiteDash). El nombre y logo de LiteDash no se usan en esta versión.
