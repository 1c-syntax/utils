package com.github._1c_syntax.utils;


import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class CaseInsensitivePattern {

  public Pattern compile(String regex) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

}
