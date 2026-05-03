package com.raiden.mqtt;

import org.jetbrains.annotations.NotNull;

public interface MessagePublisher {
  boolean publish(@NotNull String json);
}
