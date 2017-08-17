/*
 * Jexer - Java Text User Interface
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2017 Kevin Lamonte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * @author Kevin Lamonte [kevin.lamonte@gmail.com]
 * @version 1
 */
package jexer.demos;

import jexer.*;
import jexer.event.*;
import static jexer.TCommand.*;
import static jexer.TKeypress.*;

/**
 * This window demonstates the TEditor widget.
 */
public class DemoEditorWindow extends TWindow {

    /**
     * Hang onto my TEditor so I can resize it with the window.
     */
    private TEditorWidget editField;

    /**
     * Public constructor makes a text window out of any string.
     *
     * @param parent the main application
     * @param title the text string
     * @param text the text string
     */
    public DemoEditorWindow(final TApplication parent, final String title,
        final String text) {

        super(parent, title, 0, 0, 44, 22, RESIZABLE);
        editField = addEditor(text, 0, 0, 42, 20);

        statusBar = newStatusBar("Editable text demo window");
        statusBar.addShortcutKeypress(kbF1, cmHelp, "Help");
        statusBar.addShortcutKeypress(kbF2, cmShell, "Shell");
        statusBar.addShortcutKeypress(kbF10, cmExit, "Exit");
    }

    /**
     * Public constructor.
     *
     * @param parent the main application
     */
    public DemoEditorWindow(final TApplication parent) {
        this(parent, "Editor",
"This is an example of an editable text field.  Some example text follows.\n" +
"\n" +
"This library implements a text-based windowing system loosely\n" +
"reminiscient of Borland's [Turbo\n" +
"Vision](http://en.wikipedia.org/wiki/Turbo_Vision) library.  For those\n" +
"wishing to use the actual C++ Turbo Vision library, see [Sergio\n" +
"Sigala's updated version](http://tvision.sourceforge.net/) that runs\n" +
"on many more platforms.\n" +
"\n" +
"This library is licensed MIT.  See the file LICENSE for the full license\n" +
"for the details.\n" +
"\n" +
"package jexer.demos;\n" +
"\n" +
"import jexer.*;\n" +
"import jexer.event.*;\n" +
"import static jexer.TCommand.*;\n" +
"import static jexer.TKeypress.*;\n" +
"\n" +
"/**\n" +
" * This window demonstates the TText, THScroller, and TVScroller widgets.\n" +
" */\n" +
"public class DemoEditorWindow extends TWindow {\n" +
"\n" +
"1 2 3 123\n" +
"\n"
        );

    }

    /**
     * Handle window/screen resize events.
     *
     * @param event resize event
     */
    @Override
    public void onResize(final TResizeEvent event) {
        if (event.getType() == TResizeEvent.Type.WIDGET) {
            // Resize the text field
            TResizeEvent editSize = new TResizeEvent(TResizeEvent.Type.WIDGET,
                event.getWidth() - 2, event.getHeight() - 2);
            editField.onResize(editSize);
            return;
        }

        // Pass to children instead
        for (TWidget widget: getChildren()) {
            widget.onResize(event);
        }
    }

}