package com.appland.appmap.process;

import com.appland.appmap.output.v1.Event;
import com.appland.appmap.process.EventProcessorType;
import com.appland.appmap.process.HttpTomcatReceiver;
import com.appland.appmap.process.NullReceiver;
import com.appland.appmap.process.SqlJdbcReceiver;
import com.appland.appmap.process.PassThroughReceiver;
import com.appland.appmap.record.EventFactory;
import com.appland.appmap.record.RuntimeRecorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EventDispatcher {
  public interface Callback {
    void invoke();
  }

  public static final int    EVENT_DISCARD = (1 << 0);
  public static final int     EVENT_RECORD = (1 << 1);
  public static final int EVENT_EXIT_EARLY = (1 << 2);

  private static HashMap<EventProcessorType, IEventProcessor> eventProcessors =
      new HashMap<>() {{
        put(EventProcessorType.Null, new NullReceiver());
        put(EventProcessorType.PassThrough, new PassThroughReceiver());
        put(EventProcessorType.Http_Tomcat, new HttpTomcatReceiver());
        put(EventProcessorType.Sql_Jdbc, new SqlJdbcReceiver());
        put(EventProcessorType.ServletFilter, new ServletFilterReceiver());
      }};

  private static Boolean               isEnabled = false;
  private static RuntimeRecorder runtimeRecorder = RuntimeRecorder.get();
  private static List<Callback>        callbacks = new ArrayList<>();

  public static void invoke(Callback c) {
    EventDispatcher.callbacks.add(c);
  }

  public static void runCallbacks() {
    if (EventDispatcher.callbacks.size() < 1) {
      return;
    }

    List<Callback> callbacks = new ArrayList<>(EventDispatcher.callbacks);
    EventDispatcher.callbacks.clear();

    for (Callback callback : callbacks) {
      callback.invoke();
    }
  }

  public static Boolean dispatchEvent(EventProcessorType type, Event event) {
    IEventProcessor eventProcessor = EventDispatcher.eventProcessors.get(type);
    if (eventProcessor == null) {
      return true;
    }

    // track if we were enabled before processing this event. if the flag changes, we don't want
    // to record this event.
    Boolean wasEnabled = EventDispatcher.isEnabled();
    int eventAction = eventProcessor.processEvent(event);

    Boolean recordEvent = (eventAction & EventDispatcher.EVENT_RECORD) != 0;
    Boolean continueExecution = (eventAction & EventDispatcher.EVENT_EXIT_EARLY) == 0;

    if (recordEvent && wasEnabled == true) {
      EventDispatcher.runtimeRecorder.recordEvent(event);
    }

    return continueExecution;
  }

  public static Boolean isEnabled() {
    return EventDispatcher.isEnabled;
  }

  public static void setEnabled(Boolean val) {
    EventDispatcher.isEnabled = val;
  }
}
