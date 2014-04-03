/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2013 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.helper.EditorData;
import com.maddyhome.idea.vim.helper.EditorDataContext;
import com.maddyhome.idea.vim.helper.UiHelper;
import com.maddyhome.idea.vim.option.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * This panel displays text in a <code>more</code> like window.
 */
public class MorePanel extends JPanel {
  private static Logger ourLogger = Logger.getInstance(MorePanel.class.getName());

  @NotNull private final Editor myEditor;

  @NotNull private JLabel myLabel = new JLabel("more");
  @NotNull private JTextArea myText = new JTextArea();
  @NotNull private JScrollPane myScrollPane =
    new JBScrollPane(myText, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
  private ComponentAdapter myAdapter;
  private boolean myAtEnd = false;
  private int myLineHeight = 0;

  @Nullable private JComponent myOldGlass = null;
  @Nullable private LayoutManager myOldLayout = null;
  private boolean myWasOpaque = false;

  private boolean myActive = false;

  private MorePanel(@NotNull Editor editor) {
    myEditor = editor;

    // Create a text editor for the text and a label for the prompt
    BorderLayout layout = new BorderLayout(0, 0);
    setLayout(layout);
    add(myScrollPane, BorderLayout.CENTER);
    add(myLabel, BorderLayout.SOUTH);

    setFontForElements();

    myText.setBorder(null);
    myScrollPane.setBorder(null);

    myLabel.setForeground(myText.getForeground());
    myLabel.setBackground(myText.getBackground());
    setForeground(myText.getForeground());
    setBackground(myText.getBackground());

    myText.setEditable(false);

    setBorder(BorderFactory.createEtchedBorder());

    myAdapter = new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        positionPanel();
      }
    };

