package com.instashare.instasharecore.util;

import org.testcontainers.containers.MongoDBContainer;

public class MyMongoDbContainer extends MongoDBContainer {

  private static final String IMAGE_TAG = "mongo:5.0.10";

  private static MyMongoDbContainer container;

  private MyMongoDbContainer() {
    super(IMAGE_TAG);
  }

  public static MyMongoDbContainer getInstance() {
    if (container == null) {
      container = new MyMongoDbContainer();
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
