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
package com.github._1c_syntax.utils;

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
 * Приведение файловых путей и URI к каноническому абсолютному виду.
 *
 * <p>Утилита сглаживает различия в записи одного и того же файла, из-за которых он иначе выглядел
 * бы как разные ресурсы: относительные пути разворачиваются в абсолютные, символические ссылки и
 * сегменты {@code .}/{@code ..} схлопываются через каноникализацию файла, регистр и разделители
 * приводятся к виду файловой системы. Для {@code file:}-URI дополнительно нормализуется
 * процентное кодирование спецсимволов и восстанавливается authority, чтобы форма URI совпадала с
 * той, что отдаёт JDK для канонического файла.
 *
 * <p>Благодаря этому URI/путь можно использовать как стабильный ключ (например, документа в
 * рабочей области), не опасаясь, что тот же файл придёт в другой записи.
 */
@UtilityClass
public final class Absolute {

  /**
   * Разбирает строковый URI и приводит его к каноническому абсолютному виду.
   *
   * <p>Если строка не является корректным URL, она интерпретируется как {@link URI} и обрабатывается
   * через {@link #uri(URI)}.
   *
   * @param uri строковое представление URI
   * @return канонический абсолютный URI
   */
  public static URI uri(String uri) {
    try {
      var url = new URL(uri.replace("+", "%2B").replace("%%", "%25%"));
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

  /**
   * Приводит URI к каноническому абсолютному виду, нормализуя процентное кодирование пути и,
   * для {@code file:}-URI без authority, восстанавливая её через каноникализацию файла.
   *
   * @param uri исходный URI
   * @return канонический абсолютный URI
   */
  public static URI uri(URI uri) {
    var decodedUri = URI.create(uri.getScheme() + ":" + encodePath(uri.getSchemeSpecificPart()));

    return checkFileAuthorityAndReturnURI(decodedUri);
  }

  /**
   * Возвращает канонический абсолютный URI файла.
   *
   * @param file исходный файл
   * @return канонический абсолютный {@code file:}-URI
   */
  public static URI uri(File file) {
    return uri(path(file).toUri());
  }

  /**
   * Возвращает канонический абсолютный путь по его строковому представлению.
   *
   * @param path строковое представление пути
   * @return канонический абсолютный путь
   */
  public static Path path(String path) {
    return path(Path.of(path));
  }

  /**
   * Возвращает канонический абсолютный путь к файлу, на который указывает URI.
   *
   * @param uri исходный URI
   * @return канонический абсолютный путь
   */
  public static Path path(URI uri) {
    return path(Path.of(uri(uri)));
  }

  /**
   * Возвращает канонический абсолютный путь для переданного {@link Path}.
   *
   * @param path исходный путь
   * @return канонический абсолютный путь
   */
  public static Path path(Path path) {
    return path(path.toFile());
  }

  /**
   * Возвращает канонический абсолютный путь файла: разворачивает символические ссылки и
   * сегменты {@code .}/{@code ..}, приводит запись к виду файловой системы.
   *
   * @param file исходный файл
   * @return канонический абсолютный путь
   */
  @SneakyThrows
  public static Path path(File file) {
    return file.getCanonicalFile().toPath().toAbsolutePath();
  }

  private static String encodePath(String path) {
    return path
      .replace("%", "%25")
      .replace(" ", "%20")
      .replace("#", "%23")
      .replace("+", "%2B")
      .replace(",", "%2C")
      .replace("[", "%5B")
      .replace("]", "%5D")
      .replace("?", "%3F")
      .replace("{", "%7B")
      .replace("}", "%7D")
      .replace(":", "%3A")
      .replace("\"", "%22")
      .replace("\\", "%5C")
      .replace("^", "%5E")
      ;
  }

  private static URI checkFileAuthorityAndReturnURI(URI uri) {
    if ("file".equals(uri.getScheme()) && uri.getAuthority() == null) {
      return path(new File(uri)).toUri();
    }

    return uri;
  }
}
