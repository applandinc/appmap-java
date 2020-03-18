package com.appland.appmap.record;

import com.alibaba.fastjson.JSONWriter;
import com.appland.appmap.config.AppMapConfig;
import com.appland.appmap.output.v1.Event;
import com.appland.appmap.record.IRecordingSession.Metadata;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class AppMapSerializer {
  public static class FileSections {
    public static final String Version = "version";
    public static final String Metadata = "metadata";
    public static final String Events = "events";
    public static final String ClassMap = "class_map";
  }

  private class SectionInfo {
    public String name;
    public String type;

    SectionInfo(String name, String type) {
      this.name = name;
      this.type = type;
    }
  }

  private JSONWriter json;
  private SectionInfo currentSection = null;
  private HashSet<String> sectionsWritten = new HashSet<String>();

  AppMapSerializer(Writer writer) {
    this.json = new JSONWriter(writer);
    this.json.startObject();
  }

  private void setCurrentSection(String section, String type) throws IOException {
    if (this.currentSection != null && this.currentSection.name == section) {
      return;
    }

    if (this.sectionsWritten.contains(section)) {
      throw new IOException(String.format("%s section already written", section));
    }

    if (this.currentSection != null && this.currentSection.name != section) {
      // close the current section before updating it
      if (this.currentSection.type == "object") {
        this.json.endObject();
      } else if (this.currentSection.type == "array") {
        this.json.endArray();
      }
    }

    if (this.currentSection == null || this.currentSection.name != section) {
      this.sectionsWritten.add(section);
    }

    this.currentSection = new SectionInfo(section, type);

    if (this.currentSection.type == "object") {
      this.json.writeKey(section);
      this.json.startObject();
    } else if (this.currentSection.type == "array") {
      this.json.writeKey(section);
      this.json.startArray();
    }
  }

  public void write(Metadata metadata) throws IOException {
    this.setCurrentSection(FileSections.Version, "");
    this.json.writeKey("version");
    this.json.writeValue("1.2");

    this.setCurrentSection(FileSections.Metadata, "");
    this.json.writeKey("metadata");
    this.json.startObject();
    {
      if (metadata.scenarioName != null) {
        this.json.writeKey("name");
        this.json.writeValue(metadata.scenarioName);
      }

      this.json.writeKey("app");
      this.json.writeValue(AppMapConfig.get().name);

      this.json.writeKey("language");
      this.json.startObject();
      {
        this.json.writeKey("name");
        this.json.writeValue("java");
      }
      this.json.endObject();

      this.json.writeKey("client");
      this.json.startObject();
      {
        this.json.writeKey("name");
        this.json.writeValue("appmap-java");
        this.json.writeKey("url");
        this.json.writeValue("https://github.com/appland/appmap-java");
      }
      this.json.endObject();

      this.json.writeKey("recorder");
      this.json.startObject();
      {
        this.json.writeKey("name");
        this.json.writeValue(metadata.recorderName);
      }
      this.json.endObject();

      this.json.writeKey("recording");
      this.json.startObject();
      {
        if (metadata.recordedClassName != null) {
          this.json.writeKey("defined_class");
          this.json.writeValue(metadata.recordedClassName);
        }

        if (metadata.recordedMethodName != null) {
          this.json.writeKey("method_id");
          this.json.writeValue(metadata.recordedMethodName);
        }
      }
      this.json.endObject();

      this.json.writeKey("framework");
      this.json.startObject();
      {
        if (metadata.framework != null) {
          this.json.writeKey("name");
          this.json.writeValue(metadata.framework);
        }

        if (metadata.frameworkVersion != null) {
          this.json.writeKey("version");
          this.json.writeValue(metadata.frameworkVersion);
        }
      }
      this.json.endObject();
    }
    this.json.endObject();
  }

  public void write(CodeObjectTree codeObjects) throws IOException {
    this.setCurrentSection(FileSections.ClassMap, "");
    this.json.writeKey("classMap");
    this.json.writeValue(codeObjects.toArray());
  }

  public void write(List<Event> events) throws IOException {
    this.setCurrentSection(FileSections.Events, "array");

    for (Event event : events) {
      this.json.writeObject(event);
    }

    this.json.flush();
  }

  public void write(Event event) throws IOException {
    this.setCurrentSection(FileSections.Events, "array");
    this.json.writeObject(event);
  }

  public void finalize() throws IOException {
    this.setCurrentSection("EOF", "");
    this.json.endObject();
    this.json.close();
  }
}