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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;
import org.semver4j.Semver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Загрузчик BSL Language Server из GitHub-релизов.
 *
 * <p>Скачивает подходящий под текущую ОС ассет {@code bsl-language-server_{win|mac|nix}.zip}
 * последнего релиза (стабильного или pre-release), распаковывает его в подпапку с версией
 * и возвращает путь к исполняемому файлу сервера. Установленная версия кэшируется: повторные
 * вызовы обращаются к GitHub API не чаще одного раза в {@link #checkInterval} и скачивают
 * новую версию только при её появлении.
 *
 * <p>Реализация повторяет поведение загрузчика из
 * <a href="https://github.com/1c-syntax/vsc-language-1c-bsl">vsc-language-1c-bsl</a>,
 * чтобы раскладка каталога установки и правила выбора ассета совпадали между VS Code и
 * IntelliJ-based клиентами.
 */
@Slf4j
public class BslLanguageServerDownloader {

  private static final String SERVER_INFO_FILE = "SERVER-INFO";
  private static final String INFO_VERSION = "version";
  private static final String INFO_LAST_UPDATE = "lastUpdate";
  private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(8);
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
  // Крупнее дефолта JDK (16 КБ у InputStream.transferTo): архивы сервера — десятки-сотни МБ,
  // так меньше syscall'ов на чтение/запись и реже дёргается прогресс-колбэк.
  private static final int DOWNLOAD_BUFFER_SIZE = 256 * 1024;

  private static final boolean POSIX =
    FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

  private final Path installDir;
  private final GitHubReleaseClient releaseClient;
  private final HttpClient httpClient;

  /**
   * @param installDir    каталог установки сервера; в нём создаются подпапки с версиями
   *                      и файл {@code SERVER-INFO}
   * @param releaseClient источник сведений о последнем релизе
   * @param httpClient    клиент только для скачивания ассета (github-api эту загрузку не умеет);
   *                      должен следовать редиректам — ассеты GitHub отдаются редиректом на CDN
   */
  public BslLanguageServerDownloader(Path installDir, GitHubReleaseClient releaseClient,
                                     HttpClient httpClient) {
    this.installDir = installDir.toAbsolutePath();
    this.releaseClient = releaseClient;
    this.httpClient = httpClient;
  }

  /**
   * Возвращает путь к исполняемому файлу установленного сервера, если сервер уже скачан.
   *
   * @return путь к бинарю сервера или {@link Optional#empty()}, если сервер ещё не установлен
   */
  public Optional<Path> installedBinary() {
    return installedVersion()
      .map(this::binaryPath)
      .filter(Files::exists);
  }

  /**
   * Возвращает версию установленного сервера.
   *
   * @return версия из {@code SERVER-INFO} или {@link Optional#empty()}, если сервер не установлен
   */
  public Optional<String> installedVersion() {
    var info = readServerInfo();
    var version = info.getProperty(INFO_VERSION);
    return Optional.ofNullable(version).filter(it -> !it.isBlank());
  }

  /**
   * Скачивает сервер при необходимости и возвращает путь к его исполняемому файлу.
   *
   * <p>Если с момента прошлой проверки прошло меньше {@link #DEFAULT_CHECK_INTERVAL}, GitHub не
   * опрашивается и возвращается уже установленный сервер. Если сеть недоступна, но сервер
   * уже установлен, возвращается установленная версия. Если сервер не установлен и скачать
   * его не удалось — выбрасывается {@link IOException}.
   *
   * @param channel канал релизов (стабильный / pre-release)
   * @return путь к исполняемому файлу сервера
   * @throws IOException если сервер не установлен и его не удалось скачать
   */
  public Path downloadIfNeeded(BslLanguageServerReleaseChannel channel) throws IOException {
    return downloadIfNeeded(channel, DownloadProgressListener.NONE);
  }

  /**
   * Скачивает сервер при необходимости и возвращает путь к его исполняемому файлу, сообщая о
   * прогрессе скачивания ассета.
   *
   * <p>Поведение совпадает с {@link #downloadIfNeeded(BslLanguageServerReleaseChannel)}; отличие —
   * в передаче {@code progressListener}, который вызывается по мере вычитывания тела ответа
   * (только когда действительно происходит скачивание новой версии).
   *
   * @param channel          канал релизов (стабильный / pre-release)
   * @param progressListener слушатель прогресса скачивания ассета
   * @return путь к исполняемому файлу сервера
   * @throws IOException если сервер не установлен и его не удалось скачать
   */
  public Path downloadIfNeeded(BslLanguageServerReleaseChannel channel,
                               DownloadProgressListener progressListener) throws IOException {
    var installed = installedVersion().orElse(null);

    if (installed != null && !checkIntervalElapsed()) {
      LOGGER.debug("Skipping BSL Language Server update check (interval not elapsed)");
      return binaryPath(installed);
    }

    GitHubReleaseClient.Release release;
    try {
      release = releaseClient.latestRelease(channel);
    } catch (IOException e) {
      if (installed != null) {
        LOGGER.warn("Failed to fetch BSL Language Server releases, using installed version {}",
          installed, e);
        touchLastUpdate(installed);
        return binaryPath(installed);
      }
      throw new IOException("Failed to fetch BSL Language Server releases", e);
    }

    var latestVersion = normalizeVersion(release.version());

    if (installed == null || compareVersions(latestVersion, installed) > 0) {
      LOGGER.info("Downloading BSL Language Server {}", latestVersion);
      try {
        downloadAndExtract(release, latestVersion, progressListener);
        cleanupOtherVersions(latestVersion);
        installed = latestVersion;
      } catch (IOException e) {
        if (installed == null) {
          throw e;
        }
        LOGGER.warn("Failed to download BSL Language Server {}, using installed version {}",
          latestVersion, installed, e);
        touchLastUpdate(installed);
        return binaryPath(installed);
      }
    }

    writeServerInfo(installed);
    return binaryPath(installed);
  }

  private boolean checkIntervalElapsed() {
    var info = readServerInfo();
    var lastUpdate = info.getProperty(INFO_LAST_UPDATE);
    if (lastUpdate == null) {
      return true;
    }
    try {
      var elapsed = System.currentTimeMillis() - Long.parseLong(lastUpdate);
      return elapsed >= DEFAULT_CHECK_INTERVAL.toMillis();
    } catch (NumberFormatException e) {
      return true;
    }
  }

  private void downloadAndExtract(GitHubReleaseClient.Release release, String version,
                                  DownloadProgressListener progressListener) throws IOException {
    var assetName = assetName();
    var downloadUrl = release.assetDownloadUrls().get(assetName);
    if (downloadUrl == null) {
      throw new IOException("BSL Language Server release " + version + " has no asset " + assetName);
    }

    Files.createDirectories(installDir);
    var archive = installDir.resolve(version + "-download-" + assetName);
    var versionDir = installDir.resolve(version);

    try {
      download(downloadUrl, archive, progressListener);
      deleteRecursively(versionDir);
      extract(archive, versionDir);
    } finally {
      Files.deleteIfExists(archive);
    }
  }

  private void download(String url, Path destination, DownloadProgressListener progressListener)
    throws IOException {
    var request = HttpRequest.newBuilder(URI.create(url))
      .header("User-Agent", "1c-syntax-utils")
      .timeout(DOWNLOAD_TIMEOUT)
      .GET()
      .build();
    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        try (var ignored = response.body()) {
          throw new IOException("Failed to download " + url + ": HTTP " + response.statusCode());
        }
      }
      var totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
      // Владелец потока — этот блок (здесь взят response.body()), он же его и закрывает.
      // Тело читается лениво, поэтому request.timeout его не покрывает: по дедлайну сторож
      // закрывает поток — единственный способ разбудить заблокированный read(). При штатном
      // завершении сторож отменяется. copyToFile поток не закрывает.
      try (var body = response.body()) {
        var watchdog = CompletableFuture.runAsync(
          () -> closeQuietly(body),
          CompletableFuture.delayedExecutor(DOWNLOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        try {
          copyToFile(body, destination, totalBytes, progressListener);
        } finally {
          watchdog.cancel(false);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("BSL Language Server download was interrupted", e);
    }
  }

  /**
   * Копирует {@code source} в файл, сообщая о прогрессе. Поток {@code source} не закрывает —
   * этим владеет вызывающий код.
   */
  static void copyToFile(InputStream source, Path destination, long totalBytes,
                         DownloadProgressListener progressListener) throws IOException {
    var buffer = new byte[DOWNLOAD_BUFFER_SIZE];
    long readTotal = 0;
    try (var output = Files.newOutputStream(destination)) {
      progressListener.onProgress(0, totalBytes);
      int read;
      while ((read = source.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        readTotal += read;
        progressListener.onProgress(readTotal, totalBytes);
      }
    }
  }

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException e) {
      LOGGER.debug("Failed to close BSL Language Server download stream", e);
    }
  }

  private static void extract(Path archive, Path targetDir) throws IOException {
    Files.createDirectories(targetDir);
    try (var zip = ZipFile.builder().setPath(archive).get()) {
      var entries = zip.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement();
        var target = targetDir.resolve(entry.getName()).normalize();
        if (!target.startsWith(targetDir)) {
          throw new IOException("Illegal archive entry (zip slip): " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
          continue;
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = zip.getInputStream(entry)) {
          Files.copy(input, target, REPLACE_EXISTING);
        }
        applyUnixMode(target, entry.getUnixMode());
      }
    }
  }

  private static void applyUnixMode(Path path, int unixMode) throws IOException {
    if (!POSIX || unixMode == 0) {
      return;
    }
    Files.setPosixFilePermissions(path, permissionsFromMode(unixMode));
  }

  /**
   * Преобразует unix-режим из zip в набор прав, ограниченный владельцем: групповые и «прочие»
   * права намеренно не выдаются, чтобы не создавать слишком свободный доступ. Владельцу всегда
   * доступны чтение и запись, бит исполнения выставляется, если он был установлен в архиве
   * (нужно для launcher'а и бинарей внутри native-image).
   */
  private static Set<PosixFilePermission> permissionsFromMode(int mode) {
    var permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    if ((mode & 0111) != 0) {
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
    }
    return permissions;
  }

  private void cleanupOtherVersions(String keepVersion) {
    try (Stream<Path> children = Files.list(installDir)) {
      children
        .filter(Files::isDirectory)
        .filter(path -> !path.getFileName().toString().equals(keepVersion))
        .forEach(BslLanguageServerDownloader::deleteQuietly);
    } catch (IOException e) {
      LOGGER.warn("Failed to clean up old BSL Language Server versions in {}", installDir, e);
    }
  }

  /**
   * Возвращает путь к исполняемому файлу сервера внутри распакованного архива указанной версии.
   *
   * <p>Раскладка native-image архива BSL Language Server:
   * <ul>
   *   <li>Windows: {@code bsl-language-server/bsl-language-server.exe}</li>
   *   <li>Linux: {@code bsl-language-server/bin/bsl-language-server}</li>
   *   <li>macOS: {@code bsl-language-server.app/Contents/MacOS/bsl-language-server}</li>
   * </ul>
   */
  private Path binaryPath(String version) {
    var os = currentOs();
    var archiveDir = "bsl-language-server" + (os == Os.MAC ? ".app" : "");
    var binaryName = "bsl-language-server" + (os == Os.WINDOWS ? ".exe" : "");
    var versionDir = installDir.resolve(version).resolve(archiveDir);

    return switch (os) {
      case WINDOWS -> versionDir.resolve(binaryName);
      case LINUX -> versionDir.resolve("bin").resolve(binaryName);
      case MAC -> versionDir.resolve("Contents").resolve("MacOS").resolve(binaryName);
    };
  }

  private static String assetName() {
    return "bsl-language-server_" + currentOs().assetPostfix + ".zip";
  }

  private Properties readServerInfo() {
    var properties = new Properties();
    var infoFile = installDir.resolve(SERVER_INFO_FILE);
    if (Files.exists(infoFile)) {
      try (var input = Files.newInputStream(infoFile)) {
        properties.load(input);
      } catch (IOException e) {
        LOGGER.warn("Failed to read {}", infoFile, e);
      }
    }
    return properties;
  }

  private void writeServerInfo(String version) {
    var properties = new Properties();
    properties.setProperty(INFO_VERSION, version);
    properties.setProperty(INFO_LAST_UPDATE, Long.toString(System.currentTimeMillis()));
    storeServerInfo(properties);
  }

  private void touchLastUpdate(String version) {
    var properties = readServerInfo();
    properties.setProperty(INFO_VERSION, version);
    properties.setProperty(INFO_LAST_UPDATE, Long.toString(System.currentTimeMillis()));
    storeServerInfo(properties);
  }

  private void storeServerInfo(Properties properties) {
    try {
      Files.createDirectories(installDir);
      try (var output = Files.newOutputStream(installDir.resolve(SERVER_INFO_FILE))) {
        properties.store(output, "BSL Language Server install info");
      }
    } catch (IOException e) {
      LOGGER.warn("Failed to store BSL Language Server install info", e);
    }
  }

  private static String normalizeVersion(String tag) {
    var version = tag.startsWith("v") ? tag.substring(1) : tag;
    return version.strip();
  }

  /**
   * Сравнивает две версии по правилам semver: релиз новее одноимённого pre-release, числовые
   * идентификаторы pre-release сравниваются как числа ({@code 1.0.0-rc.10 > 1.0.0-rc.9}).
   * Если хотя бы одна строка не парсится как semver — используется лексикографическое сравнение.
   */
  static int compareVersions(String left, String right) {
    var leftVersion = Semver.parse(normalizeVersion(left));
    var rightVersion = Semver.parse(normalizeVersion(right));
    if (leftVersion == null || rightVersion == null) {
      return normalizeVersion(left).compareTo(normalizeVersion(right));
    }
    return leftVersion.compareTo(rightVersion);
  }

  private static Os currentOs() {
    var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (name.contains("win")) {
      return Os.WINDOWS;
    }
    if (name.contains("mac") || name.contains("darwin")) {
      return Os.MAC;
    }
    return Os.LINUX;
  }

  private static void deleteQuietly(Path path) {
    try {
      deleteRecursively(path);
    } catch (IOException e) {
      LOGGER.warn("Failed to delete {}", path, e);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(child -> {
        try {
          Files.delete(child);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private enum Os {
    WINDOWS("win"),
    MAC("mac"),
    LINUX("nix");

    private final String assetPostfix;

    Os(String assetPostfix) {
      this.assetPostfix = assetPostfix;
    }
  }
}
