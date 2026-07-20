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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент GitHub-релизов BSL Language Server: находит последний релиз канала в репозитории
 * {@value #REPOSITORY} через GitHub REST API. Работает на {@link java.net.http.HttpClient}
 * и встроенном JSON-парсере — без клиентских библиотек GitHub и внешних JSON-библиотек,
 * чтобы рантайм-замкнутость оставалась минимальной (важно для встраивания в OSGi).
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
    Map<?, ?> release;
    if (channel == BslLanguageServerReleaseChannel.PRERELEASE) {
      release = latestNonDraftRelease();
    } else {
      release = latestStableRelease();
    }

    if (release == null || !(release.get("tag_name") instanceof String tagName)) {
      throw new IOException(
        "Repository " + REPOSITORY + " has no suitable releases for channel " + channel);
    }
    return new Release(tagName, assetDownloadUrls(release));
  }

  /**
   * Последний стабильный релиз: эндпоинт {@code releases/latest} сам исключает draft
   * и pre-release, а при полном отсутствии стабильных релизов отвечает 404.
   */
  private @Nullable Map<?, ?> latestStableRelease() throws IOException {
    var response = send("/repos/" + REPOSITORY + "/releases/latest");
    if (response.statusCode() == 404) {
      return null;
    }
    return Json.parse(body(response)) instanceof Map<?, ?> release ? release : null;
  }

  /**
   * Последний релиз с учётом pre-release: список {@code releases} отдаётся newest-first,
   * берём первый не-draft. Драфты видны только пользователям с push-доступом, но при вызове
   * с таким токеном их нужно пропустить, дочитывая следующие страницы при необходимости.
   */
  private @Nullable Map<?, ?> latestNonDraftRelease() throws IOException {
    for (var page = 1; ; page++) {
      var path = "/repos/" + REPOSITORY + "/releases?per_page=" + RELEASES_PER_PAGE + "&page=" + page;
      if (!(Json.parse(get(path)) instanceof List<?> releases) || releases.isEmpty()) {
        return null;
      }
      for (Object candidate : releases) {
        if (candidate instanceof Map<?, ?> release && !Boolean.TRUE.equals(release.get("draft"))) {
          return release;
        }
      }
    }
  }

  private static Map<String, String> assetDownloadUrls(Map<?, ?> release) {
    var assetUrls = new HashMap<String, String>();
    if (release.get("assets") instanceof List<?> assets) {
      for (Object candidate : assets) {
        if (candidate instanceof Map<?, ?> asset
          && asset.get("name") instanceof String name
          && asset.get("browser_download_url") instanceof String url) {
          assetUrls.putIfAbsent(name, url);
        }
      }
    }
    return Map.copyOf(assetUrls);
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
      throw new IOException(
        "GitHub API request " + response.request().uri() + " failed: HTTP " + response.statusCode());
    }
    return response.body();
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
