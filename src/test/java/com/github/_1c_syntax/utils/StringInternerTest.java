package com.github._1c_syntax.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringInternerTest {

  private StringInterner interner;

  @BeforeEach
  public void init() {
    interner = new StringInterner();
  }

  @Test
  void testIntern() {
    //given
    String s1 = new String("1");
    String s2 = new String("1");

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
    String s1 = new String("1");
    String s2 = new String("1");

    interner.intern(s1);

    // when
    interner.clear();

    // when
    var intern = interner.intern(s2);

    // then
    assertEquals(s2, intern);
  }
}