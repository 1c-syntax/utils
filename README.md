# utils

[![Java CI](https://github.com/1c-syntax/utils/actions/workflows/check.yml/badge.svg)](https://github.com/1c-syntax/utils/actions/workflows/check.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.1c-syntax/utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.1c-syntax/utils)
[![GitHub release](https://img.shields.io/github/v/release/1c-syntax/utils?include_prereleases&sort=semver)](https://github.com/1c-syntax/utils/releases)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License: LGPL-3.0-or-later](https://img.shields.io/badge/license-LGPL--3.0--or--later-blue.svg)](LICENSE.md)

Общие утилиты для java-проектов команды [1c-syntax](https://github.com/1c-syntax) —
небольшие независимые помощники, переиспользуемые в
[BSL Language Server](https://github.com/1c-syntax/bsl-language-server) и смежных проектах.

Библиотека сознательно держит минимальную замкнутость зависимостей и помечена
[JSpecify](https://jspecify.dev/) `@NullMarked` (типы считаются non-null, если не аннотированы
`@Nullable`).

## Требования

- Java 21 или новее (сборка таргетится на Java 21; CI прогоняется на Java 21 и 25).

## Подключение

Артефакт публикуется в Maven Central под координатами `io.github.1c-syntax:utils`.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.1c-syntax:utils:VERSION")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.1c-syntax</groupId>
    <artifactId>utils</artifactId>
    <version>VERSION</version>
</dependency>
```

Актуальную версию смотрите в
[релизах](https://github.com/1c-syntax/utils/releases) или на
[Maven Central](https://central.sonatype.com/artifact/io.github.1c-syntax/utils).

## Что внутри

### Пакет `com.github._1c_syntax.utils`

| Класс | Назначение |
| --- | --- |
| `Absolute` | Приведение файловых путей и URI к каноническому абсолютному виду — стабильный ключ для одного и того же файла независимо от формы записи. |
| `Lazy<T>` | Потокобезопасное хранилище значения с ленивым однократным вычислением (double-checked locking) и возможностью сброса кэша. |
| `GenericInterner<T>` | Потокобезопасный интернер значений: один канонический экземпляр на класс эквивалентности — экономия памяти и сравнение по ссылке. |
| `StringInterner` | Интернер строк на базе `GenericInterner`, дополнительно нормализующий `null` в пустую строку. |
| `CaseInsensitivePattern` | Компиляция регулярных выражений без учёта регистра с поддержкой Unicode (важно для кириллицы кода 1С). |

### Пакет `com.github._1c_syntax.utils.downloader`

Загрузчик исполняемого файла [BSL Language Server](https://github.com/1c-syntax/bsl-language-server)
из GitHub-релизов (для встраивания в IDE-плагины и другие клиенты).

| Класс | Назначение |
| --- | --- |
| `BslLanguageServerDownloader` | Скачивает подходящий под ОС ассет последнего релиза, распаковывает его и отдаёт путь к бинарю; кэширует установленную версию и опрашивает GitHub не чаще заданного интервала. |
| `GitHubReleaseClient` | Находит последний релиз выбранного канала через GitHub REST API (на `java.net.http.HttpClient` + gson, без клиентских библиотек GitHub). |
| `BslLanguageServerReleaseChannel` | Канал релизов: `STABLE` или `PRERELEASE`. |
| `DownloadProgressListener` | Слушатель прогресса скачивания ассета. |

## Примеры использования

Каноникализация пути и URI:

```java
Path path = Absolute.path("./src/../build/out.txt"); // абсолютный канонический путь
URI uri = Absolute.uri("file:///C:/Program%20Files/app"); // нормализованный file:-URI
```

Ленивое вычисление:

```java
Lazy<List<String>> lines = new Lazy<>(() -> readAllLines(file));
List<String> value = lines.getOrCompute(); // вычислится один раз и закэшируется
lines.clear();                             // сбросить кэш
```

Интернирование:

```java
StringInterner interner = new StringInterner();
String canonical = interner.intern(name); // равные строки делят один экземпляр
```

Регистронезависимый поиск с учётом Unicode:

```java
Pattern pattern = CaseInsensitivePattern.compile("Процедура");
boolean matches = pattern.matcher("процедура").matches(); // true
```

Скачивание BSL Language Server:

```java
var client = new GitHubReleaseClient(githubToken); // токен может быть null
var downloader = new BslLanguageServerDownloader(installDir, client, HttpClient.newHttpClient());
Path binary = downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.STABLE);
```

## Сборка

Используйте wrapper Gradle:

```bash
./gradlew build   # сборка, проверки и тесты
./gradlew check   # то, что гоняет CI (test + jacoco + javadoc + проверка лицензий)
```

Заголовки лицензии проверяются плагином license; при необходимости их можно проставить командой
`./gradlew licenseFormat`.

## Лицензия

[GNU LGPL 3.0 или новее](LICENSE.md) (`SPDX-License-Identifier: LGPL-3.0-or-later`).
