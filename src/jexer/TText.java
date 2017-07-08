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
 * This code is heavily based upon the original implementation of TText.java
 * by Kevin Lamonte [kevin.lamonte@gmail.com], version 1.
 * 
 * @author niki
 * @version 1-niki
 */
package jexer;

import static jexer.TKeypress.kbDown;
import static jexer.TKeypress.kbEnd;
import static jexer.TKeypress.kbHome;
import static jexer.TKeypress.kbPgDn;
import static jexer.TKeypress.kbPgUp;
import static jexer.TKeypress.kbUp;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jexer.bits.CellAttributes;
import jexer.event.TKeypressEvent;
import jexer.event.TMouseEvent;

/**
 * TText implements a simple scrollable text area. It reflows automatically on
 * resize.
 */
public final class TText extends TWidget {

    /**
     * Text to display.
     */
    private List<String> text;

    /**
     * Text converted to lines.
     */
    private List<String> wrappedLines;

    /**
     * Text color.
     */
    private String colorKey;

    /**
     * Vertical scrollbar.
     */
    private TVScroller vScroller;

    /**
     * Maximum width of a single line.
     */
    private int maxLineWidth;

    /**
     * Number of lines between each paragraph.
     */
    private int lineSpacing = 1;

    /**
     * Add a paragraph to this {@link TText}, but does not reflow.
     * 
     * @param paragraph
     *            the new paragrapgh
     */
    public void addLine(final String paragraph) {
        text.add(paragraph);
    }
    
    /**
     * Set the text of this widget, but does not reflow.
     * <p>
     * Note that the paragraphs in this text are supposed to
     * be separated by 2 newlines (\n\n).
     * 
     * @param text
     *            the full text that will replace the current one
     *            with "\n\n"-separated paragraphs
     */
    public void setText(final String text) {
        this.text.clear();
        for (String paragraph : text.split("\n\n")) {
            this.text.add(paragraph);
        }
    }

    /**
     * Recompute the bounds for the scrollbars.
     */
    private void computeBounds() {
        maxLineWidth = 0;
        for (String line : wrappedLines) {
            if (line.length() > maxLineWidth) {
                maxLineWidth = line.length();
            }
        }

        vScroller.setBottomValue((wrappedLines.size() - getHeight()) + 1);
        if (vScroller.getBottomValue() < 0) {
            vScroller.setBottomValue(0);
        }
        if (vScroller.getValue() > vScroller.getBottomValue()) {
            vScroller.setValue(vScroller.getBottomValue());
        }
    }

    /**
     * Add a paragrah to the flow by wrapping it in lines.
     * 
     * @param paragraph
     *            the paragraph to add
     */
    private void wrap(String paragraph) {
        int max = getWidth() - 1; // -1 for the VScroll

        if (max > 0) {
            for (String line : paragraph.split("\n")) {
                if (line.length() < max) {
                    wrappedLines.add(line);
                } else {
                    String part = line;
                    while (!part.isEmpty()) {
                        int stop = Math.min(part.length(), max);

                        String dash = "";
                        if (part.length() > max) {
                            // move stop backward if better visually
                            int i = stop - 1;
                            for (; i > 0; i--) {
                                char car = part.charAt(i);
                                // TODO: better method to list punctuation?
                                if (car == ' ' || car == ',' || car == '.'
                                        || car == ',' || car == '?'
                                        || car == '!' || car == '|'
                                        || car == '-' || car == '='
                                        || car == ';' || car == ':') {
                                    stop = i + 1;
                                    break;
                                }
                            }

                            if (i <= 0) {
                                if (stop > 1) {
                                    stop--;
                                    dash = "-";
                                }
                            }
                        }

                        wrappedLines.add(part.substring(0, stop) + dash);
                        part = part.substring(stop).trim();
                    }
                }
            }

            for (int i = 0; i < lineSpacing; i++) {
                wrappedLines.add("");
            }
        }
    }

    /**
     * Resize text and scrollbars for a new width/height.
     */
    public void reflow() {
        this.wrappedLines.clear();
        for (String paragraph : text) {
            wrap(paragraph);
        }

        // Start at the top
        if (vScroller == null) {
            vScroller = new TVScroller(this, getWidth() - 1, 0, getHeight() - 1);
            vScroller.setTopValue(0);
            vScroller.setValue(0);
        } else {
            vScroller.setX(getWidth() - 1);
            vScroller.setHeight(getHeight() - 1);
        }
        vScroller.setBigChange(getHeight() - 1);

        computeBounds();
    }
    
    /**
     * Go to the top edge of the scroller.
     */
    public void toTop() {
        vScroller.toTop();
    }

    /**
     * Go to the bottom edge of the scroller.
     */
    public void toBottom() {
        vScroller.toBottom();
    }

    /**
     * Public constructor.
     *
     * @param parent parent widget
     * @param text text on the screen
     * @param x column relative to parent
     * @param y row relative to parent
     * @param width width of text area
     * @param height height of text area
     */
    public TText(final TWidget parent, final String text, final int x,
            final int y, final int width, final int height) {

        this(parent, text, x, y, width, height, "ttext");
    }

    /**
     * Public constructor.
     *
     * @param parent parent widget
     * @param text text on the screen
     * @param x column relative to parent
     * @param y row relative to parent
     * @param width width of text area
     * @param height height of text area
     * @param colorKey ColorTheme key color to use for foreground
     * text. Default is "ttext".
     */
    public TText(final TWidget parent, final String text, final int x,
            final int y, final int width, final int height,
            final String colorKey) {

        // Set parent and window
        super(parent, x, y, width, height);

        this.colorKey = colorKey;

        this.text = new ArrayList<String>();
        this.wrappedLines = new LinkedList<String>();

        setText(text);
        reflow();
    }

    /**
     * Draw the text box.
     */
    @Override
    public void draw() {
        // Setup my color
        CellAttributes color = getTheme().getColor(colorKey);

        int begin = vScroller.getValue();
        int topY = 0;
        for (int i = begin; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            String formatString = "%-" + Integer.toString(getWidth() - 1) + "s";
            getScreen().putStringXY(0, topY, String.format(formatString, line),
                    color);
            topY++;

            if (topY >= (getHeight() - 1)) {
                break;
            }
        }

        // Pad the rest with blank lines
        for (int i = topY; i < (getHeight() - 1); i++) {
            getScreen().hLineXY(0, i, getWidth() - 1, ' ', color);
        }

    }

    /**
     * Handle mouse press events.
     *
     * @param mouse mouse button press event
     */
    @Override
    public void onMouseDown(final TMouseEvent mouse) {
        if (mouse.isMouseWheelUp()) {
            vScroller.decrement();
            return;
        }
        if (mouse.isMouseWheelDown()) {
            vScroller.increment();
            return;
        }

        // Pass to children
        super.onMouseDown(mouse);
    }

    /**
     * Handle keystrokes.
     *
     * @param keypress keystroke event
     */
    @Override
    public void onKeypress(final TKeypressEvent keypress) {
        if (keypress.equals(kbUp)) {
            vScroller.decrement();
        } else if (keypress.equals(kbDown)) {
            vScroller.increment();
        } else if (keypress.equals(kbPgUp)) {
            vScroller.bigDecrement();
        } else if (keypress.equals(kbPgDn)) {
            vScroller.bigIncrement();
        } else if (keypress.equals(kbHome)) {
            vScroller.toTop();
        } else if (keypress.equals(kbEnd)) {
            vScroller.toBottom();
        } else {
            // Pass other keys (tab etc.) on
            super.onKeypress(keypress);
        }
    }
}
