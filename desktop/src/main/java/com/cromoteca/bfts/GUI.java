package com.cromoteca.bfts;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cromoteca.bfts.gui.GuiLogManager;
import com.cromoteca.bfts.gui.LogCollector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GUI {
  public static void main(String[] args) {
    GUI app = new GUI();
    app.open();
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(GUI.class);

  private final Display display;
  private final Shell shell;
  private final Browser browser;
  private final ClientAPI clientAPI;
  private final ObjectMapper mapper;
  private final LogCollector logCollector;
  private final PrintStream originalOut;
  private final PrintStream originalErr;

  public GUI() {
    clientAPI = new ClientAPI();
    mapper = new ObjectMapper();
    display = new Display();
    shell = new Shell(display);
    shell.setText("BFTS");
    shell.setLayout(new FillLayout(SWT.VERTICAL));

    browser = new Browser(shell, SWT.NONE);
    String url = GUI.class.getResource("/ui/index.html").toExternalForm();
    browser.setUrl(url);

    LOGGER.info("Initializing GUI using browser engine: {}",
        browser.getBrowserType());

    originalOut = System.out;
    originalErr = System.err;
    logCollector = new LogCollector(500);
    installLogBridge();

    new BrowserFunction(browser, "openDirectoryPicker") {
      @Override
      public Object function(Object[] arguments) {
        DirectoryDialog dialog = new DirectoryDialog(shell);
        dialog.setText("Select Directory");
        dialog.setMessage("Please select a directory");
        return dialog.open();
      }
    };

    // Add a JS <-> Java bridge for logging
    new BrowserFunction(browser, "logToJava") {
      @Override
      public Object function(Object[] arguments) {
        if (arguments != null) {
          for (Object arg : arguments) {
            System.out.println("[Browser] " + arg);
          }
        }

        return null;
      }
    };

    // Allows JS to invoke any method in clientAPI by name, with JSON params/return/exception
    new BrowserFunction(browser, "invoke") {
      @Override
      public Object function(Object[] args) {
        try {
          if (args == null || args.length == 0 || args.length > 2) {
            throw new IllegalArgumentException("Invalid arguments: "
                + (args == null ? "null" : args.length + " arguments"));
          }

          String methodName = String.valueOf(args[0]);
          String paramsJson = args.length == 1 ? null : String.valueOf(args[1]);
          Object[] arr = (paramsJson == null || paramsJson.equals("null")
              || paramsJson.equals("[]")) ? new Object[0]
              : mapper.readValue(paramsJson, Object[].class);
          Method[] methods = clientAPI.getClass().getMethods();

          for (Method m : methods) {
            if (m.getName().equals(methodName)) {
              Class<?>[] paramTypes = m.getParameterTypes();

              if (paramTypes.length == arr.length) {
                Object[] params = new Object[paramTypes.length];
                boolean convertible = true;

                for (int i = 0; i < paramTypes.length; i++) {
                  try {
                    params[i] = mapper.convertValue(arr[i], paramTypes[i]);
                  } catch (IllegalArgumentException e) {
                    convertible = false;
                    break;
                  }
                }

                if (convertible) {
                  Object result = m.invoke(clientAPI, params);
                  return mapper.writeValueAsString(result);
                }
              }
            }
          }

          throw new NoSuchMethodException("No such method: " + methodName
              + " with parameters: " + paramsJson);
        } catch (Exception ex) {
          Throwable cause = ex instanceof InvocationTargetException
              && ex.getCause() != null ? ex.getCause() : ex;
          ObjectNode err = mapper.createObjectNode();
          err.put("exception", cause.getClass().getSimpleName());
          err.put("message", cause.getMessage());

          try {
            return mapper.writeValueAsString(err);
          } catch (JsonProcessingException e) {
            LOGGER.error("Error serializing exception: {}", e.getMessage());
            return null;
          }
        }
      }
    };

    new BrowserFunction(browser, "init") {
      @Override
      public Object function(Object[] arguments) {
        try {
          if (arguments == null || arguments.length != 3) {
            throw new IllegalArgumentException("Expected 3 arguments: name, path, inMemory");
          }
          String name = String.valueOf(arguments[0]);
          String path = String.valueOf(arguments[1]);
          boolean inMemory = Boolean.parseBoolean(String.valueOf(arguments[2]));
          clientAPI.init(name, path, inMemory);
          return String.format("Local storage %s initialized in directory %s", name, path);
        } catch (Exception ex) {
          return String.format("Error: %s: %s", ex.getClass().getSimpleName(), ex.getMessage());
        }
      }
    };

    shell.setSize(1024, 768);
  }

  public void open() {
    shell.open();

    try {
      while (!shell.isDisposed()) {
        if (!display.readAndDispatch()) {
          display.sleep();
        }
      }
    } finally {
      GuiLogManager.unregister(logCollector);
      System.setOut(originalOut);
      System.setErr(originalErr);
      display.dispose();
      System.exit(0); // Ensure the program ends when the GUI window closes
    }
  }

  private void installLogBridge() {
    GuiLogManager.register(logCollector);
    OutputStream stdoutCollector = logCollector.createStdoutStream();
    OutputStream stderrCollector = logCollector.createStderrStream();

    System.setOut(createTeePrintStream(originalOut, stdoutCollector));
    System.setErr(createTeePrintStream(originalErr, stderrCollector));
  }

  private PrintStream createTeePrintStream(PrintStream original,
      OutputStream collectorStream) {
    OutputStream tee = new DualOutputStream(
        new PrintStreamAdapter(original),
        collectorStream);

    try {
      return new PrintStream(tee, true, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException ex) {
      LOGGER.warn("UTF-8 encoding not supported, fallback to default: {}",
          ex.getMessage());
      return new PrintStream(tee, true);
    }
  }

  private static class PrintStreamAdapter extends OutputStream {
    private final PrintStream delegate;

    private PrintStreamAdapter(PrintStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(int b) {
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      delegate.write(b, off, len);
    }

    @Override
    public void flush() {
      delegate.flush();
    }
  }

  private static class DualOutputStream extends OutputStream {
    private final OutputStream first;
    private final OutputStream second;

    private DualOutputStream(OutputStream first, OutputStream second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public void write(int b) throws java.io.IOException {
      first.write(b);
      second.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws java.io.IOException {
      first.write(b, off, len);
      second.write(b, off, len);
    }

    @Override
    public void flush() throws java.io.IOException {
      first.flush();
      second.flush();
    }

    @Override
    public void close() throws java.io.IOException {
      flush();
    }
  }
}
