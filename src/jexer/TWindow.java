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
package jexer;

import java.util.HashSet;

import jexer.bits.Cell;
import jexer.bits.CellAttributes;
import jexer.bits.GraphicsChars;
import jexer.event.TCommandEvent;
import jexer.event.TKeypressEvent;
import jexer.event.TMenuEvent;
import jexer.event.TMouseEvent;
import jexer.event.TResizeEvent;
import jexer.io.Screen;
import jexer.menu.TMenu;
import static jexer.TCommand.*;
import static jexer.TKeypress.*;

/**
 * TWindow is the top-level container and drawing surface for other widgets.
 */
public class TWindow extends TWidget {

    // ------------------------------------------------------------------------
    // Public constants -------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Window is resizable (default yes).
     */
    public static final int RESIZABLE   = 0x01;

    /**
     * Window is modal (default no).
     */
    public static final int MODAL       = 0x02;

    /**
     * Window is centered (default no).
     */
    public static final int CENTERED    = 0x04;
    
    /**
     * Window cannot be closed (default no, can be closed).
     */
    public static final int UNCLOSABLE  = 0x08;

    // ------------------------------------------------------------------------
    // Common window attributes -----------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Window flags.  Note package private access.
     */
    int flags = RESIZABLE;

    /**
     * Window title.
     */
    private String title = "";

    /**
     * Get window title.
     *
     * @return window title
     */
    public final String getTitle() {
        return title;
    }

    /**
     * Set window title.
     *
     * @param title new window title
     */
    public final void setTitle(final String title) {
        this.title = title;
    }

    // ------------------------------------------------------------------------
    // TApplication integration -----------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Window's parent TApplication.
     */
    private TApplication application;

    /**
     * Get this TWindow's parent TApplication.
     *
     * @return this TWindow's parent TApplication
     */
    @Override
    public final TApplication getApplication() {
        return application;
    }

    /**
     * Get the Screen.
     *
     * @return the Screen
     */
    @Override
    public final Screen getScreen() {
        return application.getScreen();
    }

    /**
     * Z order.  Lower number means more in-front.
     */
    private int z = 0;

    /**
     * Get Z order.  Lower number means more in-front.
     *
     * @return Z value.  Lower number means more in-front.
     */
    public final int getZ() {
        return z;
    }

    /**
     * Set Z order.  Lower number means more in-front.
     *
     * @param z the new Z value.  Lower number means more in-front.
     */
    public final void setZ(final int z) {
        this.z = z;
    }

    /**
     * Window's keyboard shortcuts.  Any key in this set will be passed to
     * the window directly rather than processed through the menu
     * accelerators.
     */
    private HashSet<TKeypress> keyboardShortcuts = new HashSet<TKeypress>();

    /**
     * Add a keypress to be overridden for this window.
     *
     * @param key the key to start taking control of
     */
    protected void addShortcutKeypress(final TKeypress key) {
        keyboardShortcuts.add(key);
    }

    /**
     * Remove a keypress to be overridden for this window.
     *
     * @param key the key to stop taking control of
     */
    protected void removeShortcutKeypress(final TKeypress key) {
        keyboardShortcuts.remove(key);
    }

    /**
     * Remove all keypresses to be overridden for this window.
     */
    protected void clearShortcutKeypresses() {
        keyboardShortcuts.clear();
    }

    /**
     * Determine if a keypress is overridden for this window.
     *
     * @param key the key to check
     * @return true if this window wants to process this key on its own
     */
    public boolean isShortcutKeypress(final TKeypress key) {
        return keyboardShortcuts.contains(key);
    }

    /**
     * A window may have a status bar associated with it.  TApplication will
     * draw this status bar last, and will also route events to it first
     * before the window.
     */
    protected TStatusBar statusBar = null;

    /**
     * Get the window's status bar, or null if it does not have one.
     *
     * @return the status bar, or null
     */
    public TStatusBar getStatusBar() {
        return statusBar;
    }

    /**
     * Set the window's status bar to a new one.
     *
     * @param text the status bar text
     * @return the status bar
     */
    public TStatusBar newStatusBar(final String text) {
        statusBar = new TStatusBar(this, text);
        return statusBar;
    }

