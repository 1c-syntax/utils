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

/**
 * Слушатель прогресса скачивания ассета релиза.
 *
 * <p>Вызывается загрузчиком по мере вычитывания тела ответа: сначала один раз с нулём вычитанных
 * байт, затем после каждого прочитанного блока. Позволяет клиенту (например, IntelliJ-плагину)
 * рисовать прогресс-бар вместо неопределённого «крутящегося» индикатора.
 *
 * <p>Реализация может бросить {@link RuntimeException} (в частности, для отмены операции) —
 * исключение пробрасывается наружу через вызов загрузки, а частично скачанный файл удаляется.
 */
@FunctionalInterface
public interface DownloadProgressListener {

  /**
   * Слушатель, ничего не делающий — значение по умолчанию, когда прогресс не нужен.
   */
  DownloadProgressListener NONE = (bytesRead, totalBytes) -> {
    // no-op
  };

  /**
   * @param bytesRead  сколько байт ассета уже скачано
   * @param totalBytes полный размер ассета в байтах или {@code -1}, если сервер его не сообщил
   */
  void onProgress(long bytesRead, long totalBytes);
}
