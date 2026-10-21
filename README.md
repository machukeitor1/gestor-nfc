# Gestor NFC

App Android (Kotlin + Jetpack Compose) para leer, escribir y proteger tags NFC.
Desarrollado por Cristian Machuca, desde Chillán, Chile — [github.com/machukeitor1](https://github.com/machukeitor1).

## Funciones (v0.2)

- **Leer**: chip exacto (NTAG213/215/216, Ultralight EV1, MIFARE Classic…), UID, fabricante,
  firma de originalidad NXP (detecta clones), contenido NDEF, estado de bloqueo / contraseña
  y volcado de memoria. Copiar el contenido a otro tag o guardarlo como plantilla.
- **Escribir**: URL, redes sociales (15), Wi‑Fi, contacto (vCard), texto, teléfono, SMS, email,
  ubicación (Google Maps o geo:) y abrir app (AAR). Muestra cuánto ocupa y si cabe en cada NTAG.
- **Plantillas**: guardar cualquier contenido y escribirlo una vez o **en serie**
  (muchos tags seguidos, opcionalmente protegiendo cada uno con contraseña).
- **Proteger**: contraseña contra escritura (poner / cambiar / quitar) en NTAG21x y Ultralight EV1,
  y **bloqueo permanente** con doble confirmación. Al escribir un tag protegido la app pide la
  contraseña y puede recordarla por tag (UID).
- **Borrar / formatear**: deja el tag vacío (formatea tags nuevos).
- **Historial**: lecturas, escrituras y protecciones; nombre y nota por tag, búsqueda, filtros,
  reescribir en otro tag y guardar como plantilla.
- **Acerca de** y splash de inicio.

## Abrir

1. Android Studio → *Open* → esta carpeta.
2. Esperar el *Gradle sync*.
3. Conectar un teléfono con NFC (con depuración USB) y ▶ *Run*.

> El emulador no tiene NFC: hay que probar en un teléfono real.

## Estructura

```
app/src/main/java/cl/machukeitor/gestornfc/
├─ MainActivity.kt        modo lector NFC (enableReaderMode), splash
├─ NfcViewModel.kt        estado, trabajos sobre el tag (escribir, proteger, serie)
├─ data/Stores.kt         historial y plantillas (JSON en almacenamiento privado)
├─ nfc/
│  ├─ TagInspector.kt     lectura tipo "TagInfo" (GET_VERSION, READ_SIG, READ…)
│  ├─ Ntag.kt             contraseña (PWD_AUTH) y escritura en tags protegidos
│  ├─ NxpSignature.kt     verificación ECDSA secp128r1 de la firma NXP
│  ├─ NdefParser.kt       interpreta registros NDEF
│  ├─ WritePayload.kt     tipos de contenido → NdefMessage
│  ├─ WifiConfig.kt       registro Wi‑Fi (WSC)
│  ├─ SocialNetwork.kt    redes sociales y armado de URLs
│  ├─ PasswordStore.kt    contraseñas recordadas por UID
│  └─ TagWriter.kt        escritura NDEF y bloqueo permanente
└─ ui/                    pantallas Compose (Leer, Escribir, Historial, Acerca de…)
```
