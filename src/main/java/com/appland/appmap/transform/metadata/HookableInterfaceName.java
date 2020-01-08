package com.appland.appmap.transform.metadata;

import com.appland.appmap.process.EventProcessorType;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

public class HookableInterfaceName extends Hookable {
  private String interfaceName;

  public HookableInterfaceName(String interfaceName, Hookable ... children) {
    super(children);
    this.interfaceName = interfaceName;
  }

  @Override
  protected Boolean match(CtClass classType) {
    try {
      for (CtClass superType : classType.getInterfaces()) {
        if (superType.getName().equals(interfaceName)) {
          return true;
        }
      }
    } catch (NotFoundException e) {
      // fall through
    }

    return false;
  }
}