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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest {

  @Test
  void parsesScalars() throws IOException {
    assertThat(Json.parse("\"text\"")).isEqualTo("text");
    assertThat(Json.parse("42")).isEqualTo(42L);
    assertThat(Json.parse("-7")).isEqualTo(-7L);
    assertThat(Json.parse("3.5")).isEqualTo(3.5d);
    assertThat(Json.parse("1e3")).isEqualTo(1000d);
    assertThat(Json.parse("true")).isEqualTo(Boolean.TRUE);
    assertThat(Json.parse("false")).isEqualTo(Boolean.FALSE);
    assertThat(Json.parse("null")).isNull();
  }

  @Test
  void parsesNumberOutOfLongRangeAsDouble() throws IOException {
    assertThat(Json.parse("123456789012345678901234567890"))
      .isEqualTo(123456789012345678901234567890d);
  }

  @Test
  void parsesStringEscapes() throws IOException {
    assertThat(Json.parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\"")).isEqualTo("a\"b\\c/d\b\f\n\r\t");
    assertThat(Json.parse("\"\\u0416\\u045E\"")).isEqualTo("Жў");
    // суррогатная пара
    assertThat(Json.parse("\"\\uD83D\\uDE00\"")).isEqualTo("😀");
  }

  @Test
  void parsesObjectsAndArrays() throws IOException {
    var value = Json.parse("""
      {
        "tag_name": "v1.2.3",
        "draft": false,
        "assets": [
          {"name": "a.zip", "size": 100},
          {"name": "b.zip", "size": 200.5}
        ],
        "empty_object": {},
        "empty_array": []
      }
      """);

    assertThat(value).isEqualTo(Map.of(
      "tag_name", "v1.2.3",
      "draft", false,
      "assets", List.of(
        Map.of("name", "a.zip", "size", 100L),
        Map.of("name", "b.zip", "size", 200.5d)),
      "empty_object", Map.of(),
      "empty_array", List.of()));
  }

  @Test
  void parsesNestedArrays() throws IOException {
    assertThat(Json.parse("[[1, 2], [], [null, true]]"))
      .isEqualTo(List.of(List.of(1L, 2L), List.of(), java.util.Arrays.asList(null, true)));
  }

  @Test
  void rejectsMalformedJson() {
    var samples = List.of(
      "",
      "   ",
      "{",
      "[1, 2",
      "{\"a\" 1}",
      "{\"a\": 1,}",
      "{\"a\": 1 \"b\": 2}",
      "[1 2]",
      "\"unterminated",
      "\"bad escape \\x\"",
      "\"bad unicode \\u12GX\"",
      "\"truncated unicode \\u12",
      "tru",
      "nul",
      "01a",
      "--1",
      "1.2.3",
      "{} extra",
      "42 43");

    for (var malformed : samples) {
      assertThatThrownBy(() -> Json.parse(malformed))
        .as("Json.parse(%s)", malformed)
        .isInstanceOf(IOException.class);
    }
  }

  @Test
  void rejectsUnescapedControlCharacterInString() {
    assertThatThrownBy(() -> Json.parse("\"line\nbreak\"")).isInstanceOf(IOException.class);
  }
}
