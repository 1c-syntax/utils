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

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

@UtilityClass
public final class Absolute {

  public static URI uri(String uri) {
    return uri(URI.create(uri));
  }

  public static URI uri(URI uri) {
    return URI.create(uri.getScheme() + ":" + uri.getSchemeSpecificPart());
  }

  public static Path path(String path) {
    return path(Path.of(path));
  }

  public static Path path(URI uri) {
    return path(Path.of(uri(uri)));
  }

  public static Path path(Path path) {
    return path(path.toFile());
  }

  @SneakyThrows
  public static Path path(File file) {
    return file.getCanonicalFile().toPath().toAbsolutePath();
  }
}