    // ------------------------------------------------------------------------
    // Window movement/resizing support ---------------------------------------
    // ------------------------------------------------------------------------

    /**
     * If true, then the user clicked on the title bar and is moving the
     * window.
     */
    protected boolean inWindowMove = false;

    /**
     * If true, then the user clicked on the bottom right corner and is
     * resizing the window.
     */
    protected boolean inWindowResize = false;

    /**
     * If true, then the user selected "Size/Move" (or hit Ctrl-F5) and is
     * resizing/moving the window via the keyboard.
     */
    private boolean inKeyboardResize = false;

    /**
     * If true, this window is maximized.
     */
    private boolean maximized = false;

    /**
     * Remember mouse state.
     */
    protected TMouseEvent mouse;

    // For moving the window.  resizing also uses moveWindowMouseX/Y
    private int moveWindowMouseX;
    private int moveWindowMouseY;
    private int oldWindowX;
    private int oldWindowY;

    // Resizing
    private int resizeWindowWidth;
    private int resizeWindowHeight;
    private int minimumWindowWidth = 10;
    private int minimumWindowHeight = 2;
    private int maximumWindowWidth = -1;
    private int maximumWindowHeight = -1;

    // For maximize/restore
    private int restoreWindowWidth;
    private int restoreWindowHeight;
    private int restoreWindowX;
    private int restoreWindowY;

    /**
     * Set the maximum width for this window.
     *
     * @param maximumWindowWidth new maximum width
     */
    public final void setMaximumWindowWidth(final int maximumWindowWidth) {
        this.maximumWindowWidth = maximumWindowWidth;
    }

    /**
     * Recenter the window on-screen.
     */
    public final void center() {
        if ((flags & CENTERED) != 0) {
            if (getWidth() < getScreen().getWidth()) {
                setX((getScreen().getWidth() - getWidth()) / 2);
            } else {
                setX(0);
            }
            setY(((application.getDesktopBottom()
                    - application.getDesktopTop()) - getHeight()) / 2);
            if (getY() < 0) {
                setY(0);
            }
            setY(getY() + application.getDesktopTop());
        }
    }

    /**
     * Maximize window to the current maximum size.
     */
    public void maximize() {
    	if (!maximized) {
	        restoreWindowWidth = getWidth();
	        restoreWindowHeight = getHeight();
	        restoreWindowX = getX();
	        restoreWindowY = getY();
    	}
    	
        setWidth(getScreen().getWidth());
        setHeight(application.getDesktopBottom() - 1);
        setX(0);
        setY(1);
        
        maximized = true;
    }

    /**
     * Restore (unmaximize) window.
     */
    public void restore() {
    	if (maximized) {
	        setWidth(restoreWindowWidth);
	        setHeight(restoreWindowHeight);
	        setX(restoreWindowX);
	        setY(restoreWindowY);
    	}
    	
        maximized = false;
    }

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.  Window will be located at (0, 0).
     *
     * @param application TApplication that manages this window
     * @param title window title, will be centered along the top border
     * @param width width of window
     * @param height height of window
     */
    public TWindow(final TApplication application, final String title,
        final int width, final int height) {

        this(application, title, 0, 0, width, height, RESIZABLE);
    }

    /**
     * Public constructor.  Window will be located at (0, 0).
     *
     * @param application TApplication that manages this window
     * @param title window title, will be centered along the top border
     * @param width width of window
     * @param height height of window
     * @param flags bitmask of RESIZABLE, CENTERED, or MODAL
     */
    public TWindow(final TApplication application, final String title,
        final int width, final int height, final int flags) {

        this(application, title, 0, 0, width, height, flags);
    }

    /**
     * Public constructor.
     *
     * @param application TApplication that manages this window
     * @param title window title, will be centered along the top border
     * @param x column relative to parent
     * @param y row relative to parent
     * @param width width of window
     * @param height height of window
     */
    public TWindow(final TApplication application, final String title,
        final int x, final int y, final int width, final int height) {

        this(application, title, x, y, width, height, RESIZABLE);
    }

