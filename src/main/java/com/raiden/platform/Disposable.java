package com.raiden.platform;

@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface Disposable {
  void dispose();

  interface Default extends Disposable {
    @Override
    default void dispose() {}
  }

  interface Parent extends Disposable {
    void beforeTreeDispose();
  }
}
