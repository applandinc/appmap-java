package com.appland.appmap.process.conditions;

import com.appland.appmap.config.AppMapConfig;
import com.appland.appmap.config.Properties;
import javassist.CtBehavior;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static com.appland.appmap.util.StringUtil.canonicalName;

/**
 * RecordCondition checks if the behavior should be recorded due to its inclusion in the
 * {@link Properties#Records}.
 * @see Properties#Records
 */
public abstract class RecordCondition implements Condition {

  /**
   * Determines whether the given behavior should be recorded due to its inclusion in the
   * {@link Properties#Records}.
   * @param behavior A behavior being loaded
   * @return {@code true} if the behavior should be recorded
   * @see Properties#Records
   */
  public static Boolean match(CtBehavior behavior) {
    if (behavior.getDeclaringClass().getName().startsWith("java.lang")) {
      return false;
    }

    if (!Modifier.isPublic(behavior.getModifiers())) {
      return false;
    }

    if (behavior.getMethodInfo().getLineNumber(0) < 0) {
      // likely a runtime generated method
      return false;
    }
    String className = behavior.getDeclaringClass().getName(),
            methodName = behavior.getMethodInfo().getName();
    boolean isStatic = Modifier.isStatic(behavior.getModifiers());

    return Arrays.stream(Properties.getRecords()).anyMatch(
            record ->  record.equals(canonicalName(className, methodName, isStatic))) &&
          AppMapConfig.get().includes(className, methodName, isStatic);
  }
}
