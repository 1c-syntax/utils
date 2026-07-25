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

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Хранилище значения с ленивым однократным вычислением и потокобезопасным доступом.
 *
 * <p>Значение вычисляется не в момент создания, а при первом обращении через
 * {@link #getOrCompute()} / {@link #getOrCompute(Supplier)} и кэшируется. Вычисление защищено
 * блокировкой и выполняется по схеме double-checked locking: конкурентные потоки, попавшие на
 * невычисленное значение, ждут единственного вычисления, а не запускают его повторно. Уже
 * вычисленное значение читается без блокировки через {@code volatile}-поле.
 *
 * <p>Вычисленное значение не может быть {@code null}: если {@link Supplier} вернёт {@code null},
 * будет брошено {@link NullPointerException}. Кэш можно сбросить через {@link #clear()},
 * после чего следующее обращение вычислит значение заново.
 *
 * @param <T> тип хранимого значения
 */
public final class Lazy<T> {

  private final Supplier<T> supplier;
  private final ReentrantLock lock;
  private volatile @Nullable T value;

  /**
   * Создаёт хранилище с собственной блокировкой.
   *
   * @param supplier поставщик значения по умолчанию, используемый {@link #getOrCompute()}
   */
  public Lazy(Supplier<T> supplier) {
    this(supplier, new ReentrantLock());
  }

  /**
   * Создаёт хранилище с внешней блокировкой. Общий {@link ReentrantLock} позволяет нескольким
   * экземплярам сериализовать свои вычисления на одном мониторе.
   *
   * @param supplier поставщик значения по умолчанию, используемый {@link #getOrCompute()}
   * @param lock     блокировка, под которой выполняется вычисление значения
   */
  public Lazy(Supplier<T> supplier, ReentrantLock lock) {
    // no need to initialize lazy-value
    this.supplier = supplier;
    this.lock = lock;
  }

  /**
   * Возвращает уже вычисленное значение, не запуская вычисление.
   *
   * @return закэшированное значение или {@code null}, если оно ещё не вычислено либо сброшено
   *         через {@link #clear()}
   */
  @Nullable
  public T get() {
    return value;
  }

  /**
   * Возвращает закэшированное значение, а при его отсутствии вычисляет его переданным поставщиком
   * и кэширует. Вычисление выполняется под блокировкой не более одного раза при конкурентном
   * доступе.
   *
   * @param supplier поставщик значения для этого вызова; должен вернуть не {@code null}
   * @return вычисленное (или ранее закэшированное) значение
   * @throws NullPointerException если поставщик вернул {@code null}
   */
  public T getOrCompute(Supplier<T> supplier) {
    final T result = value; // Just one volatile read
    if (result == null) {
      lock.lock();
      try {
        return maybeCompute(supplier);
      } finally {
        lock.unlock();
      }
    }
    return result;
  }

  /**
   * Возвращает закэшированное значение, а при его отсутствии вычисляет его поставщиком, переданным
   * в конструктор, и кэширует.
   *
   * @return вычисленное (или ранее закэшированное) значение
   * @throws NullPointerException если поставщик вернул {@code null}
   * @see #getOrCompute(Supplier)
   */
  public T getOrCompute() {
    return getOrCompute(supplier);
  }

  /**
   * Проверяет, вычислено ли значение.
   *
   * @return {@code true}, если значение уже вычислено и закэшировано
   */
  public boolean isPresent() {
    final T result = value;
    return result != null;
  }

  /**
   * Сбрасывает закэшированное значение. Следующее обращение через {@code getOrCompute} вычислит
   * его заново.
   */
  public void clear() {
    value = null;
  }

  private T maybeCompute(Supplier<T> supplier) {
    if (value == null) {
      requireNonNull(supplier);
      value = requireNonNull(supplier.get());
    }
    return requireNonNull(value);
  }
}
