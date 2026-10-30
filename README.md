# ParqTime

App Android para no pasarte del tiempo en los parquímetros ecoParq (Polanco, CDMX).
Escribes cuánto pagaste y te avisa a los **25, 10 y 5 minutos** y **cuando se acaba**, con sirena al
máximo (aunque el teléfono esté en silencio), vibración, linterna en estrobo y pantalla roja sobre la
pantalla bloqueada. Funciona sin internet.

Desarrollada por Tecnobasics.

## Descargar

Última versión: **https://github.com/beto69chico-debug/parktime/releases/latest/download/ParqTime.apk**

1. Abre el enlace en el teléfono y descarga `ParqTime.apk`.
2. Ábrelo. Android pedirá permitir "instalar apps de origen desconocido" para el navegador o WhatsApp: acéptalo.
3. Abre ParqTime y toca **Activar** en cada aviso amarillo hasta que desaparezca.
4. En **Ajustes → Probar alarma** confirma que suena, vibra y parpadea.

Las actualizaciones se instalan encima de la anterior sin perder nada.

## Cómo calcula el tiempo

- Tarifa por defecto: $3.40 por 15 minutos (editable en Ajustes).
- Horario por defecto: cobro desde las 08:00; cierra a las 20:00 lunes y martes, y a la 01:00 de miércoles a sábado. Domingo gratis. Todo editable.
- Si el tiempo pagado pasa la hora de cierre, lo que sobra se pausa y sigue al siguiente inicio de cobro.
- Si agregas tiempo antes de que venza, se suma; si ya venció, empieza un conteo nuevo.
- Los días festivos no se descuentan solos.

## Compilar

Cada push a `main` compila en GitHub Actions, corre las pruebas de la lógica de tiempo y publica el APK
en Releases. Para compilar localmente: abre el proyecto en Android Studio y ejecuta `assembleRelease`.
