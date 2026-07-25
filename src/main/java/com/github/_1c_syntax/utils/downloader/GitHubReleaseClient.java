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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Клиент GitHub-релизов BSL Language Server: находит последний релиз канала в репозитории
 * {@value #REPOSITORY} через GitHub REST API. Работает на {@link java.net.http.HttpClient}
 * и разбирает ответ через gson — без клиентских библиотек GitHub (и без Jackson, который тянула
 * прежняя github-api), чтобы рантайм-замкнутость оставалась минимальной и OSGi-совместимой.
 *
 * <p>Из ответа нужны лишь тег релиза, флаг {@code draft} и ассеты (имя + URL). Структурный разбор
 * gson берёт их строго из нужных полей, поэтому произвольное содержимое поля {@code body}
 * (релиз-ноуты) на результат не влияет.
 *
 * <p>Отдельная зависимость загрузчика — чтобы в тестах его можно было замокать и прогнать поток
 * скачивания без обращения к GitHub. Класс не {@code final} специально: так его мокает Mockito.
 */
public class GitHubReleaseClient {

  private static final String REPOSITORY = "1c-syntax/bsl-language-server";
  private static final String API_ROOT = "https://api.github.com";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  // Релизы отдаются newest-first: не-draft почти всегда на первой странице, поэтому страницы
  // небольшие; пагинация ниже дочитает хвост в вырожденном случае «страница целиком из драфтов».
  private static final int RELEASES_PER_PAGE = 30;
  // Верхняя граница пагинации: у настоящего GitHub цикл завершает пустая страница за последней,
  // но зеркало/прокси/кэш может бесконечно отдавать одну и ту же непустую страницу драфтов —
  // ограничение защищает от бесконечного опроса до упора в rate limit.
  private static final int MAX_RELEASES_PAGES = 10;
  // Сколько символов тела ответа включать в текст ошибки для диагностики (GitHub кладёт причину
  // в поле message; токен в теле не возвращается, так что утечки секрета нет).
  private static final int ERROR_BODY_LIMIT = 500;

  private final @Nullable String token;
  private final HttpClient httpClient;

  /**
   * Создаёт клиент с {@link HttpClient} по умолчанию (таймаут соединения и следование редиректам
   * настроены под GitHub API).
   *
   * @param token GitHub OAuth-токен для обхода лимитов анонимного API; может быть {@code null}
   */
  public GitHubReleaseClient(@Nullable String token) {
    this(token, HttpClient.newBuilder()
      .connectTimeout(CONNECT_TIMEOUT)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build());
  }

  /**
   * Создаёт клиент с переданным {@link HttpClient}.
   *
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

    var result = release == null ? null : toRelease(release);
    if (result == null) {
      throw new IOException(
        "Repository " + REPOSITORY + " has no suitable releases for channel " + channel);
    }
    return result;
  }

  /**
   * Последний стабильный релиз: эндпоинт {@code releases/latest} сам исключает draft
   * и pre-release, а при полном отсутствии стабильных релизов отвечает 404.
   */
  private @Nullable JsonObject latestStableRelease() throws IOException {
    var response = send("/repos/" + REPOSITORY + "/releases/latest");
    if (response.statusCode() == 404) {
      return null;
    }
    return parse(body(response)) instanceof JsonObject release ? release : null;
  }

  /**
   * Последний релиз с учётом pre-release: список {@code releases} отдаётся newest-first,
   * берём первый не-draft. Драфты видны только пользователям с push-доступом, но при вызове
   * с таким токеном их нужно пропустить, дочитывая следующие страницы при необходимости.
   */
  private @Nullable JsonObject latestNonDraftRelease() throws IOException {
    for (var page = 1; page <= MAX_RELEASES_PAGES; page++) {
      var path = "/repos/" + REPOSITORY + "/releases?per_page=" + RELEASES_PER_PAGE + "&page=" + page;
      if (!(parse(get(path)) instanceof JsonArray releases) || releases.isEmpty()) {
        return null;
      }
      for (JsonElement candidate : releases) {
        if (candidate instanceof JsonObject release && !isDraft(release)) {
          return release;
        }
      }
    }
    return null;
  }

  private static boolean isDraft(JsonObject release) {
    return asBoolean(release.get("draft"));
  }

  /**
   * Извлекает из объекта релиза версию (тег) и карту «имя ассета → URL». Возвращает {@code null},
   * если у релиза нет тега.
   */
  private static @Nullable Release toRelease(JsonObject release) {
    var version = asString(release.get("tag_name"));
    if (version == null) {
      return null;
    }
    var assetUrls = new LinkedHashMap<String, String>();
    if (release.get("assets") instanceof JsonArray assets) {
      for (JsonElement candidate : assets) {
        if (candidate instanceof JsonObject asset) {
          var name = asString(asset.get("name"));
          var url = asString(asset.get("browser_download_url"));
          if (name != null && url != null) {
            assetUrls.putIfAbsent(name, url);
          }
        }
      }
    }
    return new Release(version, Map.copyOf(assetUrls));
  }

  private static @Nullable String asString(@Nullable JsonElement element) {
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
      ? element.getAsString()
      : null;
  }

  private static boolean asBoolean(@Nullable JsonElement element) {
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
      && element.getAsBoolean();
  }

  private static JsonElement parse(String body) throws IOException {
    try {
      return JsonParser.parseString(body);
    } catch (JsonSyntaxException e) {
      throw new IOException("Malformed GitHub API response", e);
    }
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
