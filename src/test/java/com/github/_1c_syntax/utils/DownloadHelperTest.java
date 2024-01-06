/*
 * This file is a part of 1c-syntax utils.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.utils;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHAsset;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DownloadHelperTest {

  private final Path BASE_PATH = Path.of("build", "fixture");

  @Test
  public void test_getLatestRelease() {
    prepareFolder(BASE_PATH);
    var token = System.getenv("GITHUB_TOKEN");

    var ghAsset = DownloadHelper.getLatestRelease(token, DownloadHelper.SUPPLY_FOR_WIN, BASE_PATH);
    checkGHAsset(ghAsset);

    ghAsset = DownloadHelper.getLatestRelease(token, DownloadHelper.SUPPLY_FOR_LINUX, BASE_PATH);
    checkGHAsset(ghAsset);

    ghAsset = DownloadHelper.getLatestRelease(token, DownloadHelper.SUPPLY_FOR_MAC, BASE_PATH, true);
    checkGHAsset(ghAsset);
  }

  void checkGHAsset(Optional<GHAsset> ghAsset) {
    assertThat(ghAsset).isPresent();
    var file = Path.of(BASE_PATH.toString(), ghAsset.get().getName()).toFile();
    assertThat(file).exists();
  }


  private void prepareFolder(Path path) {
    path.toFile().mkdir();
  }

}
