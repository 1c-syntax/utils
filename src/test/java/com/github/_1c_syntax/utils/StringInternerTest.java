/*
 * This file is a part of 1c-syntax utils.
 *
 * Copyright (c) 2018-2025
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringInternerTest {

  private StringInterner interner;

  @BeforeEach
  public void init() {
    interner = new StringInterner();
  }

  @Test
  void testIntern() {
    //given
    String s1 = "1";
    String s2 = "1";

    // when
    var intern1 = interner.intern(s1);

    // then
    assertEquals(s1, intern1);

    // when
    var intern2 = interner.intern(s2);

    // then
    assertEquals(s1, intern2);
  }

  @Test
  void testClear() {

    //given
    String s1 = "1";
    String s2 = "1";

    interner.intern(s1);

    // when
    interner.clear();

    // when
    var intern = interner.intern(s2);

    // then
    assertEquals(s2, intern);
  }
}