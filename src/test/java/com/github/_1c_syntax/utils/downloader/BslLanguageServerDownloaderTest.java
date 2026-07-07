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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BslLanguageServerDownloaderTest {

  @Test
  void compareVersionsOrdersReleasesByCore() {
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.2", "1.0.1")).isPositive();
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.1", "1.0.2")).isNegative();
    assertThat(BslLanguageServerDownloader.compareVersions("1.1.0", "1.0.9")).isPositive();
    assertThat(BslLanguageServerDownloader.compareVersions("2.0.0", "1.9.9")).isPositive();
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.0", "1.0.0")).isZero();
  }

  @Test
  void compareVersionsTreatsReleaseAsNewerThanPreRelease() {
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.0", "1.0.0-rc.1")).isPositive();
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.0-rc.1", "1.0.0")).isNegative();
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.0-rc.2", "1.0.0-rc.1")).isPositive();
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.0-rc.10", "1.0.0-rc.9")).isPositive();
    assertThat(BslLanguageServerDownloader.compareVersions("1.0.0-rc.9", "1.0.0-rc.10")).isNegative();
  }

  @Test
  void installedVersionIsEmptyWhenNothingInstalled(@TempDir Path installDir) {
    var downloader = new BslLanguageServerDownloader(installDir, mock(GitHubReleaseClient.class), unusedHttpClient());
    assertThat(downloader.installedVersion()).isEmpty();
    assertThat(downloader.installedBinary()).isEmpty();
  }

  @Test
  void installedVersionIsReadFromServerInfo(@TempDir Path installDir) throws IOException {
    writeServerInfo(installDir, "1.0.2");

    var downloader = new BslLanguageServerDownloader(installDir, mock(GitHubReleaseClient.class), unusedHttpClient());
    assertThat(downloader.installedVersion()).contains("1.0.2");
  }

  @Test
  @DisabledOnOs({OS.WINDOWS, OS.MAC})
  void installedBinaryResolvesLinuxLauncher(@TempDir Path installDir) throws IOException {
    writeServerInfo(installDir, "1.0.2");
    var binary = installDir.resolve("1.0.2")
      .resolve("bsl-language-server")
      .resolve("bin")
      .resolve("bsl-language-server");
    Files.createDirectories(binary.getParent());
    Files.createFile(binary);

    var downloader = new BslLanguageServerDownloader(installDir, mock(GitHubReleaseClient.class), unusedHttpClient());
    assertThat(downloader.installedBinary()).contains(binary);
  }

  @Test
  void copyToFileWritesContentAndReportsProgress(@TempDir Path installDir) throws IOException {
    var payload = "bsl-language-server".getBytes(StandardCharsets.UTF_8);
    var destination = installDir.resolve("asset.bin");
    var progress = new ArrayList<long[]>();

    BslLanguageServerDownloader.copyToFile(
      new ByteArrayInputStream(payload),
      destination,
      payload.length,
      (bytesRead, totalBytes) -> progress.add(new long[]{bytesRead, totalBytes}));

    assertThat(Files.readAllBytes(destination)).isEqualTo(payload);
    assertThat(progress).isNotEmpty();
    assertThat(progress.get(0)).containsExactly(0, payload.length);
    var last = progress.get(progress.size() - 1);
    assertThat(last).containsExactly(payload.length, payload.length);
    assertThat(last[0]).isLessThanOrEqualTo(last[1]);
  }

  @Test
  void copyToFileReportsProgressAcrossMultipleChunks(@TempDir Path installDir) throws IOException {
    // Больше буфера copyToFile, чтобы цикл чтения выполнился несколько раз.
    var payload = new byte[1024 * 1024];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) i;
    }
    var destination = installDir.resolve("asset.bin");
    var progress = new ArrayList<long[]>();

    BslLanguageServerDownloader.copyToFile(
      new ByteArrayInputStream(payload),
      destination,
      payload.length,
      (bytesRead, totalBytes) -> progress.add(new long[]{bytesRead, totalBytes}));

    assertThat(Files.readAllBytes(destination)).isEqualTo(payload);
    assertThat(progress).hasSizeGreaterThan(2);
    assertThat(progress).map(it -> it[0]).isSorted();
    assertThat(progress.get(progress.size() - 1)).containsExactly(payload.length, payload.length);
  }

  @Test
  void copyToFileLeavesSourceOpenForCaller(@TempDir Path installDir) throws IOException {
    var closed = new AtomicInteger();
    var source = new ByteArrayInputStream(new byte[]{1, 2, 3}) {
      @Override
      public void close() {
        closed.incrementAndGet();
      }
    };

    BslLanguageServerDownloader.copyToFile(
      source, installDir.resolve("asset.bin"), 3, DownloadProgressListener.NONE);

    assertThat(closed.get()).isZero();
  }

  @Test
  void copyToFilePropagatesUnknownTotalSize(@TempDir Path installDir) throws IOException {
    var destination = installDir.resolve("asset.bin");
    List<Long> totals = new ArrayList<>();

    BslLanguageServerDownloader.copyToFile(
      new ByteArrayInputStream(new byte[]{1, 2, 3}),
      destination,
      -1,
      (bytesRead, totalBytes) -> totals.add(totalBytes));

    assertThat(totals).isNotEmpty().allMatch(total -> total == -1);
  }

  @Test
  void downloadIfNeededDownloadsExtractsAndRecordsVersion(@TempDir Path installDir) throws IOException {
    var archive = zipWithLaunchers(300 * 1024);
    var releaseClient = mock(GitHubReleaseClient.class);
    when(releaseClient.latestRelease(any()))
      .thenReturn(new GitHubReleaseClient.Release("v1.2.3", allOsAssets()));
    var downloader = new BslLanguageServerDownloader(installDir, releaseClient, httpClientReturning(archive));

    var binary = downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.STABLE);

    assertThat(binary).exists();
    assertThat(binary).startsWith(installDir.resolve("1.2.3"));
    assertThat(downloader.installedVersion()).contains("1.2.3");
  }

  @Test
  void downloadIfNeededReportsProgressForTheAsset(@TempDir Path installDir) throws IOException {
    var archive = zipWithLaunchers(300 * 1024);
    var releaseClient = mock(GitHubReleaseClient.class);
    when(releaseClient.latestRelease(any()))
      .thenReturn(new GitHubReleaseClient.Release("1.0.0", allOsAssets()));
    var downloader = new BslLanguageServerDownloader(installDir, releaseClient, httpClientReturning(archive));
    var progress = new ArrayList<long[]>();

    downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.STABLE,
      (bytesRead, totalBytes) -> progress.add(new long[]{bytesRead, totalBytes}));

    assertThat(progress).isNotEmpty();
    var last = progress.get(progress.size() - 1);
    assertThat(last[0]).isEqualTo(archive.length);
    assertThat(last[1]).isEqualTo(archive.length);
  }

  @Test
  void downloadIfNeededSkipsReleaseLookupWhenIntervalNotElapsed(@TempDir Path installDir)
    throws IOException {
    writeServerInfoAt(installDir, "1.0.0", System.currentTimeMillis());
    var releaseClient = mock(GitHubReleaseClient.class);
    var downloader = new BslLanguageServerDownloader(installDir, releaseClient, unusedHttpClient());

    var binary = downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.STABLE);

    assertThat(binary.toString()).contains("1.0.0");
    verify(releaseClient, never()).latestRelease(any());
  }

  @Test
  void downloadIfNeededFallsBackToInstalledWhenLookupFails(@TempDir Path installDir) throws IOException {
    writeServerInfo(installDir, "1.0.0");
    var releaseClient = mock(GitHubReleaseClient.class);
    when(releaseClient.latestRelease(any())).thenThrow(new IOException("no network"));
    var downloader = new BslLanguageServerDownloader(installDir, releaseClient, unusedHttpClient());

    var binary = downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.STABLE);

    assertThat(binary.toString()).contains("1.0.0");
  }

  @Test
  void downloadIfNeededThrowsWhenNothingInstalledAndLookupFails(@TempDir Path installDir)
    throws IOException {
    var releaseClient = mock(GitHubReleaseClient.class);
    when(releaseClient.latestRelease(any())).thenThrow(new IOException("no network"));
    var downloader = new BslLanguageServerDownloader(installDir, releaseClient, unusedHttpClient());

    assertThatThrownBy(() -> downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.STABLE))
      .isInstanceOf(IOException.class);
  }

  @Test
  void downloadIfNeededPassesRequestedChannelToLookup(@TempDir Path installDir) throws IOException {
    writeServerInfo(installDir, "1.0.0");
    var releaseClient = mock(GitHubReleaseClient.class);
    // та же версия — скачивания не будет, проверяем только проброс канала
    when(releaseClient.latestRelease(any()))
      .thenReturn(new GitHubReleaseClient.Release("1.0.0", Map.of()));
    var downloader = new BslLanguageServerDownloader(installDir, releaseClient, unusedHttpClient());

    downloader.downloadIfNeeded(BslLanguageServerReleaseChannel.PRERELEASE);

    verify(releaseClient).latestRelease(BslLanguageServerReleaseChannel.PRERELEASE);
  }

  /**
   * Мок {@link HttpClient}, отдающий на любой {@code send} ответ 200 с телом {@code body} и
   * заголовком {@code Content-Length}. Заменяет реальную сеть в тестах скачивания.
   */
  @SuppressWarnings("unchecked")
  private static HttpClient httpClientReturning(byte[] body) throws IOException {
    HttpResponse<InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.headers()).thenReturn(HttpHeaders.of(
      Map.of("Content-Length", List.of(String.valueOf(body.length))), (name, value) -> true));
    // Свежий поток на каждый send() — чтобы мок оставался корректным, если тело когда-нибудь
    // прочитают повторно (ретрай/редирект).
    when(response.body()).thenAnswer(invocation -> new ByteArrayInputStream(body));

    var client = mock(HttpClient.class);
    try {
      // doReturn — без дженериков, чтобы не спорить с выводом типа T у send(...).
      doReturn(response).when(client).send(any(), any());
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e); // не бывает: это настройка мока, а не реальный вызов
    }
    return client;
  }

  private static HttpClient unusedHttpClient() {
    return mock(HttpClient.class);
  }

  private static Map<String, String> allOsAssets() {
    var url = "https://example.invalid/asset.zip"; // мок игнорирует URL
    return Map.of(
      "bsl-language-server_win.zip", url,
      "bsl-language-server_mac.zip", url,
      "bsl-language-server_nix.zip", url);
  }

  /**
   * Zip с раскладкой лаунчеров для всех трёх ОС (тест OS-агностичен) плюс крупный «полезный»
   * файл, чтобы скачивание шло несколькими блоками.
   */
  private static byte[] zipWithLaunchers(int payloadSize) throws IOException {
    var payload = new byte[payloadSize];
    new Random(1).nextBytes(payload);
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      putEntry(zip, "bsl-language-server/bsl-language-server.exe", new byte[]{1});
      putEntry(zip, "bsl-language-server/bin/bsl-language-server", new byte[]{1});
      putEntry(zip, "bsl-language-server.app/Contents/MacOS/bsl-language-server", new byte[]{1});
      putEntry(zip, "payload.bin", payload);
    }
    return bytes.toByteArray();
  }

  private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(data);
    zip.closeEntry();
  }

  private static void writeServerInfo(Path installDir, String version) throws IOException {
    writeServerInfoAt(installDir, version, 0);
  }

  private static void writeServerInfoAt(Path installDir, String version, long lastUpdate)
    throws IOException {
    Files.createDirectories(installDir);
    Files.writeString(installDir.resolve("SERVER-INFO"),
      "version=" + version + System.lineSeparator()
        + "lastUpdate=" + lastUpdate + System.lineSeparator());
  }
}
