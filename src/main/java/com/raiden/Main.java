package com.raiden;

import com.formdev.flatlaf.FlatLightLaf;
import com.raiden.ui.MainFrame;

import javax.swing.*;

public final class Main {

  public static void main(String[] args) {
    FlatLightLaf.setup();

    SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
  }
}
