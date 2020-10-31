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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

@UtilityClass
@Slf4j
public class DownloadHelper {

  private final String REPO_PATH = "1c-syntax/bsl-language-server";
  private final String CONTENT_TYPE = "application/zip";
  private final int CONNECTION_TIMEOUT = 900;

  public final String SUPPLY_FOR_WIN = "win";
  public final String SUPPLY_FOR_MAC = "mac";
  public final String SUPPLY_FOR_LINUX = "nix";

  public Optional<GHAsset> getLatestRelease(String token, String supplyVariant, Path pathForSave) {
    return getLatestRelease(token, supplyVariant, pathForSave, false);
  }

  public Optional<GHAsset> getLatestRelease(String token, String supplyVariant, Path pathForSave, boolean prerelease) {
    Optional<GHAsset> ghAsset;
    try {
      GitHub github = GitHub.connectUsingOAuth(token);
      ghAsset = Optional.ofNullable(github.getRepository(REPO_PATH))
        .flatMap(repo -> {
          try {
            return getAssetsFromRelease(repo, supplyVariant, prerelease);
          } catch (IOException e) {
            LOGGER.error("Error finding asset from release", e);
            return Optional.empty();
          }
        });

    } catch (IOException e) {
      LOGGER.error("Error finding asset from release", e);
      ghAsset = Optional.empty();
    }
    if (ghAsset.isPresent() && !downloadFromAsset(ghAsset.get(), pathForSave)) {
      return Optional.empty();
    }
    return ghAsset;
  }

  private boolean downloadFromAsset(GHAsset ghAsset, Path path) {
    try {
      var file = Path.of(path.toString(), ghAsset.getName()).toFile();
      FileUtils.copyURLToFile(new URL(ghAsset.getBrowserDownloadUrl()), file, CONNECTION_TIMEOUT, 0);
    } catch (IOException e) {
      LOGGER.error("An error occurred while loading", e);
      return false;
    }
    return true;
  }

  private Optional<GHAsset> getAssetsFromRelease(GHRepository repo, String supplyVariant, boolean prerelease) throws IOException {
    var endsName = supplyVariant + ".zip";
    var release = getLatestRelease(repo, prerelease);
    if (release.isPresent()) {
      return release.get().getAssets().stream()
        .filter(ghAsset -> ghAsset.getContentType().equals(CONTENT_TYPE)
          && ghAsset.getName().endsWith(endsName))
        .findAny();
    }
    return Optional.empty();
  }

  private Optional<GHRelease> getLatestRelease(GHRepository repo, boolean prerelease) throws IOException {
    return repo.listReleases().toList().stream()
      .filter(ghRelease -> prerelease || !ghRelease.isPrerelease())
      .limit(1)
      .findAny();
  }

}