package com.appland.appmap.transform.annotations;

import com.appland.appmap.output.v1.Parameters;
import com.appland.appmap.record.EventTemplateRegistry;
import com.appland.appmap.util.Logger;
import javassist.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Hook {
  private static final EventTemplateRegistry eventTemplateRegistry = EventTemplateRegistry.get();

  private final static List<Function<CtBehavior, ISystem>> requiredHookSystemFactories =
  new ArrayList<Function<CtBehavior, ISystem>>() {{
      add(HookAnnotatedSystem::from);
      add(HookClassSystem::from);
      add(HookConditionSystem::from);
      add(HookRecordsSystem::from);
  }};

  private final static List<Function<CtBehavior, ISystem>> optionalSystemFactories =
      new ArrayList<Function<CtBehavior, ISystem>>() {{
          add(CallbackOnSystem::from);
          add(ExcludeReceiverSystem::from);
          add(ArgumentArraySystem::from);
      }};

  private SourceMethodSystem sourceSystem;
  private List<ISystem> optionalSystems;
  private Parameters staticParameters = new Parameters();
  private Parameters hookParameters;
  private CtBehavior hookBehavior;
  private String uniqueKey = "";

  private Hook( SourceMethodSystem sourceSystem,
                List<ISystem> optionalSystems,
                CtBehavior hookBehavior) {
    this.sourceSystem = sourceSystem;
    this.optionalSystems = optionalSystems;
    this.hookBehavior = hookBehavior;
    this.hookParameters = new Parameters(hookBehavior);
    this.uniqueKey = (String) AnnotationUtil.getValue(hookBehavior, Unique.class, "");

    this.buildParameters();
  }

  /**
   * Creates a Hook from code behavior.
   *
   * @return If hookBehavior is a valid hook, return a new Hook object. Otherwise, null.
   */
  public static Hook from(CtBehavior hookBehavior) {
    SourceMethodSystem sourceSystem = null;
    for (Function<CtBehavior, ISystem> factoryFn : requiredHookSystemFactories) {
      sourceSystem = (SourceMethodSystem) factoryFn.apply(hookBehavior);
      if (sourceSystem != null) {
        break;
      }
    }

    if (sourceSystem == null) {
      return null;
    }

    List<ISystem> optionalSystems = optionalSystemFactories
        .stream()
        .map(factoryFn -> factoryFn.apply(hookBehavior))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    Hook hook = new Hook(sourceSystem, optionalSystems, hookBehavior);
    for (ISystem optionalSystem : optionalSystems) {
      if (!optionalSystem.validate(hook)) {
        Logger.println("hook "
            + hook
            + " failed validation from "
            + optionalSystem.getClass().getSimpleName());
        return null;
      }
    }

    return hook;
  }

  public void buildParameters() {
    this.sourceSystem.mutateStaticParameters(this.hookBehavior, this.staticParameters);
    this.optionalSystems
        .stream()
        .sorted(Comparator.comparingInt(ISystem::getParameterPriority))
        .forEach(system -> system.mutateStaticParameters(this.hookBehavior, this.staticParameters));
  }

  public Parameters getRuntimeParameters(HookBinding binding) {
    Parameters runtimeParameters = this.staticParameters.clone();
    this.sourceSystem.mutateRuntimeParameters(binding, runtimeParameters);
    this.optionalSystems
        .stream()
        .sorted(Comparator.comparingInt(ISystem::getParameterPriority))
        .forEach(system -> {
          system.mutateRuntimeParameters(binding, runtimeParameters);
        });
    return runtimeParameters;
  }

  public HookSite prepare(CtBehavior targetBehavior) {
    if (targetBehavior instanceof CtConstructor) {
      return null;
    }

    if (!this.sourceSystem.match(targetBehavior)) {
      return null;
    }

    Integer behaviorOrdinal = eventTemplateRegistry.register(targetBehavior);
    if (behaviorOrdinal < 0) {
      return null;
    }

    HookBinding binding = new HookBinding(this, targetBehavior, behaviorOrdinal);
    Parameters behaviorParameters = new Parameters(targetBehavior);
    for (ISystem system : this.optionalSystems) {
      if (!system.validate(binding)) {
        return null;
      }
    }

    Parameters runtimeParameters = this.getRuntimeParameters(binding);

    return new HookSite(this, behaviorOrdinal, runtimeParameters);
  }

  public static void apply(CtBehavior targetBehavior, List<HookSite> hookSites) {
    final CtClass returnType = getReturnType(targetBehavior);
    final Boolean returnsVoid = (returnType == CtClass.voidType);

    final String[] invocations = new String[3];
    for (HookSite hookSite : hookSites) {
      final Integer index = hookSite.getMethodEvent().getIndex();
      if (invocations[index] == null) {
        invocations[index] = hookSite.getHookInvocation();
      } else {
        invocations[index] += hookSite.getHookInvocation();
      }
    }

      final String uniqueLocks = hookSites
          .stream()
          .filter(hs -> !hs.getUniqueKey().isEmpty())
          .map(hs -> hs.getUniqueKey())
          .distinct()
          .map(uniqueKey -> (""
            + "com.appland.appmap.process.ThreadLock.current().lockUnique(\""
            + uniqueKey
            + "\");"))
          .collect(Collectors.joining("\n"));

    try {
      targetBehavior.insertBefore(
          beforeSrcBlock(uniqueLocks,
            invocations[MethodEvent.METHOD_INVOCATION.getIndex()]));

      targetBehavior.insertAfter(
          afterSrcBlock(invocations[MethodEvent.METHOD_RETURN.getIndex()]));

      if (returnsVoid) {
        targetBehavior.addCatch("{"
            + "com.appland.appmap.process.ThreadLock.current().exit();"
            + "return;"
            + "}",
            ClassPool.getDefault().get("com.appland.appmap.process.ExitEarly"));
      } else if (!returnType.isPrimitive()) {
        targetBehavior.addCatch("{"
            + "com.appland.appmap.process.ThreadLock.current().exit();"
            + "return ("
            + returnType.getName()
            + ") $e.getReturnValue();"
            + "}",
            ClassPool.getDefault().get("com.appland.appmap.process.ExitEarly"));
      }

      targetBehavior.addCatch(
          catchSrcBlock(invocations[MethodEvent.METHOD_EXCEPTION.getIndex()]),
          ClassPool.getDefault().get("java.lang.Exception"));
      
    } catch (CannotCompileException e) {
      Logger.println("failed to compile");
      Logger.println("       method "
          + targetBehavior.getDeclaringClass().getName()
          + "."
          + targetBehavior.getName());

      Logger.println(e.getMessage());
    } catch (NotFoundException e) {
      Logger.println("failed to find class\n");
      Logger.println(e.getMessage());
    }
  }

  /* Concatenates potentially null strings with no delimeter. The return value
     is guaranteed to be non-null.
  */
  private static String safeConcatStrings(String... strs) {
    return Arrays.stream(strs)
        .filter(Objects::nonNull)
        .collect(Collectors.joining());
  }

  private static String beforeSrcBlock(String... invocations) {
    final String allInvocations = safeConcatStrings(invocations);
    return "{"
        + "com.appland.appmap.process.ThreadLock.current().enter();"
        + allInvocations
        + "}";
  }

  private static String afterSrcBlock(String... invocations) {
    final String allInvocations = safeConcatStrings(invocations);
    return "{"
        + allInvocations
        + "com.appland.appmap.process.ThreadLock.current().exit();"
        + "}";
  }

  private static String catchSrcBlock(String... invocations) {
    final String allInvocations = safeConcatStrings(invocations);
    return "{"
        + allInvocations
        + "com.appland.appmap.process.ThreadLock.current().exit();"
        + "throw $e;"
        + "}";
  }

  public String getKey() {
    return this.sourceSystem.getKey();
  }

  public String toString() {
    return String.format("%s(%s)", this.sourceSystem.toString(), this.hookParameters.toString());
  }

  public String getUniqueKey() {
    return this.uniqueKey;
  }

  public Parameters getParameters() {
    return this.hookParameters;
  }

  public CtBehavior getBehavior() {
    return this.hookBehavior;
  }
  
  public void validate() throws HookValidationException {

  } 

  public void validate(CtBehavior behavior) throws HookValidationException {

  }

  public MethodEvent getMethodEvent() {
    MethodEvent methodEvent = MethodEvent.METHOD_INVOCATION;
    for (ISystem system : this.optionalSystems) {
      if (system instanceof CallbackOnSystem) {
        methodEvent = ((CallbackOnSystem) system).getMethodEvent();
        break;
      }
    }
    return methodEvent;
  }

  public SourceMethodSystem getSourceSystem() {
    return this.sourceSystem;
  }

  private static CtClass getReturnType(CtBehavior behavior) {
    CtClass returnType = CtClass.voidType;
    if (behavior instanceof CtMethod) {
      try {
        returnType = ((CtMethod) behavior).getReturnType();
      } catch (NotFoundException e) {
        Logger.println("warning - unknown return type");
        Logger.println(e.getMessage());
      }
    }
    return returnType;
  }

  public ISystem getSystem(Class<? extends ISystem> systemClass) {
    for (ISystem system : this.optionalSystems) {
      if (systemClass.isInstance(system)) {
        return system;
      }
    }
    return null;
  }
}