    /**
     * Public constructor.
     *
     * @param application TApplication that manages this window
     * @param title window title, will be centered along the top border
     * @param x column relative to parent
     * @param y row relative to parent
     * @param width width of window
     * @param height height of window
     * @param flags mask of RESIZABLE, CENTERED, or MODAL
     */
    public TWindow(final TApplication application, final String title,
        final int x, final int y, final int width, final int height,
        final int flags) {

        super();

        // I am my own window and parent
        setupForTWindow(this, x, y + application.getDesktopTop(),
            width, height);

        // Save fields
        this.title       = title;
        this.application = application;
        this.flags       = flags;

        // Minimum width/height are 10 and 2
        assert (width >= 10);
        assert (getHeight() >= 2);

        // MODAL implies CENTERED
        if (isModal()) {
            this.flags |= CENTERED;
        }

        // Center window if specified
        center();

        // Add me to the application
        application.addWindow(this);
    }

    // ------------------------------------------------------------------------
    // General behavior -------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Returns true if this window is modal.
     *
     * @return true if this window is modal
     */
    public final boolean isModal() {
        if ((flags & MODAL) == 0) {
            return false;
        }
        return true;
    }
    
    /**
     * This window cannot be closed.
     * @return TRUE if it cannot be closed
     */
    public boolean isUnclosable() {
    	return (flags & UNCLOSABLE) != 0;
    }

    /**
     * Retrieve the background color.
     *
     * @return the background color
     */
    public final CellAttributes getBackground() {
        if (!isModal()
            && (inWindowMove || inWindowResize || inKeyboardResize)
        ) {
            assert (isActive());
            return getTheme().getColor("twindow.background.windowmove");
        } else if (isModal() && inWindowMove) {
            assert (isActive());
            return getTheme().getColor("twindow.background.modal");
        } else if (isModal()) {
            if (isActive()) {
                return getTheme().getColor("twindow.background.modal");
            }
            return getTheme().getColor("twindow.background.modal.inactive");
        } else if (isActive()) {
            assert (!isModal());
            return getTheme().getColor("twindow.background");
        } else {
            assert (!isModal());
            return getTheme().getColor("twindow.background.inactive");
        }
    }

    /**
     * Retrieve the border color.
     *
     * @return the border color
     */
    public CellAttributes getBorder() {
        if (!isModal()
            && (inWindowMove || inWindowResize || inKeyboardResize)
        ) {
            assert (isActive());
            return getTheme().getColor("twindow.border.windowmove");
        } else if (isModal() && inWindowMove) {
            assert (isActive());
            return getTheme().getColor("twindow.border.modal.windowmove");
        } else if (isModal()) {
            if (isActive()) {
                return getTheme().getColor("twindow.border.modal");
            } else {
                return getTheme().getColor("twindow.border.modal.inactive");
            }
        } else if (isActive()) {
            assert (!isModal());
            return getTheme().getColor("twindow.border");
        } else {
            assert (!isModal());
            return getTheme().getColor("twindow.border.inactive");
        }
    }

