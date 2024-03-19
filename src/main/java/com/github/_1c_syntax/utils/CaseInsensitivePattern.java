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
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pattern helper
 */
@UtilityClass
public class CaseInsensitivePattern {

  /**
   * Compiles the given regular expression into a pattern with CASE_INSENSITIVE and UNICODE_CASE
   * flags.
   *
   * @param regex The expression to be compiled
   * @return the given regular expression compiled into a pattern with the given flags
   * @throws IllegalArgumentException If bit values other than those corresponding to the defined
   *                                  match flags are set in {@code flags}
   * @throws PatternSyntaxException   If the expression's syntax is invalid
   */
  public static @NonNull Pattern compile(@NonNull String regex) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

}
