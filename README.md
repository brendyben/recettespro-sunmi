# RecettesPro - App Sunmi V2s

App Android wrapper qui charge `https://kapdatalabs.com` dans une WebView et expose une interface JavaScript pour imprimer directement sur l'imprimante intégrée du Sunmi V2s, sans passer par le dialogue d'impression Android.

## 🚀 Étapes pour obtenir l'APK (sans Android Studio)

### 1. Créer un compte GitHub (gratuit, 2 min)
- Va sur https://github.com → Sign up

### 2. Créer un nouveau repo
- Clique sur "+" en haut à droite → "New repository"
- Nom : `recettespro-sunmi`
- Coche "Public" (ou Private, peu importe)
- Clique "Create repository"

### 3. Uploader les fichiers
- Sur la page du repo, clique "uploading an existing file"
- Glisse TOUS les fichiers et dossiers de ce projet (en gardant la structure)
- Commit en bas de la page

### 4. Attendre le build (3 min)
- Va dans l'onglet **Actions** du repo
- Tu verras le workflow "Build APK" en cours (point jaune)
- Quand il devient vert ✅, clique dessus
- En bas de la page, télécharge `RecettesPro-debug-apk`

### 5. Installer sur le V2s
- Décompresse le zip → tu obtiens `app-debug.apk`
- Transfère-le sur le V2s (USB, email, Drive, peu importe)
- Sur le V2s : Paramètres → Sécurité → autoriser "Sources inconnues"
- Ouvre l'APK depuis le gestionnaire de fichiers → Installer

### 6. Adapter ton site

Dans le code de ta page de ticket sur kapdatalabs.com, remplace ton bouton imprimer par :

```javascript
function imprimerTicket() {
  // Détecte si on est dans l'app Sunmi
  if (window.SunmiPrint && window.SunmiPrint.isAvailable()) {
    const ticket = {
      titre: "RECETTESPRO - VILLE DE KINSHASA",
      numero: "20260510-NGALIEMA-024",
      agent: "Benito",
      commune: "Ngaliema",
      marche: "Ngaliema-A",
      date: "2026-05-10",
      heure: "17:11:36",
      montant: "2 000 FC",
      libelle: "Montant collecte"
    };
    
    const result = window.SunmiPrint.printTicket(JSON.stringify(ticket));
    
    if (result === "OK") {
      window.SunmiPrint.toast("Ticket imprime !");
    } else {
      alert("Erreur impression : " + result);
    }
  } else {
    // Fallback : navigateur classique
    window.print();
  }
}
```

## 🖨️ API JavaScript disponible

Une fois l'app installée, ton site peut appeler :

| Fonction | Description |
|----------|-------------|
| `window.SunmiPrint.isAvailable()` | `true` si l'imprimante est prête |
| `window.SunmiPrint.printTicket(json)` | Imprime un ticket formaté (champs : titre, numero, agent, commune, marche, date, heure, montant, libelle) |
| `window.SunmiPrint.printRaw(text)` | Imprime du texte brut |
| `window.SunmiPrint.toast(msg)` | Affiche une notif Android |

## 📐 Format imprimante

- **Largeur** : 58 mm (32 caractères par ligne)
- **Avance papier** : automatique en fin de ticket
- **Coupe** : pas de massicot sur V2s, la coupe est manuelle

## 🛠️ Personnaliser le rendu

Tu peux modifier `app/src/main/java/com/kapdatalabs/recettespro/MainActivity.kt` :
- Ajuster les tailles de police (`setFontSize` ou `printTextWithFont`)
- Ajouter logo (méthode `printBitmap`)
- Ajouter QR code (méthode `printQRCode`)
- Ajouter codes-barres (méthode `printBarCode`)

Après modification : push sur GitHub, le build se relance automatiquement.

## ❓ Problèmes courants

**L'app crashe au lancement** : vérifier que c'est bien installé sur un Sunmi (pas un autre Android).

**`isAvailable()` retourne false** : redémarrer le V2s, puis l'app.

**Texte coupé à droite** : ton libellé fait > 32 caractères, raccourcis-le ou découpe sur 2 lignes.
