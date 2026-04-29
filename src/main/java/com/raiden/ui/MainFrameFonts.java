package com.raiden.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

final class MainFrameFonts {

  private static final String REGULAR_FONT_RESOURCE = "/fonts/Maple-Regular.ttf";
  private static final String BOLD_FONT_RESOURCE = "/fonts/Maple-Bold.ttf";
  private static final Font REGULAR_FONT = loadFont(REGULAR_FONT_RESOURCE);
  private static final Font BOLD_FONT = loadFont(BOLD_FONT_RESOURCE);

  private MainFrameFonts() {}

  @NotNull
  static Font plain(float size) {
    return REGULAR_FONT.deriveFont(Font.PLAIN, size);
  }

  @NotNull
  static Font bold(float size) {
    return BOLD_FONT.deriveFont(Font.BOLD, size);
  }

  @NotNull
  private static Font loadFont(@NotNull String resourcePath) {
    try (InputStream stream = MainFrameFonts.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Maple 字体资源不存在：" + resourcePath);
      }
      Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      return font;
    }
    catch (FontFormatException | IOException e) {
      throw new IllegalStateException("加载 Maple 字体失败：" + resourcePath, e);
    }
  }
}
