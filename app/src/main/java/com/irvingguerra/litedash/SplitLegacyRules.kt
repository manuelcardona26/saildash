package com.irvingguerra.litedash

/*
 * SailDash: pantalla dividida de Android 7 a 12 ([SplitLegacy]). Aquí solo la lógica, sin Android,
 * para poder probarla: qué app va en cada mitad y cómo se abre cada una para que caiga en su lado.
 */

/** Las dos mitades de la pantalla acostada: el mapa a la izquierda y la música a la derecha. */
enum class SplitSide { LEFT, RIGHT }

/**
 * Cómo se abre una app de la pareja. [adjacent] = con FLAG_ACTIVITY_LAUNCH_ADJACENT ("al lado");
 * [stack] = la pila de Android 7–8 donde debe quedar (null = la que Android decida).
 */
data class SplitLaunch(val adjacent: Boolean, val stack: Int?)

object SplitPair {
    /** Mapas que van a la izquierda, en orden de preferencia: Organic Maps, OsmAnd (+ y gratis), Waze, Google Maps. */
    val MAP_APPS = listOf("app.organicmaps", "net.osmand.plus", "net.osmand", "com.waze", "com.google.android.apps.maps")

    /** AutoKit, la app del adaptador de CarPlay: a la izquierda si no hay mapa. */
    const val AUTOKIT = "cn.manstep.phonemirrorBox"

    /** Mitad izquierda de fábrica: el primer mapa instalado o, si no hay, AutoKit. */
    val LEFT_APPS = MAP_APPS + AUTOKIT

    /** Mitad derecha de fábrica: Spotify, NewPipe (YouTube liviano) o YouTube. */
    val RIGHT_APPS = listOf("com.spotify.music", "org.schabi.newpipe", "com.google.android.youtube")

    /** Pilas de Android 7–8 (ActivityManager.StackId): la de pantalla completa (al dividir, la mitad derecha) y la acoplada (la izquierda). */
    const val STACK_FULLSCREEN = 1
    const val STACK_DOCKED = 3

    /** Desde Android 9 ya no hay pilas: las ventanas van por modo y una app nueva cae sola en la mitad libre. */
    private const val PIE = 28

    /**
     * La app de la izquierda: la elegida si sigue instalada; si no, la primera de [LEFT_APPS] que
     * haya. Nunca [avoid] (la que ya va en la otra mitad).
     */
    fun left(installed: Set<String>, chosen: String?, avoid: String? = null): String? =
        (listOfNotNull(chosen) + LEFT_APPS).firstOrNull { it in installed && it != avoid }

    /**
     * La app de la derecha: la elegida si sigue instalada; si no, Spotify, NewPipe o YouTube y, si
     * no hay ninguna, tu app de música de SailDash ([music]). Nunca [avoid] (la de la izquierda).
     */
    fun right(installed: Set<String>, chosen: String?, music: String?, avoid: String? = null): String? =
        (listOfNotNull(chosen) + RIGHT_APPS + listOfNotNull(music)).firstOrNull { it in installed && it != avoid }

    /**
     * De qué lado va [pkg] al abrirla desde SailDash: la de cada mitad ([left], [right]), los mapas
     * ([maps]) a la izquierda y la música ([music]) a la derecha. null = ni mapa ni música: se abre
     * normal. AutoKit solo se divide si es la de la izquierda (abierto solo, CarPlay va completo).
     */
    fun sideOf(
        pkg: String,
        left: String?,
        right: String?,
        maps: Set<String> = emptySet(),
        music: Set<String> = emptySet(),
    ): SplitSide? = when (pkg) {
        left -> SplitSide.LEFT
        right -> SplitSide.RIGHT
        in MAP_APPS, in maps -> SplitSide.LEFT
        in RIGHT_APPS, in music -> SplitSide.RIGHT
        else -> null
    }

    /**
     * Cómo abrir la app del lado [side] en Android [sdk]. [already] = la pantalla ya estaba dividida
     * (cada una se manda directo a su mitad); si no, la izquierda se abre normal y el servicio la
     * acopla antes de abrir la derecha. [canPickStack] = en Android 7–8 se puede indicar la pila.
     *
     * Android 7–8 abre "al lado" en la mitad contraria a la que tiene el foco. Recién dividida, el
     * foco lo tienen las recientes (a la derecha): "al lado" mandaría la derecha encima de la
     * izquierda. Por eso, sin poder indicar la pila, la derecha va "al lado" solo si la izquierda
     * tiene el foco (ya estaba dividida y se acaba de abrir). Indicando la pila no hay duda, y "al
     * lado" ayuda: mueve la app de mitad si estaba en la otra. Desde Android 9 una app nueva cae sola
     * en la mitad libre y "al lado" la mandaría a la izquierda.
     */
    fun launch(side: SplitSide, sdk: Int, already: Boolean, canPickStack: Boolean): SplitLaunch {
        val pick = canPickStack && sdk < PIE
        return when (side) {
            // Desde el inicio (que tiene el foco), "al lado" es la mitad acoplada.
            SplitSide.LEFT -> if (already) SplitLaunch(adjacent = true, stack = if (pick) STACK_DOCKED else null) else SplitLaunch(adjacent = false, stack = null)
            SplitSide.RIGHT -> when {
                sdk >= PIE -> SplitLaunch(adjacent = false, stack = null)
                pick -> SplitLaunch(adjacent = true, stack = STACK_FULLSCREEN)
                else -> SplitLaunch(adjacent = already, stack = null)
            }
        }
    }
}
