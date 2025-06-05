package com.cromoteca.bfts;

import java.util.prefs.Preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.cromoteca.bfts.client.Configuration;
import com.cromoteca.bfts.storage.EncryptionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CommandLineSWT {

    private static final Configuration CONFIG
            = new Configuration(Preferences.userNodeForPackage(CommandLineSWT.class));

    private final Display display;
    private final Shell shell;
    private final Browser browser;

    public CommandLineSWT() {
        display = new Display();
        shell = new Shell(display);
        shell.setText("BFTS - SWT GUI");
        shell.setLayout(new FillLayout(SWT.VERTICAL));

        browser = new Browser(shell, SWT.NONE);
        String url = CommandLineSWT.class.getResource("/ui/index.html").toExternalForm();
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
                    String js = "showPickedDirectory(" + (dir == null ? "null" : "'" + dir.replace("\\", "\\\\").replace("'", "\\'") + "'") + ");";
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

        // Shows a list of local and connected storages
        new BrowserFunction(browser, "list") {
            @Override
            public Object function(Object[] arguments) {
                try {
                    ObjectMapper mapper = new ObjectMapper();

                    ArrayNode locals = mapper.createArrayNode();
                    for (String name : CONFIG.getLocalStorages()) {
                        ObjectNode node = mapper.createObjectNode();
                        node.put("name", name);
                        node.put("port", CONFIG.getLocalStoragePort(name));
                        locals.add(node);
                    }

                    ArrayNode connected = mapper.createArrayNode();
                    for (String name : CONFIG.getConnectedStorages()) {
                        ObjectNode node = mapper.createObjectNode();
                        node.put("name", name);
                        node.put("path", CONFIG.getConnectedStoragePath(name));
                        EncryptionType encType = CONFIG.getConnectedStorageEncryptionType(name);
                        node.put("encryption", encType.toString().toLowerCase());
                        connected.add(node);
                    }

                    ObjectNode result = mapper.createObjectNode();
                    result.set("localStorages", locals);
                    result.set("connectedStorages", connected);

                    return mapper.writeValueAsString(result);
                } catch (JsonProcessingException ex) {
                    System.err.println("Error serializing storage list: " + ex.getMessage());
                    return null;
                }
            }
        };

        shell.setSize(600, 400);
    }

    public void open() {
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }

    public static void main(String[] args) {
        CommandLineSWT app = new CommandLineSWT();
        app.open();
    }
}
