/*
 * This file is a part of 1c-syntax utils.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * 1c-syntax utils is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * 1c-syntax utils is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with 1c-syntax utils.
 */
package com.github._1c_syntax.utils.downloader;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Клиент GitHub-релизов BSL Language Server: находит последний релиз канала в репозитории
 * {@value #REPOSITORY} через GitHub REST API. Работает на {@link java.net.http.HttpClient}
 * и разбирает ответ регулярными выражениями по ссылкам на ассеты — без клиентских библиотек
 * GitHub и внешних JSON-библиотек, чтобы рантайм-замкнутость оставалась минимальной (важно
 * для встраивания в OSGi).
 *
 * <p>Полноценный JSON-разбор не нужен: и стабильный, и pre-release-канал запрашиваются так, чтобы
 * в ответе был ровно один релиз, а из него нужны лишь версия и ссылки на ассеты. И то, и другое
 * берётся из самих download-ссылок вида
 * {@code https://github.com/<repo>/releases/download/<tag>/<file>} — поэтому произвольное
 * содержимое поля {@code body} (релиз-ноуты) не влияет на результат.
 *
 * <p>Отдельная зависимость загрузчика — чтобы в тестах его можно было замокать и прогнать поток
 * скачивания без обращения к GitHub. Класс не {@code final} специально: так его мокает Mockito.
 */
public class GitHubReleaseClient {

  private static final String REPOSITORY = "1c-syntax/bsl-language-server";
  private static final String API_ROOT = "https://api.github.com";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  // Верхняя граница пагинации pre-release: каждая страница — один релиз (per_page=1), цикл
  // дочитывает хвост, только пока встречает draft'ы; ограничение защищает от бесконечного
  // опроса, если апстрим (зеркало/прокси/кэш) отдаёт draft-релиз бесконечно.
  private static final int MAX_RELEASES_PAGES = 10;
  // Сколько символов тела ответа включать в текст ошибки для диагностики (GitHub кладёт причину
  // в поле message; токен в теле не возвращается, так что утечки секрета нет).
  private static final int ERROR_BODY_LIMIT = 500;

  // Ссылка на ассет релиза: https://github.com/<repo>/releases/download/<tag>/<file>.
  // Группа 1 — тег (версия), группа 2 — имя ассета, всё совпадение — URL для скачивания. Только
  // browser_download_url имеет такой путь (у html_url — /releases/tag/, у API-ссылок другой хост),
  // поэтому лишнего не захватываем.
  private static final Pattern ASSET_URL = Pattern.compile(
    "https://github\\.com/" + Pattern.quote(REPOSITORY) + "/releases/download/([^/\"]+)/([^/\"]+)");
  // Флаг draft у релиза. В pre-release-канале страница содержит ровно один релиз, поэтому
  // сопоставлять флаг конкретному объекту в списке не нужно.
  private static final Pattern DRAFT = Pattern.compile("\"draft\"\\s*:\\s*true");
  // Есть ли в ответе вообще объект релиза — чтобы отличить его от пустого списка [] за последней
  // страницей пагинации.
  private static final Pattern HAS_RELEASE = Pattern.compile("\"tag_name\"\\s*:");

  private final @Nullable String token;
  private final HttpClient httpClient;

  /**
   * @param token GitHub OAuth-токен для обхода лимитов анонимного API; может быть {@code null}
   */
  public GitHubReleaseClient(@Nullable String token) {
    this(token, HttpClient.newBuilder()
      .connectTimeout(CONNECT_TIMEOUT)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build());
  }

  /**
   * @param token      GitHub OAuth-токен для обхода лимитов анонимного API; может быть {@code null}
   * @param httpClient клиент для запросов к GitHub API — например, с настроенным прокси
   */
  public GitHubReleaseClient(@Nullable String token, HttpClient httpClient) {
    this.token = token;
    this.httpClient = httpClient;
  }

  /**
   * Возвращает последний релиз выбранного канала.
   *
   * @param channel канал релизов (стабильный / pre-release)
   * @return версия релиза и ссылки на его ассеты
   * @throws IOException если релизы недоступны или подходящего релиза нет
   */
  public Release latestRelease(BslLanguageServerReleaseChannel channel) throws IOException {
    var release = channel == BslLanguageServerReleaseChannel.PRERELEASE
      ? latestNonDraftRelease()
      : latestStableRelease();

    if (release == null) {
      throw new IOException(
        "Repository " + REPOSITORY + " has no suitable releases for channel " + channel);
    }
    return release;
  }

  /**
   * Последний стабильный релиз: эндпоинт {@code releases/latest} сам исключает draft
   * и pre-release, а при полном отсутствии стабильных релизов отвечает 404.
   */
  private @Nullable Release latestStableRelease() throws IOException {
    var response = send("/repos/" + REPOSITORY + "/releases/latest");
    if (response.statusCode() == 404) {
      return null;
    }
    return parseRelease(body(response));
  }

  /**
   * Последний релиз с учётом pre-release. Запрашиваем по одному релизу на страницу
   * ({@code per_page=1}, newest-first): так в ответе всегда ровно один релиз и не нужно
   * сопоставлять ассеты нескольким релизам в списке. Draft'ы (видны только push-токену)
   * пропускаем, переходя к следующей странице; анонимно GitHub их вообще не отдаёт.
   */
  private @Nullable Release latestNonDraftRelease() throws IOException {
    for (var page = 1; page <= MAX_RELEASES_PAGES; page++) {
      var body = get("/repos/" + REPOSITORY + "/releases?per_page=1&page=" + page);
      if (!HAS_RELEASE.matcher(body).find()) {
        return null;
      }
      if (DRAFT.matcher(body).find()) {
        continue;
      }
      return parseRelease(body);
    }
    return null;
  }

  /**
   * Извлекает версию и ссылки на ассеты из ответа с одним релизом. Версия — тег из пути
   * download-ссылки (у всех ассетов релиза он одинаковый), карта — «имя ассета → URL». Если
   * ассетов нет, релиз бесполезен загрузчику — возвращается {@code null}.
   */
  private static @Nullable Release parseRelease(String body) {
    var assetUrls = new LinkedHashMap<String, String>();
    String version = null;
    var matcher = ASSET_URL.matcher(body);
    while (matcher.find()) {
      if (version == null) {
        version = matcher.group(1);
      }
      assetUrls.putIfAbsent(matcher.group(2), matcher.group());
    }
    if (version == null) {
      return null;
    }
    return new Release(version, Map.copyOf(assetUrls));
  }

  private String get(String path) throws IOException {
    return body(send(path));
  }

  private HttpResponse<String> send(String path) throws IOException {
    var builder = HttpRequest.newBuilder(URI.create(API_ROOT + path))
      .header("Accept", "application/vnd.github+json")
      .header("X-GitHub-Api-Version", "2022-11-28")
      .header("User-Agent", "1c-syntax-utils")
      .timeout(REQUEST_TIMEOUT)
      .GET();
    if (token != null && !token.isBlank()) {
      builder.header("Authorization", "Bearer " + token);
    }

    try {
      return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("GitHub API request " + path + " was interrupted", e);
    }
  }

  private static String body(HttpResponse<String> response) throws IOException {
    if (response.statusCode() != 200) {
      var details = errorDetails(response.body());
      throw new IOException("GitHub API request " + response.request().uri()
        + " failed: HTTP " + response.statusCode() + details);
    }
    return response.body();
  }

  private static String errorDetails(@Nullable String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    var trimmed = body.strip();
    if (trimmed.length() > ERROR_BODY_LIMIT) {
      trimmed = trimmed.substring(0, ERROR_BODY_LIMIT) + "…";
    }
    return ": " + trimmed;
  }

  /**
   * Сведения о релизе, нужные загрузчику.
   *
   * @param version           тег/версия релиза (может начинаться с {@code v})
   * @param assetDownloadUrls карта «имя ассета → URL для скачивания»
   */
  public record Release(String version, Map<String, String> assetDownloadUrls) {
  }
}
