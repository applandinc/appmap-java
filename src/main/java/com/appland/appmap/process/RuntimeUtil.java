package com.appland.appmap.process;

/**
 * Runtime utility methods to be called from hook-generated bytecode.
 */
public class RuntimeUtil {
  /**
   * Convert a primitive byte to a Byte Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(byte value) {
    return new Byte(value);
  }

  /**
   * Convert a primitive char to a Character Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(char value) {
    return new Character(value);
  }

  /**
   * Convert a primitive short to a Short Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(short value) {
    return new Short(value);
  }

  /**
   * Convert a primitive long to a Long Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(long value) {
    return new Long(value);
  }

  /**
   * Convert a primitive float to a Float Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(float value) {
    return new Float(value);
  }

  /**
   * Convert a primitive double to a Double Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(double value) {
    return new Double(value);
  }

  /**
   * Convert a primitive int to an Integer Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(int value) {
    return new Integer(value);
  }

  /**
   * Convert a primitive boolean to a Boolean Object.
   * @param value The primitive value
   * @return The value as an Object
   */
  public static Object boxValue(boolean value) {
    return new Boolean(value);
  }

  /**
   * No-op.
   */
  public static Object boxValue(Object value) {
    return value;
  }
}
