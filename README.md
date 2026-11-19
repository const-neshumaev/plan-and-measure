# Plan & Measure

Android-приложение для лазерного дальномера **Instrumax Sniper 80 Plus**. Цель — принимать измерения по Bluetooth, привязывать их к фотографиям и схемам помещения, сохранять проекты.

Публичного описания протокола Instrumax не существует: он восстановлен экспериментально. Методика исследования, подтверждённые факты и журнал экспериментов собраны в `docs/protocol-research/`.

Измерения можно вводить вручную: дальномер не обязателен и лишь ускоряет ввод.

## Стек

- Kotlin, Jetpack Compose, Material 3, MVVM.
- `minSdk 24`, `compileSdk 34`, `targetSdk 34`; проверяется на Xiaomi 11T (Android 14).
- Gradle 8.7, AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.06.00, kotlinx.serialization 1.7.3.

## Требования

- Android Studio (актуальная версия) — она принесёт JDK 17+ и Android SDK.
- Телефон с BLE и Android 7.0+ (проверяем на Xiaomi 11T, Android 14).
- Дальномер с включённым Bluetooth-режимом.
- Для Gradle нужен JDK 17–21. Встроенный в новые версии Android Studio JBR 25 не подходит для Gradle 8.7: в Settings → Build, Execution, Deployment → Build Tools → Gradle выберите Gradle JDK 17/19/21 (можно скачать прямо там).

Отдельно ставить Gradle не нужно: версия зафиксирована в `gradle/wrapper/gradle-wrapper.properties`, wrapper уже в проекте.

### SDK

Проект ожидает Android SDK с установленными `Android SDK Platform 34` и `Android SDK Build-Tools 34.0.0`. Если при открытии Android Studio просит путь к SDK, укажите корень SDK (папку, в которой лежат `platforms`, `build-tools`, `platform-tools`). Затем в SDK Manager установите платформу 34 и Build-Tools 34.0.0 и примите лицензии.

## Сборка

1. Открыть папку проекта в Android Studio.
2. Дождаться Sync: Studio сама скачает Gradle 8.7 и зависимости, при необходимости предложит установить Android SDK Platform 34 и Build Tools.
3. Запустить на телефоне: `Run 'app'`.

Через командную строку (нужны JDK 17–21, Android SDK и env `JAVA_HOME`, указывающий на этот JDK):

```
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/`.

## Структура проекта

```
app/src/main/java/com/planmeasure/
  MainActivity.kt      точка входа; тема, локаль, edge-to-edge
  LabViewModel.kt      владелец BLE-сессии и потока кадров
  ble/                 сканирование, GATT-клиент, разрешения, UUID
  protocol/            разбор подтверждённых кадров дальномера
  measurements/        списки измерений: модель, JSON, репозиторий, ViewModel
  plans/               планы: модель, геометрия, правки, масштаб размеров, JSON,
                       репозиторий, ViewModel
  units/               единицы длины, канонические миллиметры, валидация ввода
  settings/            тема, язык, последняя выбранная единица
  storage/             атомарная запись JSON-документов
  logging/             журнал протокола и экспорт
  ui/                  экраны Compose: меню, подключение, измерения, планы,
                       BLE Lab, заглушки разделов, общие компоненты, тема
app/src/main/res/
  values/strings.xml      английские строки (по умолчанию)
  values-ru/strings.xml   русские строки
```

## Документация

- `docs/overview.md` — назначение проекта, архитектура и точки входа.
- `docs/domain-model.md` — доменная модель и форматы хранения.
- `docs/roadmap.md` — будущие планы и отложенные идеи.
- `docs/adr/` — архитектурные решения и причины их принятия.
- `docs/protocol-research/` — методика, спецификация и журнал экспериментов.
- `AGENTS.md` — правила работы с репозиторием для ИИ-агента.

## Лицензия

Код и документация распространяются по лицензии MIT — см. `LICENSE`. Проект можно свободно использовать, изменять и распространять, при сохранении уведомления об авторских правах.

`docs/protocol-research/protocol.md` содержит независимое описание протокола и не включает код или материалы производителя; см. дисклеймер в начале файла.
