# Lumina Réveil ☀️

Réveil « lever de soleil » pour Android : l'écran s'allume avec une couleur au choix
et monte progressivement en luminosité pendant l'intervalle avant l'heure, une ambiance
sonore douce (pluie, vagues…) monte en même temps, puis un **chœur d'oiseaux à l'aube**
te réveille en montant doucement en volume.

Tout le son est **synthétisé dans l'app** : aucun fichier .mp3 à ajouter.

---

## 📲 Obtenir l'APK SANS installer Android Studio (le plus simple)

Je ne peux pas compiler l'APK à distance, mais ce projet contient un workflow qui la
compile automatiquement dans le cloud via **GitHub Actions**. Tu récupères juste le fichier.

1. Crée un compte sur https://github.com (gratuit).
2. Crée un nouveau dépôt (bouton **New repository**), par ex. `lumina-reveil`.
3. Dans le dépôt : **Add file → Upload files**, glisse **tout le contenu** de ce dossier
   (garde la structure), puis **Commit**.
   - ⚠️ Vérifie que le dossier `.github/` est bien envoyé (il peut être caché).
4. Va dans l'onglet **Actions** → le build « Build APK » démarre tout seul
   (sinon clique **Run workflow**).
5. Quand le rond devient vert (≈ 3–5 min), ouvre le build → section **Artifacts** →
   télécharge **lumina-reveil-apk**. Dedans : `app-debug.apk`.
6. Transfère l'APK sur ton téléphone et installe-le
   (autorise « sources inconnues » si demandé).

## 💻 Alternative : compiler avec Android Studio

1. Installe Android Studio.
2. **Open** → sélectionne ce dossier. Laisse-le télécharger le SDK/Gradle.
3. Menu **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
4. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## ⚙️ Réglages importants sur le téléphone (pour un réveil fiable)

À la 1ʳᵉ ouverture, l'app demande :
- **Notifications** → Autoriser.
- **Alarmes et rappels** (alarmes exactes) → Autoriser.

Puis, dans les paramètres Android, pour éviter que le système coupe l'app pendant la nuit :
- **Batterie → Optimisation batterie → Lumina Réveil → Ne pas optimiser / Sans restriction**.
- Sur Xiaomi/Huawei/Samsung/Oppo : autorise le **démarrage automatique** (Autostart) et le
  fonctionnement **en arrière-plan**.

Laisse le volume média/alarme à un niveau audible ; l'app monte le son toute seule
depuis très bas.

---

## 🐦 Sons disponibles

**Réveil (par défaut) : Chants d'oiseaux (aube)** — chœur d'oiseaux synthétisé (plusieurs
espèces qui se superposent et se densifient en montant).

Autres mélodies de réveil : Harpe ascendante, Kalimba, Marimba pentatonique, Bol tibétain,
Piano cristallin, Carillon de vent.

Ambiances (phase montante avant le réveil) : Pluie douce, Vagues de l'océan, Ruisseau,
Forêt à l'aube, Vent léger, Aucun.

Ambiances lumineuses : Soleil, Aube rosée, Océan, Forêt, Bougie, Clair de lune.

---

## 🧩 Structure

- `data/` — modèle de réveil, stockage, listes de sons/couleurs
- `alarm/` — planification (AlarmManager), réception, service de premier plan (montée lumière+son), redémarrage
- `audio/` — moteur audio synthétisé (pluie, vagues, oiseaux, mélodies)
- `MainActivity.kt` — écran principal + éditeur de réveil (avec aperçu audio)
- `AlarmActivity.kt` — écran plein « lever de soleil » (par-dessus l'écran verrouillé)
