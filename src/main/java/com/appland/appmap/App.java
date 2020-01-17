package com.appland.appmap;

import java.lang.instrument.Instrumentation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import com.appland.appmap.commands.Inspect;
import com.appland.appmap.commands.Record;
import com.appland.appmap.commands.Upload;
import com.appland.appmap.config.AppMapConfig;
import com.appland.appmap.record.RuntimeRecorder;
import com.appland.appmap.transform.ClassFileTransformer;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "appmap",
    mixinStandardHelpOptions = true,
    subcommands = {
      Inspect.class,
      Record.class,
      Upload.class,
})
public class App implements Runnable {

  @Override public void run() { }

  /**
   * This method is the entry point of the CLI.
   */
  public static void main(String[] args) {
    CommandLine cmd = new CommandLine(new App());
    if (args.length == 0) {
      cmd.usage(System.out);
      return;
    }

    Integer exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  public static void premain(String agentArgs, Instrumentation inst) {
    if (AppMapConfig.load(new File("appmap.yml")) == null) {
      return;
    }

    inst.addTransformer(new ClassFileTransformer());

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        public void run() {
          RuntimeRecorder runtimeRecorder = RuntimeRecorder.get();

          try {
            System.err.print("writing data to appmap.json... ");
            PrintWriter out = new PrintWriter("appmap.json");
            out.print(runtimeRecorder.dumpJson());
            out.close();

            System.err.print("done.\n");
          } catch (FileNotFoundException e) {
            System.err.printf("failed: %s\n", e.getMessage());
          } catch (Exception e) {
            System.err.printf("failed: %s\n", e.getMessage());
          }
        }
    }, "Shutdown-thread"));
  }
}