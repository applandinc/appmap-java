package com.appland.appmap.web;

import com.appland.appmap.record.ActiveSessionException;
import com.appland.appmap.record.IRecordingSession;
import com.appland.appmap.record.Recorder;
import com.appland.appmap.util.Logger;
import com.appland.appmap.config.Properties;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(name = "RecordServlet", urlPatterns = {"/_appmap/record"}, loadOnStartup = 1) 
public class RecordServlet extends HttpServlet {
  private static final Recorder recorder = Recorder.getInstance();
  private static boolean debug = Properties.DebugHttp;

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse res) {
    if (debug) {
      Logger.println("RecordServlet.doDelete");
    }

    try {
      String json = recorder.stop();
      res.setContentType("application/json");
      res.setContentLength(json.length());

      PrintWriter writer = res.getWriter();
      writer.write(json);
      writer.flush();
    } catch (ActiveSessionException e) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } catch (IOException e) {
      Logger.printf("failed to write response: %s\n", e.getMessage());
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) {
    if (debug) {
      Logger.println("RecordServlet.doGet");
    }

    res.setStatus(HttpServletResponse.SC_OK);

    String responseJson = String.format("{\"enabled\":%b}", recorder.hasActiveSession());
    res.setContentType("application/json");
    res.setContentLength(responseJson.length());

    try {
      PrintWriter writer = res.getWriter();
      writer.write(responseJson);
      writer.flush();
    } catch (IOException e) {
      Logger.printf("failed to write response: %s\n", e.getMessage());
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) {
    if (debug) {
      Logger.println("RecordServlet.doPost");
    }

    IRecordingSession.Metadata metadata = new IRecordingSession.Metadata();
    metadata.recorderName = "remote_recording";
    try {
      recorder.start(metadata);
    } catch (ActiveSessionException e) {
      res.setStatus(HttpServletResponse.SC_CONFLICT);
    }
  }
}
