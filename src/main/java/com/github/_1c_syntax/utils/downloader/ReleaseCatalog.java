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

import java.io.IOException;
import java.util.Map;

/**
 * Источник сведений о релизах BSL Language Server. Абстрагирует получение последнего релиза от
 * конкретного транспорта (GitHub API), чтобы {@link BslLanguageServerDownloader} можно было
 * тестировать без обращения к сети.
 */
public interface ReleaseCatalog {

  /**
   * Возвращает сведения о последнем релизе выбранного канала.
   *
   * @param channel канал релизов (стабильный / pre-release)
   * @return версия релиза и ссылки на его ассеты
   * @throws IOException если релизы недоступны или подходящего релиза нет
   */
  ReleaseInfo latestRelease(BslLanguageServerReleaseChannel channel) throws IOException;

  /**
   * Сведения о релизе, нужные загрузчику.
   *
   * @param version           тег/версия релиза (может начинаться с {@code v})
   * @param assetDownloadUrls карта «имя ассета → URL для скачивания»
   */
  record ReleaseInfo(String version, Map<String, String> assetDownloadUrls) {
  }
}
