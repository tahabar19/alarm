package com.lumina.reveil.data

/**
 * Ambiances lumineuses (le dégradé de "lever de soleil" affiché à l'écran).
 * startColor = couleur en début de montée (sombre), endColor = couleur pleine luminosité.
 */
enum class LightScene(
    val label: String,
    val startColor: Long,
    val endColor: Long
) {
    SOLEIL("Soleil", 0xFF3A1200, 0xFFFFD27A),
    AUBE("Aube rosée", 0xFF2A0A1E, 0xFFFFB6C1),
    OCEAN("Océan", 0xFF001018, 0xFF7EC8E3),
    FORET("Forêt", 0xFF04160A, 0xFFB6E3A0),
    BOUGIE("Bougie", 0xFF1A0A00, 0xFFFFC46B),
    LUNE("Clair de lune", 0xFF03060F, 0xFFBFD4FF);
}

/**
 * Sons d'ambiance (phase pré-réveil, doux, montent doucement).
 */
enum class AmbianceSound(val label: String) {
    PLUIE("Pluie douce"),
    OCEAN("Vagues de l'océan"),
    RUISSEAU("Ruisseau"),
    FORET("Forêt à l'aube (oiseaux)"),
    VENT("Vent léger"),
    AUCUN("Aucun");
}

/**
 * Mélodies de réveil (phase alarme, montent en volume pour réveiller).
 * Toutes sont synthétisées dans l'app (aucun fichier requis).
 */
enum class WakeMelody(val label: String) {
    OISEAUX("Chants d'oiseaux (aube)"),
    HARPE("Harpe ascendante"),
    KALIMBA("Kalimba paisible"),
    MARIMBA("Marimba pentatonique"),
    BOL_TIBETAIN("Bol tibétain"),
    PIANO("Piano cristallin"),
    CARILLON("Carillon de vent");
}
