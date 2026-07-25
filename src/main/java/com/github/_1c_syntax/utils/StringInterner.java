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

import org.jspecify.annotations.Nullable;

/**
 * Интернер строк — {@link GenericInterner} для {@link String}, дополнительно принимающий
 * {@code null}.
 *
 * <p>В отличие от базового интернера, {@code null} не сохраняется, а нормализуется в пустую
 * строку, поэтому метод {@link #intern(String)} никогда не возвращает {@code null}. Для непустых
 * строк поведение совпадает с {@link GenericInterner}.
 */
public class StringInterner extends GenericInterner<String> {

  /**
   * Создаёт пустой интернер строк.
   */
  public StringInterner() {
    // no additional state
  }

  /**
   * Возвращает канонический экземпляр переданной строки; для {@code null} возвращает пустую строку.
   *
   * @param object интернируемая строка либо {@code null}
   * @return канонический экземпляр строки, либо {@code ""}, если передан {@code null}
   */
  @Override
  public String intern(@Nullable String object) {
    if (object == null) {
      return "";
    }
    return super.intern(object);
  }
}
