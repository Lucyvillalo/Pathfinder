# Pathfinder

Pathfinder es una aplicación Android local para organizar hábitos y productividad. La interfaz usa Compose y Material 3; el perfil, las misiones completadas y las recompensas se guardan con Room. DataStore conserva el nombre, el onboarding, el tiempo diario, los objetivos y el tema.

## Abrir y ejecutar

1. Abre la carpeta del proyecto en Android Studio.
2. Usa JDK 17 y deja que Gradle descargue el wrapper 8.13 y las dependencias.
3. Ejecuta la configuración `app` en un emulador o dispositivo Android 7.0 (API 24) o posterior.

También puedes compilar desde PowerShell:

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo con ADB:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para distribuir una versión comercial, configura una clave de firma privada y genera una variante release firmada en Android Studio (`Build > Generate Signed Bundle / APK`). Conserva la clave fuera del repositorio y úsala en las actualizaciones para mantener la identidad de firma. La venta por Hotmart no activa automáticamente funciones dentro de esta app.

## Qué está implementado

- Onboarding local con nombre, objetivos, nivel y disponibilidad diaria.
- Inicio, catálogo de rutas, estadísticas y perfil con navegación inferior.
- Treinta misiones originales en español con pasos verificables, XP y recompensa única dentro de una transacción Room.
- XP persistida; niveles de 100 XP; racha calculada usando fechas locales de las recompensas registradas.
- Misión diaria elegida según objetivos y misiones pendientes.
- Seis lecciones locales con contenido, ejercicio práctico, cuestionario autocorregible y mejor resultado persistido.
- Lecturas locales con búsqueda, favoritos e historial persistidos.
- Estadísticas de actividad registradas para siete y treinta días.
- Desafío de misiones e insignias que se desbloquean según actividad persistida.
- Tema claro, oscuro y automático del sistema.
- Edición local del nombre y tiempo diario disponible desde Perfil.
- Pantalla de cuenta con inicio de sesión Google real mediante Credential Manager y Firebase Auth cuando el proyecto Firebase esté registrado y configurado.
- Ilustraciones vectoriales locales y adaptables para bienvenida, inicio y misiones, sin depender de imágenes remotas.
- Alternativas de productividad con referencias consultables y búsqueda web que abre el navegador del usuario.
- Confirmación para borrar perfil, progreso y experiencia.
- Migración Room 1→2 para conservar bases creadas por la versión anterior.
- Pruebas unitarias de niveles, rachas, selección de misión y puntuación de cuestionario.

## Alcance pendiente

Esta versión sigue siendo un MVP local y no completa todos los 50 flujos solicitados. La app Android ya está registrada en Firebase con su SHA-1 debug, pero la consola no entregó el archivo `app/google-services.json`; por eso Google Sign-In permanece deshabilitado hasta añadir el archivo correcto. No incluye acceso por correo/contraseña ni recuperación, recordatorios programados, sincronización, backup remoto ni pago/activación Hotmart. El esquema Room conserva perfil, progreso de misiones, XP, avance de lecciones, favoritos e historial, pero aún no cubre todas las entidades pedidas. Las insignias se derivan del avance local actual. La disponibilidad diaria se captura y se puede editar, pero no cuenta con planificación/calendario de notificaciones. Varias categorías están marcadas como próximas porque no tienen lecciones publicadas. Falta completar mapa/detalle de habilidades, historial detallado, edición de objetivos, preferencias de privacidad/soporte y pruebas instrumentadas de navegación, persistencia y reinicio. El onboarding incluye bienvenida e introducción; se puede usar la app sin cuenta.

La búsqueda web se abre en el navegador y requiere conexión. Las ideas de productividad y sus referencias están disponibles localmente. `google-services.json` contiene identificadores de configuración del proyecto y no debe reemplazarse por valores inventados. Para Google Sign-In, añade el archivo descargado de Firebase y registra también la huella SHA-1 del certificado de firma; para producción registra la huella de la clave release.
# Pathfinder
