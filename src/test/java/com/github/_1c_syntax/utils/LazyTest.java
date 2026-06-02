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

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazyTest {

  @Test
  void getOrComputeReturnsValue() {
    var lazy = new Lazy<>(() -> "value");
    assertThat(lazy.getOrCompute()).isEqualTo("value");
    assertThat(lazy.isPresent()).isTrue();
  }

  @Test
  void getOrComputeCachesValue() {
    var counter = new int[]{0};
    var lazy = new Lazy<>(() -> {
      counter[0]++;
      return "value";
    });
    lazy.getOrCompute();
    lazy.getOrCompute();
    lazy.getOrCompute();
    assertThat(counter[0]).isEqualTo(1);
  }

  @Test
  void supplierExceptionReleasesLock() {
    // Регрессия: до фикса unlock() стоял после maybeCompute() без try/finally,
    // и любое RuntimeException из supplier'а оставляло lock захваченным
    // навсегда — следующий поток вешал getOrCompute() либо документ-сервис
    // (где Lazy шарит lock с externalным ReentrantLock).
    var lock = new ReentrantLock();
    var lazy = new Lazy<String>(() -> {
      throw new IllegalStateException("boom");
    }, lock);

    assertThatThrownBy(lazy::getOrCompute).isInstanceOf(IllegalStateException.class);
    assertThat(lock.isLocked())
      .as("lock должен быть отпущен после RuntimeException в supplier'е")
      .isFalse();
    assertThat(lock.getHoldCount()).isZero();
  }

  @Test
  void supplierExceptionReleasesSharedLockMultipleAttempts() {
    // Дважды бросаем, потом успешно вычисляем — lock должен корректно
    // освобождаться каждый раз, и финальный getOrCompute должен пройти.
    var lock = new ReentrantLock();
    var attempt = new int[]{0};
    var lazy = new Lazy<String>(() -> {
      attempt[0]++;
      if (attempt[0] < 3) {
        throw new IllegalStateException("attempt " + attempt[0]);
      }
      return "ok";
    }, lock);

    assertThatThrownBy(lazy::getOrCompute).isInstanceOf(IllegalStateException.class);
    assertThat(lock.isLocked()).isFalse();
    assertThatThrownBy(lazy::getOrCompute).isInstanceOf(IllegalStateException.class);
    assertThat(lock.isLocked()).isFalse();
    assertThat(lazy.getOrCompute()).isEqualTo("ok");
    assertThat(lock.isLocked()).isFalse();
  }

  @Test
  void clearAllowsRecompute() {
    var counter = new int[]{0};
    var lazy = new Lazy<>(() -> {
      counter[0]++;
      return "v" + counter[0];
    });
    assertThat(lazy.getOrCompute()).isEqualTo("v1");
    lazy.clear();
    assertThat(lazy.isPresent()).isFalse();
    assertThat(lazy.getOrCompute()).isEqualTo("v2");
  }

  @Test
  void getReturnsNullBeforeCompute() {
    var lazy = new Lazy<>(() -> "value");
    assertThat(lazy.get()).isNull();
    lazy.getOrCompute();
    assertThat(lazy.get()).isEqualTo("value");
  }
}
