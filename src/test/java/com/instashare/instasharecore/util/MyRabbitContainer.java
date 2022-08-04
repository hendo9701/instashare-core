package com.instashare.instasharecore.util;

import org.testcontainers.containers.RabbitMQContainer;

public class MyRabbitContainer extends RabbitMQContainer {
  private static final String IMAGE_TAG = "rabbitmq:3";

  private static MyRabbitContainer container;

  private MyRabbitContainer() {
    super(IMAGE_TAG);
  }

  public static MyRabbitContainer getInstance() {
    if (container == null) {
      container = new MyRabbitContainer();
    }
    return container;
  }

  @Override
  public void start() {
    super.start();
  }

  @Override
  public void stop() {
    // do nothing, JVM handles shut down
  }
}
