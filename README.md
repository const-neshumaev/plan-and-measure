# Plan & Measure

Android-приложение для лазерного дальномера **Instrumax Sniper 80 Plus**. Цель — принимать измерения по Bluetooth, привязывать их к фотографиям и схемам помещения, сохранять проекты.

Публичного описания протокола Instrumax не существует: он восстановлен экспериментально. Методика исследования, подтверждённые факты и журнал экспериментов собраны в `docs/protocol-research/`.

Измерения можно вводить вручную: дальномер не обязателен и лишь ускоряет ввод. Списки измерений с комментариями сохраняются локально и доступны для открытия при следующем запуске.

В разделе «Планы» контур помещения рисуется пальцем прямо на экране: линии выравниваются по сетке и углам кратным 45°, а узлы цепляются друг к другу. Готовый чертёж можно править: в режиме «Правка» узел перетаскивается со снапом к сетке, линию можно выделить и удалить, последнее действие отменяется кнопкой «Отменить». Планы сохраняются локально и открываются снова вместе с чертежом.

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
  MainActivity.kt           точка входа, edge-to-edge
  LabViewModel.kt           состояние BLE-сессии и лабораторные сценарии
  ble/
    BleScanner.kt           сканирование
    BleGattClient.kt        GATT-клиент с очередью операций и потоком кадров
    BleModels.kt            модели сканирования и GATT
    BleUuids.kt             словарь UUID
    BlePermissions.kt       разрешения Android 12+
    BleUtils.kt             мелкие помощники
    RangefinderDevice.kt    эвристика «это дальномер» для списка устройств
    LastDeviceStorage.kt    память о последнем подключённом устройстве
  logging/
    PacketLog.kt            журнал событий и пакетов
    LogExporter.kt          CSV/TXT
    Hex.kt                  hex-кодирование и разбор
  protocol/
    FrameCodec.kt           разбор подтверждённых кадров
  measurements/
    Measurement.kt          измерение и источник (ручной/дальномер)
    MeasurementList.kt      документ списка и его сводка
    MeasurementListJson.kt  JSON-кодек документа
    MeasurementListRepository.kt      контракт хранения
    JsonMeasurementListRepository.kt  файловое хранилище с атомарной записью
    MeasurementInput.kt     валидация ручного ввода
    MeasurementsViewModel.kt          состояние списков и записи
  settings/
    AppSettings.kt          модель настроек (тема, язык)
    SettingsStorage.kt      сохранение в SharedPreferences
    SettingsViewModel.kt    состояние настроек и запись
    LanguageResolution.kt   выбор локали и fallback на английский
  plans/
    PlanDocument.kt         план: узлы, грани, контуры
    PlanGeometry.kt         привязка к сетке и углам
    PlanEditing.kt          применение жестов к плану
    PlanJson.kt             JSON-кодек плана
    PlanRepository.kt       контракт хранения
    JsonPlanRepository.kt   файловое хранилище
    PlansViewModel.kt       состояние планов и открытого чертежа
  storage/
    JsonDocumentStore.kt    атомарная запись JSON-документов
  ui/
    AppRoot.kt              навигация: меню, разделы MVP, подключение, BLE Lab
    home/HomeScreen.kt      главное меню и статус подключения
    connect/ConnectScreen.kt  поиск и подключение дальномера
    measurements/
      MeasurementsFlow.kt  списки и редактор списка
      MeasurementListsScreen.kt  сохранённые списки
      MeasurementListScreen.kt   редактор списка и статус записи
      MeasurementEditorSheet.kt  ручной ввод и правка
      MeasurementFormat.kt единицы и форматирование времени
    plans/
      PlansFlow.kt         список планов и редактор
      PlanListsScreen.kt   сохранённые планы с превью
      PlanEditorScreen.kt  чертёж и действия с планом
      PlanDrawing.kt       канвас, сетка и превью
    sections/               экраны-заглушки разделов MVP
    lab/
      LabFlow.kt            общий app bar и back-навигация лаборатории
      ScanScreen.kt         список устройств
      DeviceScreen.kt       GATT-дерево, команды, журнал
    components/             общие компоненты
    theme/                  палитра, типографика, формы
app/src/main/res/
  values/strings.xml        английские строки (по умолчанию)
  values-ru/strings.xml     русские строки
```

## Данные

Списки измерений хранятся только на устройстве, в `filesDir/measurements/<uuid>.json`. Сервера и аккаунтов нет. Каждое изменение сразу записывается атомарно, предыдущая версия документа остаётся в `.bak`. Подробности и причины — в `docs/adr/0002-local-json-documents-for-measurement-lists.md`.

Планы хранятся так же, в `filesDir/plans/<uuid>.json`. Координаты чертежа — абстрактные единицы сетки, а не пиксели, поэтому план одинаково выглядит на разных устройствах и в разной ориентации. Формат и причины — в `docs/adr/0004-store-plans-as-local-json-documents.md`.

`allowBackup` выключен: при переустановке данные пропадут. Экспорт и резервное копирование появятся отдельной задачей.

Дальномер не обязателен: значения вводятся вручную в любом списке. Кадры с прибора записываются только пока открыт редактор списка, приложение на переднем плане и запись не поставлена на паузу — см. `docs/adr/0003-measurements-are-independent-of-ble-session.md`.

## Документация

- `docs/protocol-research/research-plan.md` — методика снятия протокола, эксперименты E0–E10.
- `docs/protocol-research/protocol.md` — спецификация протокола (заполняется по мере исследования).
- `docs/protocol-research/experiments.md` — журнал проведённых экспериментов.
- `docs/adr/` — архитектурные решения и причины их принятия; правила ведения — в `docs/adr/README.md`.
- `AGENTS.md` — правила для ИИ-агента, который продолжит разработку.

## Дальнейшие шаги

1. Закрыть оставшиеся вопросы протокола (дюймы/миллиметры, кадры ошибок, поведение на длинных сессиях).
2. Привязка размеров к граням плана: выбор грани, запись с дальномера и ручной ввод.
3. Зум и панорама чертежа, пересмотр масштаба плана.
4. Раздел «Проекты»: помещения, привязка списков измерений и планов к проекту.
5. Привязка измерений к фотографиям.
6. Экспорт и резервное копирование, PDF/SVG/DXF.

## Лицензия

Код и документация распространяются по лицензии MIT — см. `LICENSE`. Проект можно свободно использовать, изменять и распространять, при сохранении уведомления об авторских правах.

`docs/protocol-research/protocol.md` содержит независимое описание протокола и не включает код или материалы производителя; см. дисклеймер в начале файла.
