package com.raiden.platform;

public interface CheckedDisposable extends Disposable {
  boolean isDisposed();
}
