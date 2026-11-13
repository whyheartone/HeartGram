# HeartGram для Android

Неофициальный клиент Telegram для Android на основе [exteraless](https://github.com/exteraless/exteraless).
Сверху добавляем свои функции и то, что хорошо работает в AyuGram и DiveGram.

Подробнее о том, чему мы обязаны, — в разделе [Благодарности](#благодарности).

> Это неофициальный клиент, он не связан с Telegram FZ-LLC. Ты сам отвечаешь за свои данные и аккаунт.

## Статус

Клиент собирается в GitHub Actions, обновляется по воздуху (OTA) из релизов этого репозитория.
Оригинальный README exteraless: [README.exteraless.md](README.exteraless.md). Дорожная карта: [docs/ROADMAP.md](docs/ROADMAP.md).

Пакет приложения `org.telegram.messenger`, как у оригинального Telegram: клиент ставится поверх него (и поверх старой
версии HeartGram, если APK подписан тем же ключом).

## Сборка

Основная сборка идёт в GitHub Actions:

- `build.yml` — на каждый пуш и pull request: проверка workflow, быстрая компиляция Java и Kotlin, debug APK (arm64-v8a;
  `universal` по запросу). APK скачивается из артефактов запуска.
- `release.yml` — релиз для OTA: запускается вручную (Actions → Release) или тегом `v*`. Собирает `arm64-v8a` и `universal`,
  создаёт GitHub Release с APK. Клиент сам ищет там обновления (Настройки → Другое → Обновления).

Нужно локально (если собирать не в CI): JDK 21, Android SDK (platform 37, build-tools 36.0.0), NDK 27.2.12479018,
Python 3.11 (для Chaquopy), `nasm`. Подмодули: `git clone --recurse-submodules --shallow-submodules`.

### Секреты репозитория (Settings → Secrets and variables → Actions)

В репозиторий они не попадают. Без них клиент собирается, но без значков HeartGram и с отладочной подписью.

| Секрет | Что это |
|--------|---------|
| `HG_LIST_URL` | адрес списка значков HeartGram |
| `HG_SEAL_KEY` | ключ закрытой части списка (64 hex-символа, AES-256-GCM) |
| `RELEASE_KEYSTORE` | ключ подписи, файл `.jks` в base64. **Нужен для обновлений поверх прошлой версии**: случайный отладочный ключ у каждой сборки свой |
| `LOCAL_PROPERTIES` | base64 от файла со строками `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`, а также (необязательно) `TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH` |


## Благодарности

HeartGram стоит на открытом коде этих проектов. Спасибо авторам:

- [exteraless](https://github.com/exteraless/exteraless) — основа клиента
- [NagramX](https://github.com/risin42/NagramX)
- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Dr4iv3rNope / NotSoAndroidAyuGram](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram)
- [Inugram](https://github.com/teidesu/inugram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [Nekogram](https://github.com/Nekogram/Nekogram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)
- [DiveGram](https://github.com/Snegovikio/DiveGram-Android)
- [tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy)

**Дизайн.** Иконки и оформление, унаследованные от exteraGram, созданы его дизайнером
[@the8055u](https://t.me/the8055u) и студией [@BlueprintDsgn](https://t.me/BlueprintDsgn).
Права на эти материалы принадлежат авторам.
