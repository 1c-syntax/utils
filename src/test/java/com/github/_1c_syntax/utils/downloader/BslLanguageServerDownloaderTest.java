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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
  }

  @Test
  void installedVersionIsEmptyWhenNothingInstalled(@TempDir Path installDir) {
    var downloader = new BslLanguageServerDownloader(installDir);
    assertThat(downloader.installedVersion()).isEmpty();
    assertThat(downloader.installedBinary()).isEmpty();
  }

  @Test
  void installedVersionIsReadFromServerInfo(@TempDir Path installDir) throws IOException {
    writeServerInfo(installDir, "1.0.2");

    var downloader = new BslLanguageServerDownloader(installDir);
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

    var downloader = new BslLanguageServerDownloader(installDir);
    assertThat(downloader.installedBinary()).contains(binary);
  }

  private static void writeServerInfo(Path installDir, String version) throws IOException {
    Files.createDirectories(installDir);
    Files.writeString(installDir.resolve("SERVER-INFO"),
      "version=" + version + System.lineSeparator() + "lastUpdate=0" + System.lineSeparator());
  }
}
