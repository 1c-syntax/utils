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
/**
 * Общие утилиты java-проектов команды 1c-syntax.
 *
 * <p>Небольшие независимые помощники, переиспользуемые в BSL Language Server и смежных проектах:
 * каноникализация путей/URI ({@link com.github._1c_syntax.utils.Absolute}), ленивое вычисление
 * ({@link com.github._1c_syntax.utils.Lazy}), интернирование значений
 * ({@link com.github._1c_syntax.utils.GenericInterner},
 * {@link com.github._1c_syntax.utils.StringInterner}) и компиляция регистронезависимых
 * регулярных выражений ({@link com.github._1c_syntax.utils.CaseInsensitivePattern}).
 *
 * <p>Пакет помечен {@link org.jspecify.annotations.NullMarked}: типы считаются non-null, если
 * явно не аннотированы {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package com.github._1c_syntax.utils;

import org.jspecify.annotations.NullMarked;
