package com.cromoteca.bfts;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.widgets.DirectoryDialog;

public class CommandLineSWT {
    public static void main(String[] args) {
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("BFTS - SWT GUI");
        shell.setLayout(new FillLayout(SWT.VERTICAL));

        Browser browser = new Browser(shell, SWT.NONE);
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
                if (arguments != null && arguments.length > 0) {
                    System.out.println("[Browser] " + arguments[0]);
                }
                return null;
            }
        };

        shell.setSize(600, 400);
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        display.dispose();
    }
}
