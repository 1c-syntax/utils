/*
 * This file is a part of 1c-syntax utils.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AbsoluteTest {

  @Test
  void testUriFromFile() {
    // given
    var file = new File("/fake.bsl");

    // when
    var uriFromUri = Absolute.uri(file.toURI());
    var uriFromFile = Absolute.uri(file);

    // then
    assertThat(uriFromUri).isEqualTo(uriFromFile);
    assertThat(uriFromFile).hasScheme("file");
    assertThat(uriFromFile.getPath()).endsWith("fake.bsl");
  }

  @Test
  void testUriFromString() {
    // given
    var uriString = "file:///fake.bsl";

    // when
    var uriFromString = Absolute.uri(uriString);
    var uriFromFile = Absolute.uri(new File(URI.create(uriString)));

    // then
    assertThat(uriFromString).isEqualTo(uriFromFile);
    assertThat(uriFromFile).hasScheme("file");
    assertThat(uriFromFile.getPath()).endsWith("fake.bsl");
  }

  @Test
  void testUNCURI() {
    // given
    var uriString = "file://server/c%24/fake.bsl";

    // when
    var uri = Absolute.uri(uriString);

    // then
    assertThat(uri)
      .hasScheme("file")
      .hasHost("server")
    ;
    assertThat(uri.getPath())
      .endsWith("fake.bsl")
      .contains("$")
    ;
  }

  @Test
  void testUNCWithPortURI() {
    // given
    var uriString = "file://server:1234/c%24/fake.bsl";

    // when
    var uri = Absolute.uri(uriString);

    // then
    assertThat(uri)
      .hasScheme("file")
      .hasHost("server")
      .hasPort(1234)
    ;
    assertThat(uri.getPath())
      .endsWith("fake.bsl")
      .contains("$")
    ;
  }

  @Test
  void testUriFromUntitledSchema() {
    // given
    var uriString = "untitled:///fake.bsl";

    // when
    var uri = Absolute.uri(uriString);

    // then
    assertThat(uri).hasScheme("untitled");
    assertThat(uri.getPath()).endsWith("fake.bsl");
  }

  @Test
  void testUriFromUntitledFromVSC() {
    // given
    var uriString = "untitled:Untitled-1";

    // when
    var uri = Absolute.uri(uriString);

    // then
    assertThat(uri).hasScheme("untitled");
  }

  @Test
  void testUriFromStringWithSpaces() {
    // given
    var uriString = "file:///fake%20path/fake.bsl";

    // when
    var uri = Absolute.uri(uriString);

    // then
    assertThat(uri.getPath()).endsWith("fake.bsl");
  }

  @Test
  void testUriFromStringWithBrackets() {
    // given
    var uriString = "file://server/fake%20path_кириллица/[some]fake.bsl";

    // when
    var uri = Absolute.uri(uriString);

    // then
    assertThat(uri.toString()).doesNotContain("[");
    assertThat(uri.toString()).doesNotContain("]");
  }
}