    // Setup some listeners to handle keystrokes
    MoreKeyListener moreKeyListener = new MoreKeyListener(this);
    addKeyListener(moreKeyListener);
    myText.addKeyListener(moreKeyListener);
  }

  @NotNull
  public static MorePanel getInstance(@NotNull Editor editor) {
    MorePanel panel = EditorData.getMorePanel(editor);
    if (panel == null) {
      panel = new MorePanel(editor);
      EditorData.setMorePanel(editor, panel);
    }
    return panel;
  }

  public boolean hasText() {
    return myText.getText().length() > 0;
  }

  public String getText() {
    return myText.getText();
  }

  public void setText(@NotNull String data) {
    if (data.length() > 0 && data.charAt(data.length() - 1) == '\n') {
      data = data.substring(0, data.length() - 1);
    }

    myText.setText(data);
    myText.setCaretPosition(0);
  }

  /**
   * Turns on the more window for the given editor
   */
  public void activate() {
    JRootPane root = SwingUtilities.getRootPane(myEditor.getContentComponent());
    myOldGlass = (JComponent)root.getGlassPane();
    if (myOldGlass != null) {
      myOldLayout = myOldGlass.getLayout();
      myWasOpaque = myOldGlass.isOpaque();
      myOldGlass.setLayout(null);
      myOldGlass.setOpaque(false);
      myOldGlass.add(this);
      myOldGlass.addComponentListener(myAdapter);
    }

    setFontForElements();
    positionPanel();

    if (myOldGlass != null) {
      myOldGlass.setVisible(true);
    }
    myActive = true;

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myText.requestFocus();
          }
        });
      }
    });
  }

  /**
   * Turns off the ex entry field and puts the focus back to the original component
   */
  public void deactivate() {
    ourLogger.info("deactivate");
    if (!myActive) return;
    myActive = false;
    myText.setText("");
    if (myOldGlass != null) {
      myOldGlass.removeComponentListener(myAdapter);
      myOldGlass.setVisible(false);
      myOldGlass.remove(this);
      myOldGlass.setOpaque(myWasOpaque);
      myOldGlass.setLayout(myOldLayout);
    }
    myEditor.getContentComponent().requestFocus();
  }

  /**
   * Checks if the ex entry panel is currently active
   *
   * @return true if active, false if not
   */
  public boolean isActive() {
    return myActive;
  }

  private void setFontForElements() {
    final Font font = UiHelper.getEditorFont();
    myText.setFont(font);
    myLabel.setFont(font);
  }

  private static int countLines(@NotNull String text) {
    if (text.length() == 0) {
      return 0;
    }

    int count = 0;
    int pos = -1;
    while ((pos = text.indexOf('\n', pos + 1)) != -1) {
      count++;
    }

    if (text.charAt(text.length() - 1) != '\n') {
      count++;
    }

    return count;
  }

  private void scrollLine() {
    scrollOffset(myLineHeight);
  }

  private void scrollPage() {
    scrollOffset(myScrollPane.getVerticalScrollBar().getVisibleAmount());
  }

  private void scrollHalfPage() {
    double sa = myScrollPane.getVerticalScrollBar().getVisibleAmount() / 2.0;
    double offset = Math.ceil(sa / myLineHeight) * myLineHeight;
    scrollOffset((int)offset);
  }

  private void handleEnter() {
    if (myAtEnd) {
      close();
    }
    else {
      scrollLine();
    }
  }

  private void badKey() {
    myLabel.setText("-- MORE -- (RET: line, SPACE: page, d: half page, q: quit)");
  }

  private void scrollOffset(int more) {
    myAtEnd = false;
    int val = myScrollPane.getVerticalScrollBar().getValue();
    myScrollPane.getVerticalScrollBar().setValue(val + more);
    myScrollPane.getHorizontalScrollBar().setValue(0);
    if (ourLogger.isDebugEnabled()) {
      ourLogger.debug("val=" + val);
      ourLogger.debug("more=" + more);
      ourLogger
        .debug("scrollPane.getVerticalScrollBar().getMaximum()=" + myScrollPane.getVerticalScrollBar().getMaximum());
      ourLogger.debug("scrollPane.getVerticalScrollBar().getVisibleAmount()=" +
                      myScrollPane.getVerticalScrollBar().getVisibleAmount());
    }
    if (val + more >=
        myScrollPane.getVerticalScrollBar().getMaximum() - myScrollPane.getVerticalScrollBar().getVisibleAmount()) {
      myAtEnd = true;
      myLabel.setText("Hit ENTER or type command to continue");
    }
    else {
      myLabel.setText("-- MORE --");
    }
  }

  private void positionPanel() {
    final JComponent contentComponent = myEditor.getContentComponent();
    Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, contentComponent);
    setSize(scroll.getSize());

    myLineHeight = myText.getFontMetrics(myText.getFont()).getHeight();
    int count = countLines(myText.getText());
    int visLines = getSize().height / myLineHeight - 1;
    if (ourLogger.isDebugEnabled()) {
      ourLogger.debug("size.height=" + getSize().height);
      ourLogger.debug("lineHeight=" + myLineHeight);
      ourLogger.debug("count=" + count);
      ourLogger.debug("visLines=" + visLines);
    }
    int lines = Math.min(count, visLines);
    setSize(getSize().width, lines * myLineHeight + myLabel.getPreferredSize().height +
                             getBorder().getBorderInsets(this).top * 2);

    myScrollPane.getVerticalScrollBar().setValues(0, visLines, 0, count - 1);

    int height = getSize().height;
    Rectangle bounds = scroll.getBounds();
    bounds.translate(0, scroll.getHeight() - height);
    bounds.height = height;
    Point pos = SwingUtilities.convertPoint(scroll.getParent(), bounds.getLocation(),
                                            SwingUtilities.getRootPane(contentComponent).getGlassPane());
    bounds.setLocation(pos);
    setBounds(bounds);

    myScrollPane.getVerticalScrollBar().setValue(0);
    if (!Options.getInstance().isSet("more")) {
      // FIX
      scrollOffset(100000);
    }
    else {
      scrollOffset(0);
    }
  }

  private void close() {
    close(null);
  }

  private void close(@Nullable final KeyEvent e) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        deactivate();
        final VirtualFile vf = EditorData.getVirtualFile(myEditor);
        if (vf != null) {
          FileEditorManager.getInstance(myEditor.getProject()).openFile(vf, true);
        }

        final Project project = myEditor.getProject();

        if (project != null && e != null && e.getKeyChar() != '\n') {
          final KeyStroke key = KeyStroke.getKeyStrokeForEvent(e);
          final List<KeyStroke> keys = new ArrayList<KeyStroke>(1);
          keys.add(key);
          VimPlugin.getMacro().playbackKeys(myEditor, new EditorDataContext(myEditor), project, keys, 0, 0, 1);
        }
      }
    });
  }

  private static class MoreKeyListener extends KeyAdapter {
    private MorePanel myMorePanel;

    public MoreKeyListener(MorePanel panel) {
      this.myMorePanel = panel;
    }

    /**
     * Invoked when a key has been pressed.
     */
    public void keyTyped(@NotNull KeyEvent e) {
      if (myMorePanel.myAtEnd) {
        myMorePanel.close(e);
      }
      else {
        switch (e.getKeyChar()) {
          case ' ':
            myMorePanel.scrollPage();
            break;
          case 'd':
            myMorePanel.scrollHalfPage();
            break;
          case 'q':
            myMorePanel.close();
            break;
          case '\n':
            myMorePanel.handleEnter();
            break;
          case '\u001b':
            myMorePanel.close();
            break;
          case KeyEvent.CHAR_UNDEFINED: {
            switch (e.getKeyCode()) {
              case KeyEvent.VK_ENTER:
                myMorePanel.handleEnter();
                break;
              case KeyEvent.VK_ESCAPE:
                myMorePanel.close();
                break;
              default:
                if (ourLogger.isDebugEnabled()) ourLogger.debug("e.getKeyCode()=" + e.getKeyCode());
                myMorePanel.badKey();
            }
          }
          default:
            if (ourLogger.isDebugEnabled()) ourLogger.debug("e.getKeyChar()=" + (int)e.getKeyChar());
            myMorePanel.badKey();
        }
      }
    }
  }
}
