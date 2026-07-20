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

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный JSON-парсер для ответов GitHub REST API — чтобы не тянуть Jackson/gson
 * в рантайм-замкнутость библиотеки (важно для встраивания в OSGi, см. issue #81).
 *
 * <p>Поддерживает весь синтаксис RFC 8259. Значения отображаются в {@link Map}
 * (объект, порядок ключей сохраняется), {@link List} (массив), {@link String},
 * {@link Long}/{@link Double} (число), {@link Boolean} и {@code null}.
 *
 * <p>Некорректный JSON приводит к {@link IOException}: парсер применяется только к сетевым
 * ответам, где повреждённые данные — та же ошибка обмена, что и обрыв соединения.
 */
final class Json {

  private final String text;
  private int pos;

  private Json(String text) {
    this.text = text;
  }

  /**
   * Разбирает строку с одним JSON-значением.
   *
   * @param text текст JSON
   * @return значение: {@code Map<String, Object>}, {@code List<Object>}, {@link String},
   *   {@link Long}, {@link Double}, {@link Boolean} или {@code null}
   * @throws IOException если текст не является корректным JSON
   */
  static @Nullable Object parse(String text) throws IOException {
    var parser = new Json(text);
    parser.skipWhitespace();
    var value = parser.readValue();
    parser.skipWhitespace();
    if (parser.pos < text.length()) {
      throw parser.error("Unexpected trailing characters");
    }
    return value;
  }

  private @Nullable Object readValue() throws IOException {
    return switch (peek()) {
      case '{' -> readObject();
      case '[' -> readArray();
      case '"' -> readString();
      case 't' -> readLiteral("true", Boolean.TRUE);
      case 'f' -> readLiteral("false", Boolean.FALSE);
      case 'n' -> readLiteral("null", null);
      default -> readNumber();
    };
  }

  private Map<String, Object> readObject() throws IOException {
    expect('{');
    var object = new LinkedHashMap<String, Object>();
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return object;
    }
    while (true) {
      skipWhitespace();
      var key = readString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      object.put(key, readValue());
      skipWhitespace();
      char next = peek();
      pos++;
      if (next == '}') {
        return object;
      }
      if (next != ',') {
        throw error("Expected ',' or '}' in object");
      }
    }
  }

  private List<@Nullable Object> readArray() throws IOException {
    expect('[');
    var array = new ArrayList<@Nullable Object>();
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return array;
    }
    while (true) {
      skipWhitespace();
      array.add(readValue());
      skipWhitespace();
      char next = peek();
      pos++;
      if (next == ']') {
        return array;
      }
      if (next != ',') {
        throw error("Expected ',' or ']' in array");
      }
    }
  }

  private String readString() throws IOException {
    expect('"');
    var builder = new StringBuilder();
    while (true) {
      char c = next();
      if (c == '"') {
        return builder.toString();
      }
      if (c == '\\') {
        builder.append(readEscape());
      } else if (c < 0x20) {
        throw error("Unescaped control character in string");
      } else {
        builder.append(c);
      }
    }
  }

  private char readEscape() throws IOException {
    char c = next();
    return switch (c) {
      case '"', '\\', '/' -> c;
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> readUnicodeEscape();
      default -> throw error("Invalid escape sequence '\\" + c + "'");
    };
  }

  private char readUnicodeEscape() throws IOException {
    if (pos + 4 > text.length()) {
      throw error("Unexpected end of unicode escape");
    }
    var hex = text.substring(pos, pos + 4);
    try {
      var code = Integer.parseInt(hex, 16);
      pos += 4;
      return (char) code;
    } catch (NumberFormatException e) {
      throw error("Invalid unicode escape '\\u" + hex + "'");
    }
  }

  private Number readNumber() throws IOException {
    var start = pos;
    if (peek() == '-') {
      pos++;
    }
    while (pos < text.length() && isNumberChar(text.charAt(pos))) {
      pos++;
    }
    var literal = text.substring(start, pos);
    try {
      if (literal.indexOf('.') < 0 && literal.indexOf('e') < 0 && literal.indexOf('E') < 0) {
        try {
          return Long.parseLong(literal);
        } catch (NumberFormatException outOfLongRange) {
          return Double.parseDouble(literal);
        }
      }
      return Double.parseDouble(literal);
    } catch (NumberFormatException e) {
      throw error("Invalid number '" + literal + "'");
    }
  }

  private static boolean isNumberChar(char c) {
    return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
  }

  private @Nullable Object readLiteral(String literal, @Nullable Object value) throws IOException {
    if (!text.startsWith(literal, pos)) {
      throw error("Invalid literal");
    }
    pos += literal.length();
    return value;
  }

  private void skipWhitespace() {
    while (pos < text.length()) {
      char c = text.charAt(pos);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return;
      }
      pos++;
    }
  }

  private char peek() throws IOException {
    if (pos >= text.length()) {
      throw error("Unexpected end of JSON");
    }
    return text.charAt(pos);
  }

  private char next() throws IOException {
    char c = peek();
    pos++;
    return c;
  }

  private void expect(char expected) throws IOException {
    if (next() != expected) {
      pos--;
      throw error("Expected '" + expected + "'");
    }
  }

  private IOException error(String message) {
    return new IOException("Malformed JSON at position " + pos + ": " + message);
  }
}
