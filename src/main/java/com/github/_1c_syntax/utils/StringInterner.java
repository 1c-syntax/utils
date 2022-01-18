package com.github._1c_syntax.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StringInterner {

  private final Map<String, String> map = new ConcurrentHashMap<>();

  public String intern(String string) {
    String exist = map.putIfAbsent(string, string);
    return (exist == null) ? string : exist;
  }

  public void clear() {
    map.clear();
  }
}
