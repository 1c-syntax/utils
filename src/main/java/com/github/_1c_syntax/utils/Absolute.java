/*
 * This file is a part of 1c-syntax utils.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.utils;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Методы получения абсолютного пути файла с учетом различных особенностей
 */
@UtilityClass
public final class Absolute {

  public static URI uri(@NonNull String uri) {
    try {
      var url = new URL(uri);
      var decodedPath = URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8);
      var decodedUri = new URI(
        url.getProtocol(),
        url.getUserInfo(),
        url.getHost(),
        url.getPort(),
        decodedPath,
        url.getQuery(),
        url.getRef()
      );

      return checkFileAuthorityAndReturnURI(decodedUri);
    } catch (MalformedURLException | URISyntaxException e) {
      return uri(URI.create(uri));
    }
  }

  public static URI uri(@NonNull URI uri) {
    var decodedUri = URI.create(uri.getScheme() + ":" + encodePath(uri.getSchemeSpecificPart()));

    return checkFileAuthorityAndReturnURI(decodedUri);
  }

  public static URI uri(@NonNull File file) {
    return uri(path(file).toUri());
  }

  public static Path path(@NonNull String path) {
    return path(Path.of(path));
  }

  public static Path path(@NonNull URI uri) {
    return path(Path.of(uri(uri)));
  }

  public static Path path(@NonNull Path path) {
    return path(path.toFile());
  }

  @SneakyThrows
  public static Path path(@NonNull File file) {
    return file.getCanonicalFile().toPath().toAbsolutePath();
  }

  private static String encodePath(@NonNull String path) {
    return path
      .replace(" ", "%20")
      .replace("#", "%23")
      .replace("[", "%91")
      .replace("]", "%93")
      .replace("?", "%3F")
      .replace("{", "%7B")
      .replace("}", "%7D")
      .replace(":", "%3A")
      .replace("\"", "%22")
      .replace("\\", "%5C")
      ;
  }

  private static URI checkFileAuthorityAndReturnURI(@NonNull URI uri) {
    if ("file".equals(uri.getScheme()) && uri.getAuthority() == null) {
      return path(new File(uri)).toUri();
    }

    return uri;
  }
}
