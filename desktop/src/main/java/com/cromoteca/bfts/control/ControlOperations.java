package com.cromoteca.bfts.control;

/**
 * Defines operations that can be executed locally by the JVM owning the control port.
 */
public interface ControlOperations {
  void startAllLocal();
  void startLocal(String name);
  void stopAllLocal();
  void stopLocal(String name);
  void fastLocal();
  void niceLocal();
  ControlStatus status(int port);
}
