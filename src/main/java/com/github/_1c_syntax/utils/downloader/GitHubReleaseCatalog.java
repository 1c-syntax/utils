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
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.HttpClientGitHubConnector;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link ReleaseCatalog} поверх GitHub API. Ходит в репозиторий {@value #REPOSITORY} за последним
 * релизом канала через переданный {@link HttpClient}.
 */
final class GitHubReleaseCatalog implements ReleaseCatalog {

  private static final String REPOSITORY = "1c-syntax/bsl-language-server";

  private final HttpClient httpClient;
  private final @Nullable String token;

  /**
   * @param httpClient HTTP-клиент, через который github-api обращается к GitHub
   * @param token      GitHub OAuth-токен для обхода лимитов анонимного API; может быть {@code null}
   */
  GitHubReleaseCatalog(HttpClient httpClient, @Nullable String token) {
    this.httpClient = httpClient;
    this.token = token;
  }

  @Override
  public ReleaseInfo latestRelease(BslLanguageServerReleaseChannel channel) throws IOException {
    var builder = new GitHubBuilder().withConnector(new HttpClientGitHubConnector(httpClient));
    if (token != null && !token.isBlank()) {
      builder.withOAuthToken(token);
    }
    GitHub github = builder.build();

    GHRepository repository = github.getRepository(REPOSITORY);

    GHRelease release;
    if (channel == BslLanguageServerReleaseChannel.PRERELEASE) {
      // GitHub отдаёт релизы newest-first — берём первый не-draft.
      release = repository.listReleases().toList().stream()
        .filter(it -> !it.isDraft())
        .findFirst()
        .orElse(null);
    } else {
      release = repository.getLatestRelease();
    }

    if (release == null) {
      throw new IOException(
        "Repository " + REPOSITORY + " has no suitable releases for channel " + channel);
    }

    var assetUrls = new HashMap<String, String>();
    for (GHAsset asset : release.listAssets().toList()) {
      assetUrls.putIfAbsent(asset.getName(), asset.getBrowserDownloadUrl());
    }
    return new ReleaseInfo(release.getTagName(), Map.copyOf(assetUrls));
  }
}
