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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация универсального интернера
 */
public class GenericInterner<T> {

  private final Map<T, T> map = new ConcurrentHashMap<>();

  /**
   * Метод интернирования значения
   *
   * @param object Интернируемый объект
   * @return значение из кеша
   */
  public T intern(T object) {
    T exist = map.putIfAbsent(object, object);
    return (exist == null) ? object : exist;
  }

  /**
   * Очистка кеша интернера
   */
  public void clear() {
    map.clear();
  }
}
