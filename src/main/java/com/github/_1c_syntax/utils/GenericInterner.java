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
 * Потокобезопасный интернер значений произвольного типа.
 *
 * <p>Хранит по одному каноническому экземпляру для каждого класса эквивалентности
 * (по {@code equals}/{@code hashCode}) и возвращает его для всех равных значений. Позволяет
 * заменить множество равных, но разных по ссылке объектов на один и тем самым сократить
 * потребление памяти, а для потребителей — сравнивать значения по ссылке ({@code ==}).
 *
 * <p>Кэш не имеет ограничения по размеру и не вытесняет записи автоматически: интернированные
 * значения удерживаются до явного {@link #clear()}. Реализация основана на
 * {@link ConcurrentHashMap} и безопасна для конкурентного использования.
 *
 * @param <T> тип интернируемых значений
 */
public class GenericInterner<T> {

  private final Map<T, T> map = new ConcurrentHashMap<>();

  /**
   * Создаёт пустой интернер.
   */
  public GenericInterner() {
    // no state to initialize beyond the backing map
  }

  /**
   * Возвращает канонический экземпляр, равный переданному значению. Если равное значение ещё не
   * интернировано, каноническим становится переданный объект.
   *
   * @param object интернируемое значение
   * @return ранее сохранённый экземпляр, равный {@code object}, либо сам {@code object},
   *         если равного ещё не было
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