    /**
     * Retrieve the border line type.
     *
     * @return the border line type
     */
    private int getBorderType() {
        if (!isModal()
            && (inWindowMove || inWindowResize || inKeyboardResize)
        ) {
            assert (isActive());
            return 1;
        } else if (isModal() && inWindowMove) {
            assert (isActive());
            return 1;
        } else if (isModal()) {
            if (isActive()) {
                return 2;
            } else {
                return 1;
            }
        } else if (isActive()) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * Called by TApplication.drawChildren() to render on screen.
     */
    @Override
    public void draw() {
        // Draw the box and background first.
        CellAttributes border = getBorder();
        CellAttributes background = getBackground();
        int borderType = getBorderType();

        getScreen().drawBox(0, 0, getWidth(), getHeight(), border,
            background, borderType, true);

        // Draw the title
        int titleLeft = (getWidth() - title.length() - 2) / 2;
        putCharXY(titleLeft, 0, ' ', border);
        putStringXY(titleLeft + 1, 0, title);
        putCharXY(titleLeft + title.length() + 1, 0, ' ', border);

        if (isActive()) {
        	if (!isUnclosable()) {
	            // Draw the close button
	            putCharXY(2, 0, '[', border);
	            putCharXY(4, 0, ']', border);
	            if (mouseOnClose() && mouse.isMouse1()) {
	                putCharXY(3, 0, GraphicsChars.CP437[0x0F],
	                    !isModal()
	                    ? getTheme().getColor("twindow.border.windowmove")
	                    : getTheme().getColor("twindow.border.modal.windowmove"));
	            } else {
	                putCharXY(3, 0, GraphicsChars.CP437[0xFE],
	                    !isModal()
	                    ? getTheme().getColor("twindow.border.windowmove")
	                    : getTheme().getColor("twindow.border.modal.windowmove"));
	            }
        	}
        	
            // Draw the maximize button
            if (!isModal()) {

                putCharXY(getWidth() - 5, 0, '[', border);
                putCharXY(getWidth() - 3, 0, ']', border);
                if (mouseOnMaximize() && mouse.isMouse1()) {
                    putCharXY(getWidth() - 4, 0, GraphicsChars.CP437[0x0F],
                        getTheme().getColor("twindow.border.windowmove"));
                } else {
                    if (maximized) {
                        putCharXY(getWidth() - 4, 0, GraphicsChars.CP437[0x12],
                            getTheme().getColor("twindow.border.windowmove"));
                    } else {
                        putCharXY(getWidth() - 4, 0, GraphicsChars.UPARROW,
                            getTheme().getColor("twindow.border.windowmove"));
                    }
                }

                // Draw the resize corner
                if ((flags & RESIZABLE) != 0) {
                    putCharXY(getWidth() - 2, getHeight() - 1,
                        GraphicsChars.SINGLE_BAR,
                        getTheme().getColor("twindow.border.windowmove"));
                    putCharXY(getWidth() - 1, getHeight() - 1,
                        GraphicsChars.LRCORNER,
                        getTheme().getColor("twindow.border.windowmove"));
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Event handlers ---------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Returns true if the mouse is currently on the close button.
     *
     * @return true if mouse is currently on the close button
     */
    private boolean mouseOnClose() {
    	if (((flags & UNCLOSABLE) == 0)) {
	        if ((mouse != null)
	            && (mouse.getAbsoluteY() == getY())
	            && (mouse.getAbsoluteX() == getX() + 3)
	        ) {
	            return true;
	        }
    	}
    	
        return false;
    }

    /**
     * Returns true if the mouse is currently on the maximize/restore button.
     *
     * @return true if the mouse is currently on the maximize/restore button
     */
    private boolean mouseOnMaximize() {
        if ((mouse != null)
            && !isModal()
            && (mouse.getAbsoluteY() == getY())
            && (mouse.getAbsoluteX() == getX() + getWidth() - 4)
        ) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the mouse is currently on the resizable lower right
     * corner.
     *
     * @return true if the mouse is currently on the resizable lower right
     * corner
     */
    private boolean mouseOnResize() {
        if (((flags & RESIZABLE) != 0)
            && !isModal()
            && (mouse != null)
            && (mouse.getAbsoluteY() == getY() + getHeight() - 1)
            && ((mouse.getAbsoluteX() == getX() + getWidth() - 1)
                || (mouse.getAbsoluteX() == getX() + getWidth() - 2))
        ) {
            return true;
        }
        return false;
    }

    /**
     * Subclasses should override this method to cleanup resources.  This is
     * called by application.closeWindow().
     */
    public void onClose() {
        // Default: do nothing
    }

    /**
     * Called by application.switchWindow() when this window gets the
     * focus, and also by application.addWindow().
     */
    public void onFocus() {
        // Default: do nothing
    }

    /**
     * Called by application.switchWindow() when another window gets the
     * focus.
     */
    public void onUnfocus() {
        // Default: do nothing
    }

    /**
     * Handle mouse button presses.
     *
     * @param mouse mouse button event
     */
    @Override
    public void onMouseDown(final TMouseEvent mouse) {
        this.mouse = mouse;

        inKeyboardResize = false;

        if ((mouse.getAbsoluteY() == getY())
            && mouse.isMouse1()
            && (getX() <= mouse.getAbsoluteX())
            && (mouse.getAbsoluteX() < getX() + getWidth())
            && !mouseOnClose()
            && !mouseOnMaximize()
        ) {
            // Begin moving window
            inWindowMove = true;
            moveWindowMouseX = mouse.getAbsoluteX();
            moveWindowMouseY = mouse.getAbsoluteY();
            oldWindowX = getX();
            oldWindowY = getY();
            if (maximized) {
                maximized = false;
            }
            return;
        }
        if (mouseOnResize()) {
            // Begin window resize
            inWindowResize = true;
            moveWindowMouseX = mouse.getAbsoluteX();
            moveWindowMouseY = mouse.getAbsoluteY();
            resizeWindowWidth = getWidth();
            resizeWindowHeight = getHeight();
            if (maximized) {
                maximized = false;
            }
            return;
        }

        // Give the shortcut bar a shot at this.
        if (statusBar != null) {
            if (statusBar.statusBarMouseDown(mouse)) {
                return;
            }
        }

        // I didn't take it, pass it on to my children
        super.onMouseDown(mouse);
    }

    /**
     * Handle mouse button releases.
     *
     * @param mouse mouse button release event
     */
    @Override
    public void onMouseUp(final TMouseEvent mouse) {
        this.mouse = mouse;

        if ((inWindowMove) && (mouse.isMouse1())) {
            // Stop moving window
            inWindowMove = false;
            return;
        }

        if ((inWindowResize) && (mouse.isMouse1())) {
            // Stop resizing window
            inWindowResize = false;
            return;
        }

        if (mouse.isMouse1() && mouseOnClose()) {
            // Close window
            application.closeWindow(this);
            return;
        }

        if ((mouse.getAbsoluteY() == getY())
            && mouse.isMouse1()
            && mouseOnMaximize()) {
            if (maximized) {
                // Restore
                restore();
            } else {
                // Maximize
                maximize();
            }
            // Pass a resize event to my children
            onResize(new TResizeEvent(TResizeEvent.Type.WIDGET,
                    getWidth(), getHeight()));
            return;
        }

        // Give the shortcut bar a shot at this.
        if (statusBar != null) {
            if (statusBar.statusBarMouseUp(mouse)) {
                return;
            }
        }

        // I didn't take it, pass it on to my children
        super.onMouseUp(mouse);
    }

    /**
     * Handle mouse movements.
     *
     * @param mouse mouse motion event
     */
    @Override
    public void onMouseMotion(final TMouseEvent mouse) {
        this.mouse = mouse;

        if (inWindowMove) {
            // Move window over
            setX(oldWindowX + (mouse.getAbsoluteX() - moveWindowMouseX));
            setY(oldWindowY + (mouse.getAbsoluteY() - moveWindowMouseY));
            // Don't cover up the menu bar
            if (getY() < application.getDesktopTop()) {
                setY(application.getDesktopTop());
            }
            // Don't go below the status bar
            if (getY() >= application.getDesktopBottom()) {
                setY(application.getDesktopBottom() - 1);
            }
            return;
        }

        if (inWindowResize) {
            // Move window over
            setWidth(resizeWindowWidth + (mouse.getAbsoluteX()
                    - moveWindowMouseX));
            setHeight(resizeWindowHeight + (mouse.getAbsoluteY()
                    - moveWindowMouseY));
            if (getX() + getWidth() > getScreen().getWidth()) {
                setWidth(getScreen().getWidth() - getX());
            }
            if (getY() + getHeight() > application.getDesktopBottom()) {
                setY(application.getDesktopBottom() - getHeight() + 1);
            }
            // Don't cover up the menu bar
            if (getY() < application.getDesktopTop()) {
                setY(application.getDesktopTop());
            }

            // Keep within min/max bounds
            if (getWidth() < minimumWindowWidth) {
                setWidth(minimumWindowWidth);
                inWindowResize = false;
            }
            if (getHeight() < minimumWindowHeight) {
                setHeight(minimumWindowHeight);
                inWindowResize = false;
            }
            if ((maximumWindowWidth > 0)
                && (getWidth() > maximumWindowWidth)
            ) {
                setWidth(maximumWindowWidth);
                inWindowResize = false;
            }
            if ((maximumWindowHeight > 0)
                && (getHeight() > maximumWindowHeight)
            ) {
                setHeight(maximumWindowHeight);
                inWindowResize = false;
            }

            // Pass a resize event to my children
            onResize(new TResizeEvent(TResizeEvent.Type.WIDGET,
                    getWidth(), getHeight()));
            return;
        }

        // Give the shortcut bar a shot at this.
        if (statusBar != null) {
            statusBar.statusBarMouseMotion(mouse);
        }

        // I didn't take it, pass it on to my children
        super.onMouseMotion(mouse);
    }

    /**
     * Handle keystrokes.
     *
     * @param keypress keystroke event
     */
    @Override
    public void onKeypress(final TKeypressEvent keypress) {

        if (inKeyboardResize) {

            // ESC or ENTER - Exit size/move
            if (keypress.equals(kbEsc) || keypress.equals(kbEnter)) {
                inKeyboardResize = false;
            }

            if (keypress.equals(kbLeft)) {
                if (getX() > 0) {
                    setX(getX() - 1);
                }
            }
            if (keypress.equals(kbRight)) {
                if (getX() < getScreen().getWidth() - 1) {
                    setX(getX() + 1);
                }
            }
            if (keypress.equals(kbDown)) {
                if (getY() < application.getDesktopBottom() - 1) {
                    setY(getY() + 1);
                }
            }
            if (keypress.equals(kbUp)) {
                if (getY() > 1) {
                    setY(getY() - 1);
                }
            }
            if (keypress.equals(kbShiftLeft)) {
                if ((getWidth() > minimumWindowWidth)
                    || (minimumWindowWidth <= 0)
                ) {
                    setWidth(getWidth() - 1);
                }
            }
            if (keypress.equals(kbShiftRight)) {
                if ((getWidth() < maximumWindowWidth)
                    || (maximumWindowWidth <= 0)
                ) {
                    setWidth(getWidth() + 1);
                }
            }
            if (keypress.equals(kbShiftUp)) {
                if ((getHeight() > minimumWindowHeight)
                    || (minimumWindowHeight <= 0)
                ) {
                    setHeight(getHeight() - 1);
                }
            }
            if (keypress.equals(kbShiftDown)) {
                if ((getHeight() < maximumWindowHeight)
                    || (maximumWindowHeight <= 0)
                ) {
                    setHeight(getHeight() + 1);
                }
            }

            // Pass a resize event to my children
            onResize(new TResizeEvent(TResizeEvent.Type.WIDGET,
                    getWidth(), getHeight()));

            return;
        }

        // Give the shortcut bar a shot at this.
        if (statusBar != null) {
            if (statusBar.statusBarKeypress(keypress)) {
                return;
            }
        }

        // These keystrokes will typically not be seen unless a subclass
        // overrides onMenu() due to how TApplication dispatches
        // accelerators.

        // Ctrl-W - close window
        if (keypress.equals(kbCtrlW)) {
            application.closeWindow(this);
            return;
        }

        // F6 - behave like Alt-TAB
        if (keypress.equals(kbF6)) {
            application.switchWindow(true);
            return;
        }

        // Shift-F6 - behave like Shift-Alt-TAB
        if (keypress.equals(kbShiftF6)) {
            application.switchWindow(false);
            return;
        }

        // F5 - zoom
        if (keypress.equals(kbF5)) {
            if (maximized) {
                restore();
            } else {
                maximize();
            }
        }

        // Ctrl-F5 - size/move
        if (keypress.equals(kbCtrlF5)) {
            inKeyboardResize = !inKeyboardResize;
        }

        // I didn't take it, pass it on to my children
        super.onKeypress(keypress);
    }

    /**
     * Handle posted command events.
     *
     * @param command command event
     */
    @Override
    public void onCommand(final TCommandEvent command) {

        // These commands will typically not be seen unless a subclass
        // overrides onMenu() due to how TApplication dispatches
        // accelerators.

        if (command.equals(cmWindowClose)) {
            application.closeWindow(this);
            return;
        }

        if (command.equals(cmWindowNext)) {
            application.switchWindow(true);
            return;
        }

        if (command.equals(cmWindowPrevious)) {
            application.switchWindow(false);
            return;
        }

        if (command.equals(cmWindowMove)) {
            inKeyboardResize = true;
            return;
        }

        if (command.equals(cmWindowZoom)) {
            if (maximized) {
                restore();
            } else {
                maximize();
            }
        }

        // I didn't take it, pass it on to my children
        super.onCommand(command);
    }

    /**
     * Handle posted menu events.
     *
     * @param menu menu event
     */
    @Override
    public void onMenu(final TMenuEvent menu) {
        if (menu.getId() == TMenu.MID_WINDOW_CLOSE) {
            application.closeWindow(this);
            return;
        }

        if (menu.getId() == TMenu.MID_WINDOW_NEXT) {
            application.switchWindow(true);
            return;
        }

        if (menu.getId() == TMenu.MID_WINDOW_PREVIOUS) {
            application.switchWindow(false);
            return;
        }

        if (menu.getId() == TMenu.MID_WINDOW_MOVE) {
            inKeyboardResize = true;
            return;
        }

        if (menu.getId() == TMenu.MID_WINDOW_ZOOM) {
            if (maximized) {
                restore();
            } else {
                maximize();
            }
            return;
        }

        // I didn't take it, pass it on to my children
        super.onMenu(menu);
    }

    // ------------------------------------------------------------------------
    // Passthru for Screen functions ------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Get the attributes at one location.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @return attributes at (x, y)
     */
    public final CellAttributes getAttrXY(final int x, final int y) {
        return getScreen().getAttrXY(x, y);
    }

    /**
     * Set the attributes at one location.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param attr attributes to use (bold, foreColor, backColor)
     */
    public final void putAttrXY(final int x, final int y,
        final CellAttributes attr) {

        getScreen().putAttrXY(x, y, attr);
    }

    /**
     * Set the attributes at one location.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param attr attributes to use (bold, foreColor, backColor)
     * @param clip if true, honor clipping/offset
     */
    public final void putAttrXY(final int x, final int y,
        final CellAttributes attr, final boolean clip) {

        getScreen().putAttrXY(x, y, attr, clip);
    }

    /**
     * Fill the entire screen with one character with attributes.
     *
     * @param ch character to draw
     * @param attr attributes to use (bold, foreColor, backColor)
     */
    public final void putAll(final char ch, final CellAttributes attr) {
        getScreen().putAll(ch, attr);
    }

    /**
     * Render one character with attributes.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param ch character + attributes to draw
     */
    public final void putCharXY(final int x, final int y, final Cell ch) {
        getScreen().putCharXY(x, y, ch);
    }

    /**
     * Render one character with attributes.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param ch character to draw
     * @param attr attributes to use (bold, foreColor, backColor)
     */
    public final void putCharXY(final int x, final int y, final char ch,
        final CellAttributes attr) {

        getScreen().putCharXY(x, y, ch, attr);
    }

    /**
     * Render one character without changing the underlying attributes.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param ch character to draw
     */
    public final void putCharXY(final int x, final int y, final char ch) {
        getScreen().putCharXY(x, y, ch);
    }

    /**
     * Render a string.  Does not wrap if the string exceeds the line.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param str string to draw
     * @param attr attributes to use (bold, foreColor, backColor)
     */
    public final void putStringXY(final int x, final int y, final String str,
        final CellAttributes attr) {

        getScreen().putStringXY(x, y, str, attr);
    }

    /**
     * Render a string without changing the underlying attribute.  Does not
     * wrap if the string exceeds the line.
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param str string to draw
     */
    public final void putStringXY(final int x, final int y, final String str) {
        getScreen().putStringXY(x, y, str);
    }

    /**
     * Draw a vertical line from (x, y) to (x, y + n).
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param n number of characters to draw
     * @param ch character to draw
     * @param attr attributes to use (bold, foreColor, backColor)
     */
    public final void vLineXY(final int x, final int y, final int n,
        final char ch, final CellAttributes attr) {

        getScreen().vLineXY(x, y, n, ch, attr);
    }

    /**
     * Draw a horizontal line from (x, y) to (x + n, y).
     *
     * @param x column coordinate.  0 is the left-most column.
     * @param y row coordinate.  0 is the top-most row.
     * @param n number of characters to draw
     * @param ch character to draw
     * @param attr attributes to use (bold, foreColor, backColor)
     */
    public final void hLineXY(final int x, final int y, final int n,
        final char ch, final CellAttributes attr) {

        getScreen().hLineXY(x, y, n, ch, attr);
    }


}
