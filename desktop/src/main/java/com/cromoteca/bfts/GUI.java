package com.cromoteca.bfts;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    new BrowserFunction(browser, "openDirectoryPicker") {
      @Override
      public Object function(Object[] arguments) {
        // Open directory dialog from browser window
        DirectoryDialog dialog = new DirectoryDialog(shell);
        dialog.setText("Select Directory");
        dialog.setMessage("Please select a directory");
        String dir = dialog.open();

        browser.getDisplay().asyncExec(() -> {
          String js = "showPickedDirectory(" + (dir == null ? "null" : "'"
              + dir.replace("\\", "\\\\").replace("'", "\\'") + "'") + ");";
          browser.execute(js);
        });

        return null;
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

    shell.setSize(1024, 768);
  }

  public void open() {
    shell.open();

    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }

    display.dispose();
    System.exit(0); // Ensure the program ends when the GUI window closes
  }
}
