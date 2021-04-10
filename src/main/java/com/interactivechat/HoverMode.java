package com.interactivechat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HoverMode {
  HIGHLIGHT("Highlight"),
  UNDERLINE("Underline"),
  OFF("Off");

  private final String name;

  @Override
  public String toString() {
    return name;
  }
}