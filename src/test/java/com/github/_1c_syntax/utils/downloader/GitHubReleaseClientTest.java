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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubReleaseClientTest {

  private final List<HttpRequest> requests = new ArrayList<>();

  @Test
  void stableChannelUsesLatestReleaseEndpoint() throws IOException {
    var client = new GitHubReleaseClient("token", httpClient(200, releaseObject("v1.2.3", false, "nix", "win")));

    var release = client.latestRelease(BslLanguageServerReleaseChannel.STABLE);

    assertThat(release.version()).isEqualTo("v1.2.3");
    assertThat(release.assetDownloadUrls()).containsOnly(
      entry("bsl-language-server_nix.zip", downloadUrl("v1.2.3", "nix")),
      entry("bsl-language-server_win.zip", downloadUrl("v1.2.3", "win")));

    assertThat(requests).hasSize(1);
    var request = requests.get(0);
    assertThat(request.uri().toString())
      .isEqualTo("https://api.github.com/repos/1c-syntax/bsl-language-server/releases/latest");
    assertThat(request.headers().firstValue("Authorization")).contains("Bearer token");
    assertThat(request.headers().firstValue("Accept")).contains("application/vnd.github+json");
    assertThat(request.headers().firstValue("User-Agent")).contains("1c-syntax-utils");
  }

  @Test
  void versionAndAssetsComeFromDownloadUrlNotFromReleaseBody() throws IOException {
    // tag_name и body намеренно «отравлены»: фейковый тег и чужая download-ссылка в релиз-ноутах.
    // Результат должен опираться только на настоящие ссылки на ассеты этого репозитория.
    var body = "{\"tag_name\":\"v0.0.0-fake\",\"draft\":false,"
      + "\"body\":\"см. https://github.com/evil/repo/releases/download/v9.9.9/bsl-language-server_nix.zip "
      + "и {\\\"assets\\\":[]}\","
      + "\"assets\":[" + asset("nix", "v1.2.3") + "]}";
    var client = new GitHubReleaseClient(null, httpClient(200, body));

    var release = client.latestRelease(BslLanguageServerReleaseChannel.STABLE);

    assertThat(release.version()).isEqualTo("v1.2.3");
    assertThat(release.assetDownloadUrls())
      .containsOnly(entry("bsl-language-server_nix.zip", downloadUrl("v1.2.3", "nix")));
  }

  @Test
  void anonymousClientSendsNoAuthorizationHeader() throws IOException {
    var client = new GitHubReleaseClient(null, httpClient(200, releaseObject("v1.2.3", false, "nix")));

    client.latestRelease(BslLanguageServerReleaseChannel.STABLE);

    assertThat(requests.get(0).headers().firstValue("Authorization")).isEmpty();
  }

  @Test
  void blankTokenSendsNoAuthorizationHeader() throws IOException {
    var client = new GitHubReleaseClient("  ", httpClient(200, releaseObject("v1.2.3", false, "nix")));

    client.latestRelease(BslLanguageServerReleaseChannel.STABLE);

    assertThat(requests.get(0).headers().firstValue("Authorization")).isEmpty();
  }

  @Test
  void prereleaseChannelRequestsSinglereleasePageAndPicksIt() throws IOException {
    var client = new GitHubReleaseClient(null,
      httpClient(200, releaseArray(releaseObject("v1.3.0-rc.1", false, "nix"))));

    var release = client.latestRelease(BslLanguageServerReleaseChannel.PRERELEASE);

    assertThat(release.version()).isEqualTo("v1.3.0-rc.1");
    assertThat(release.assetDownloadUrls())
      .containsOnly(entry("bsl-language-server_nix.zip", downloadUrl("v1.3.0-rc.1", "nix")));
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).uri().toString())
      .contains("/releases?per_page=1&page=1");
  }

  @Test
  void prereleaseChannelSkipsDraftPagesUntilNonDraft() throws IOException {
    var client = new GitHubReleaseClient(null, httpClient(request -> {
      var draftPage = request.uri().toString().endsWith("page=1");
      return response(request, 200, releaseArray(
        draftPage
          ? releaseObject("v9.9.9", true, "nix")
          : releaseObject("v1.3.0-rc.1", false, "nix")));
    }));

    var release = client.latestRelease(BslLanguageServerReleaseChannel.PRERELEASE);

    assertThat(release.version()).isEqualTo("v1.3.0-rc.1");
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).uri().toString()).endsWith("page=1");
    assertThat(requests.get(1).uri().toString()).endsWith("page=2");
  }

  @Test
  void prereleaseChannelStopsPagingAtBoundWhenEveryPageIsDraft() {
    var client = new GitHubReleaseClient(null, httpClient(request ->
      response(request, 200, releaseArray(releaseObject("v9.9.9", true, "nix")))));

    assertThatThrownBy(() -> client.latestRelease(BslLanguageServerReleaseChannel.PRERELEASE))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("no suitable releases");
    // Ограничение пагинации: без него мок отдавал бы непустую draft-страницу бесконечно.
    assertThat(requests).hasSize(10);
  }

  @Test
  void prereleaseChannelStopsAtEmptyPage() {
    var client = new GitHubReleaseClient(null, httpClient(200, "[]"));

    assertThatThrownBy(() -> client.latestRelease(BslLanguageServerReleaseChannel.PRERELEASE))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("no suitable releases");
    assertThat(requests).hasSize(1);
  }

  @Test
  void stableChannelFailsWhenThereAreNoStableReleases() {
    var client = new GitHubReleaseClient(null, httpClient(404, "{\"message\": \"Not Found\"}"));

    assertThatThrownBy(() -> client.latestRelease(BslLanguageServerReleaseChannel.STABLE))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("no suitable releases");
  }

  @Test
  void failsOnHttpError() {
    var client = new GitHubReleaseClient(null, httpClient(403, "{\"message\": \"rate limit\"}"));

    assertThatThrownBy(() -> client.latestRelease(BslLanguageServerReleaseChannel.STABLE))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("HTTP 403")
      .hasMessageContaining("rate limit");
  }

  @Test
  void releaseWithoutMatchingAssetsIsTreatedAsNoSuitable() {
    // Ответ 200, но ссылок на ассеты этого репозитория нет — версию вывести не из чего.
    var client = new GitHubReleaseClient(null,
      httpClient(200, "{\"tag_name\":\"v1.0.0\",\"draft\":false,\"assets\":[]}"));

    assertThatThrownBy(() -> client.latestRelease(BslLanguageServerReleaseChannel.STABLE))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("no suitable releases");
  }

  @Test
  void interruptedRequestRestoresInterruptFlagAndFails() throws Exception {
    var httpClient = mock(HttpClient.class);
    doAnswer(invocation -> {
      throw new InterruptedException("interrupted");
    }).when(httpClient).send(any(), any());
    var client = new GitHubReleaseClient(null, httpClient);

    try {
      assertThatThrownBy(() -> client.latestRelease(BslLanguageServerReleaseChannel.STABLE))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted(); // сбрасываем флаг, чтобы не влиять на другие тесты
    }
  }

  private static String downloadUrl(String tag, String os) {
    return "https://github.com/1c-syntax/bsl-language-server/releases/download/"
      + tag + "/bsl-language-server_" + os + ".zip";
  }

  private static String asset(String os, String tag) {
    return "{\"name\":\"bsl-language-server_" + os + ".zip\","
      + "\"browser_download_url\":\"" + downloadUrl(tag, os) + "\"}";
  }

  /**
   * JSON одного релиза с ассетами под указанные ОС (как отдаёт {@code releases/latest}).
   */
  private static String releaseObject(String tag, boolean draft, String... oses) {
    var assets = Arrays.stream(oses).map(os -> asset(os, tag)).collect(Collectors.joining(","));
    return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft
      + ",\"body\":\"notes\",\"assets\":[" + assets + "]}";
  }

  /**
   * Обёртка релиза в список из одного элемента (как отдаёт {@code releases?per_page=1}).
   */
  private static String releaseArray(String releaseObject) {
    return "[" + releaseObject + "]";
  }

  private HttpClient httpClient(int status, String body) {
    return httpClient(request -> response(request, status, body));
  }

  /**
   * Мок {@link HttpClient}, отвечающий на каждый {@code send} через {@code responses}
   * и записывающий запросы в {@link #requests}.
   */
  private HttpClient httpClient(Function<HttpRequest, HttpResponse<String>> responses) {
    var client = mock(HttpClient.class);
    try {
      when(client.send(any(), any())).thenAnswer(invocation -> {
        HttpRequest request = invocation.getArgument(0);
        requests.add(request);
        return responses.apply(request);
      });
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e); // не бывает: это настройка мока, а не реальный вызов
    }
    return client;
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<String> response(HttpRequest request, int status, String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(response.request()).thenReturn(request);
    return response;
  }
}
