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

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jexer.bits.CellAttributes;
import jexer.bits.ColorTheme;
import jexer.bits.GraphicsChars;
import jexer.event.TCommandEvent;
import jexer.event.TInputEvent;
import jexer.event.TKeypressEvent;
import jexer.event.TMenuEvent;
import jexer.event.TMouseEvent;
import jexer.event.TResizeEvent;
import jexer.backend.Backend;
import jexer.backend.SwingBackend;
import jexer.backend.ECMA48Backend;
import jexer.io.Screen;
import jexer.menu.TMenu;
import jexer.menu.TMenuItem;
import static jexer.TCommand.*;
import static jexer.TKeypress.*;

/**
 * TApplication sets up a full Text User Interface application.
 */
public class TApplication implements Runnable {

    // ------------------------------------------------------------------------
    // Public constants -------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * If true, emit thread stuff to System.err.
     */
    private static final boolean debugThreads = false;

    /**
     * If true, emit events being processed to System.err.
     */
    private static final boolean debugEvents = false;

    /**
     * If true, do "smart placement" on new windows that are not specified to
     * be centered.
     */
    private static final boolean smartWindowPlacement = true;

    /**
     * Two backend types are available.
     */
    public static enum BackendType {
        /**
         * A Swing JFrame.
         */
        SWING,

        /**
         * An ECMA48 / ANSI X3.64 / XTERM style terminal.
         */
        ECMA48,

        /**
         * Synonym for ECMA48.
         */
        XTERM
    }

    // ------------------------------------------------------------------------
    // Primary/secondary event handlers ---------------------------------------
    // ------------------------------------------------------------------------

    /**
     * WidgetEventHandler is the main event consumer loop.  There are at most
     * two such threads in existence: the primary for normal case and a
     * secondary that is used for TMessageBox, TInputBox, and similar.
     */
    private class WidgetEventHandler implements Runnable {
        /**
         * The main application.
         */
        private TApplication application;

        /**
         * Whether or not this WidgetEventHandler is the primary or secondary
         * thread.
         */
        private boolean primary = true;

        /**
         * Public constructor.
         *
         * @param application the main application
         * @param primary if true, this is the primary event handler thread
         */
        public WidgetEventHandler(final TApplication application,
            final boolean primary) {

            this.application = application;
            this.primary = primary;
        }

        /**
         * The consumer loop.
         */
        public void run() {

            // Loop forever
            while (!application.quit) {

                // Wait until application notifies me
                while (!application.quit) {
                    try {
                        synchronized (application.drainEventQueue) {
                            if (application.drainEventQueue.size() > 0) {
                                break;
                            }
                        }

                        synchronized (this) {
                            if (debugThreads) {
                                System.err.printf("%s %s sleep\n", this,
                                    primary ? "primary" : "secondary");
                            }

                            this.wait();

                            if (debugThreads) {
                                System.err.printf("%s %s AWAKE\n", this,
                                    primary ? "primary" : "secondary");
                            }

                            if ((!primary)
                                && (application.secondaryEventReceiver == null)
                            ) {
                                // Secondary thread, emergency exit.  If we
                                // got here then something went wrong with
                                // the handoff between yield() and
                                // closeWindow().
                                synchronized (application.primaryEventHandler) {
                                    application.primaryEventHandler.notify();
                                }
                                application.secondaryEventHandler = null;
                                throw new RuntimeException(
                                        "secondary exited at wrong time");
                            }
                            break;
                        }
                    } catch (InterruptedException e) {
                        // SQUASH
                    }
                }

                // Wait for drawAll() or doIdle() to be done, then handle the
                // events.
                boolean oldLock = lockHandleEvent();
                assert (oldLock == false);

                // Pull all events off the queue
                for (;;) {
                    TInputEvent event = null;
                    synchronized (application.drainEventQueue) {
                        if (application.drainEventQueue.size() == 0) {
                            break;
                        }
                        event = application.drainEventQueue.remove(0);
                    }
                    application.repaint = true;
                    if (primary) {
                        primaryHandleEvent(event);
                    } else {
                        secondaryHandleEvent(event);
                    }
                    if ((!primary)
                        && (application.secondaryEventReceiver == null)
                    ) {
                        // Secondary thread, time to exit.

                        // DO NOT UNLOCK.  Primary thread just came back from
                        // primaryHandleEvent() and will unlock in the else
                        // block below.  Just wake it up.
                        synchronized (application.primaryEventHandler) {
                            application.primaryEventHandler.notify();
                        }
                        // Now eliminate my reference so that
                        // wakeEventHandler() resumes working on the primary.
                        application.secondaryEventHandler = null;

                        // All done!
                        return;
                    }
                } // for (;;)

                // Unlock.  Either I am primary thread, or I am secondary
                // thread and still running.
                oldLock = unlockHandleEvent();
                assert (oldLock == true);

                // I have done some work of some kind.  Tell the main run()
                // loop to wake up now.
                synchronized (application) {
                    application.notify();
                }

            } // while (true) (main runnable loop)
        }
    }

    /**
     * The primary event handler thread.
     */
    private volatile WidgetEventHandler primaryEventHandler;

    /**
     * The secondary event handler thread.
     */
    private volatile WidgetEventHandler secondaryEventHandler;

    /**
     * The widget receiving events from the secondary event handler thread.
     */
    private volatile TWidget secondaryEventReceiver;

    /**
     * Spinlock for the primary and secondary event handlers.
     * WidgetEventHandler.run() is responsible for setting this value.
     */
    private volatile boolean insideHandleEvent = false;

    /**
     * Wake the sleeping active event handler.
     */
    private void wakeEventHandler() {
        if (secondaryEventHandler != null) {
            synchronized (secondaryEventHandler) {
                secondaryEventHandler.notify();
            }
        } else {
            assert (primaryEventHandler != null);
            synchronized (primaryEventHandler) {
                primaryEventHandler.notify();
            }
        }
    }

    /**
     * Set the insideHandleEvent flag to true.  lockoutEventHandlers() will
     * spin indefinitely until unlockHandleEvent() is called.
     *
     * @return the old value of insideHandleEvent
     */
    private boolean lockHandleEvent() {
        if (debugThreads) {
            System.err.printf("  >> lockHandleEvent(): oldValue %s",
                insideHandleEvent);
        }
        boolean oldValue = true;

        synchronized (this) {
            // Wait for TApplication.run() to finish using the global state
            // before allowing further event processing.
            while (lockoutHandleEvent == true) {
                try {
                    // Backoff so that the backend can finish its work.
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    // SQUASH
                }
            }

            oldValue = insideHandleEvent;
            insideHandleEvent = true;
        }

        if (debugThreads) {
            System.err.printf(" ***\n");
        }
        return oldValue;
    }

    /**
     * Set the insideHandleEvent flag to false.  lockoutEventHandlers() will
     * spin indefinitely until unlockHandleEvent() is called.
     *
     * @return the old value of insideHandleEvent
     */
    private boolean unlockHandleEvent() {
        if (debugThreads) {
            System.err.printf("  << unlockHandleEvent(): oldValue %s\n",
                insideHandleEvent);
        }
        synchronized (this) {
            boolean oldValue = insideHandleEvent;
            insideHandleEvent = false;
            return oldValue;
        }
    }

    /**
     * Spinlock for the primary and secondary event handlers.  When true, the
     * event handlers will spinlock wait before calling handleEvent().
     */
    private volatile boolean lockoutHandleEvent = false;

    /**
     * TApplication.run() needs to be able rely on the global data structures
     * being intact when calling doIdle() and drawAll().  Tell the event
     * handlers to wait for an unlock before handling their events.
     */
    private void stopEventHandlers() {
        if (debugThreads) {
            System.err.printf(">> stopEventHandlers()");
        }

        lockoutHandleEvent = true;
        // Wait for the last event to finish processing before returning
        // control to TApplication.run().
        while (insideHandleEvent == true) {
            try {
                // Backoff so that the event handler can finish its work.
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // SQUASH
            }
        }

        if (debugThreads) {
            System.err.printf(" XXX\n");
        }
    }

    /**
     * TApplication.run() needs to be able rely on the global data structures
     * being intact when calling doIdle() and drawAll().  Tell the event
     * handlers that it is now OK to handle their events.
     */
    private void startEventHandlers() {
        if (debugThreads) {
            System.err.printf("<< startEventHandlers()\n");
        }
        lockoutHandleEvent = false;
    }

    // ------------------------------------------------------------------------
    // TApplication attributes ------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Access to the physical screen, keyboard, and mouse.
     */
    private Backend backend;

    /**
     * Get the Backend.
     *
     * @return the Backend
     */
    public final Backend getBackend() {
        return backend;
    }

    /**
     * Get the Screen.
     *
     * @return the Screen
     */
    public final Screen getScreen() {
        return backend.getScreen();
    }

    /**
     * Actual mouse coordinate X.
     */
    private int mouseX;

    /**
     * Actual mouse coordinate Y.
     */
    private int mouseY;

    /**
     * Old version of mouse coordinate X.
     */
    private int oldMouseX;

    /**
     * Old version mouse coordinate Y.
     */
    private int oldMouseY;

    /**
     * Event queue that is filled by run().
     */
    private List<TInputEvent> fillEventQueue;

    /**
     * Event queue that will be drained by either primary or secondary
     * Thread.
     */
    private List<TInputEvent> drainEventQueue;

    /**
     * Top-level menus in this application.
     */
    private List<TMenu> menus;

    /**
     * Stack of activated sub-menus in this application.
     */
    private List<TMenu> subMenus;

    /**
     * The currently acive menu.
     */
    private TMenu activeMenu = null;

    /**
     * Active keyboard accelerators.
     */
    private Map<TKeypress, TMenuItem> accelerators;

    /**
     * All menu items.
     */
    private List<TMenuItem> menuItems;

    /**
     * Windows and widgets pull colors from this ColorTheme.
     */
    private ColorTheme theme;

    /**
     * Get the color theme.
     *
     * @return the theme
     */
    public final ColorTheme getTheme() {
        return theme;
    }

    /**
     * The top-level windows (but not menus).
     */
    private List<TWindow> windows;

    /**
     * Timers that are being ticked.
     */
    private List<TTimer> timers;

    /**
     * When true, exit the application.
     */
    private volatile boolean quit = false;

    /**
     * When true, repaint the entire screen.
     */
    private volatile boolean repaint = true;

    /**
     * Y coordinate of the top edge of the desktop.  For now this is a
     * constant.  Someday it would be nice to have a multi-line menu or
     * toolbars.
     */
    private static final int desktopTop = 1;

    /**
     * Get Y coordinate of the top edge of the desktop.
     *
     * @return Y coordinate of the top edge of the desktop
     */
    public final int getDesktopTop() {
        return desktopTop;
    }

    /**
     * Y coordinate of the bottom edge of the desktop.
     */
    private int desktopBottom;

    /**
     * Get Y coordinate of the bottom edge of the desktop.
     *
     * @return Y coordinate of the bottom edge of the desktop
     */
    public final int getDesktopBottom() {
        return desktopBottom;
    }

    // ------------------------------------------------------------------------
    // General behavior -------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Display the about dialog.
     */
    protected void showAboutDialog() {
        messageBox("About", "Jexer Version " +
            this.getClass().getPackage().getImplementationVersion(),
            TMessageBox.Type.OK);
    }

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param backendType BackendType.XTERM, BackendType.ECMA48 or
     * BackendType.SWING
     * @throws UnsupportedEncodingException if an exception is thrown when
     * creating the InputStreamReader
     */
    public TApplication(final BackendType backendType)
        throws UnsupportedEncodingException {

        switch (backendType) {
        case SWING:
            backend = new SwingBackend(this);
            break;
        case XTERM:
            // Fall through...
        case ECMA48:
            backend = new ECMA48Backend(this, null, null);
            break;
        default:
            throw new IllegalArgumentException("Invalid backend type: "
                + backendType);
        }
        TApplicationImpl();
    }

    /**
     * Public constructor.  The backend type will be BackendType.ECMA48.
     *
     * @param input an InputStream connected to the remote user, or null for
     * System.in.  If System.in is used, then on non-Windows systems it will
     * be put in raw mode; shutdown() will (blindly!) put System.in in cooked
     * mode.  input is always converted to a Reader with UTF-8 encoding.
     * @param output an OutputStream connected to the remote user, or null
     * for System.out.  output is always converted to a Writer with UTF-8
     * encoding.
     * @throws UnsupportedEncodingException if an exception is thrown when
     * creating the InputStreamReader
     */
    public TApplication(final InputStream input,
        final OutputStream output) throws UnsupportedEncodingException {

        backend = new ECMA48Backend(this, input, output);
        TApplicationImpl();
    }

    /**
     * Public constructor.  The backend type will be BackendType.ECMA48.
     *
     * @param input the InputStream underlying 'reader'.  Its available()
     * method is used to determine if reader.read() will block or not.
     * @param reader a Reader connected to the remote user.
     * @param writer a PrintWriter connected to the remote user.
     * @param setRawMode if true, set System.in into raw mode with stty.
     * This should in general not be used.  It is here solely for Demo3,
     * which uses System.in.
     * @throws IllegalArgumentException if input, reader, or writer are null.
     */
    public TApplication(final InputStream input, final Reader reader,
        final PrintWriter writer, final boolean setRawMode) {

        backend = new ECMA48Backend(this, input, reader, writer, setRawMode);
        TApplicationImpl();
    }

    /**
     * Public constructor.  The backend type will be BackendType.ECMA48.
     *
     * @param input the InputStream underlying 'reader'.  Its available()
     * method is used to determine if reader.read() will block or not.
     * @param reader a Reader connected to the remote user.
     * @param writer a PrintWriter connected to the remote user.
     * @throws IllegalArgumentException if input, reader, or writer are null.
     */
    public TApplication(final InputStream input, final Reader reader,
        final PrintWriter writer) {

        this(input, reader, writer, false);
    }

    /**
     * Public constructor.  This hook enables use with new non-Jexer
     * backends.
     *
     * @param backend a Backend that is already ready to go.
     */
    public TApplication(final Backend backend) {
        this.backend = backend;
        TApplicationImpl();
    }

    /**
     * Finish construction once the backend is set.
     */
    private void TApplicationImpl() {
        theme           = new ColorTheme();
        desktopBottom   = getScreen().getHeight() - 1;
        fillEventQueue  = new ArrayList<TInputEvent>();
        drainEventQueue = new ArrayList<TInputEvent>();
        windows         = new LinkedList<TWindow>();
        menus           = new LinkedList<TMenu>();
        subMenus        = new LinkedList<TMenu>();
        timers          = new LinkedList<TTimer>();
        accelerators    = new HashMap<TKeypress, TMenuItem>();
        menuItems       = new ArrayList<TMenuItem>();

        // Setup the main consumer thread
        primaryEventHandler = new WidgetEventHandler(this, true);
        (new Thread(primaryEventHandler)).start();
    }

    // ------------------------------------------------------------------------
    // Screen refresh loop ----------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Invert the cell color at a position.  This is used to track the mouse.
     *
     * @param x column position
     * @param y row position
     */
    private void invertCell(final int x, final int y) {
        if (debugThreads) {
            System.err.printf("invertCell() %d %d\n", x, y);
        }
        CellAttributes attr = getScreen().getAttrXY(x, y);
        attr.setForeColor(attr.getForeColor().invert());
        attr.setBackColor(attr.getBackColor().invert());
        getScreen().putAttrXY(x, y, attr, false);
    }

    /**
     * Draw everything.
     */
    private void drawAll() {
        if (debugThreads) {
            System.err.printf("drawAll() enter\n");
        }

        if (!repaint) {
            if (debugThreads) {
                System.err.printf("drawAll() !repaint\n");
            }
            synchronized (getScreen()) {
                if ((oldMouseX != mouseX) || (oldMouseY != mouseY)) {
                    // The only thing that has happened is the mouse moved.
                    // Clear the old position and draw the new position.
                    invertCell(oldMouseX, oldMouseY);
                    invertCell(mouseX, mouseY);
                    oldMouseX = mouseX;
                    oldMouseY = mouseY;
                }
                if (getScreen().isDirty()) {
                    backend.flushScreen();
                }
                return;
            }
        }

        if (debugThreads) {
            System.err.printf("drawAll() REDRAW\n");
        }

        // If true, the cursor is not visible
        boolean cursor = false;

        // Start with a clean screen
        getScreen().clear();

        // Draw the background
        CellAttributes background = theme.getColor("tapplication.background");
        getScreen().putAll(GraphicsChars.HATCH, background);

        // Draw each window in reverse Z order
        List<TWindow> sorted = new LinkedList<TWindow>(windows);
        Collections.sort(sorted);
        TWindow topLevel = null;
        if (sorted.size() > 0) {
            topLevel = sorted.get(0);
        }
        Collections.reverse(sorted);
        for (TWindow window: sorted) {
            window.drawChildren();
        }

        // Draw the blank menubar line - reset the screen clipping first so
        // it won't trim it out.
        getScreen().resetClipping();
        getScreen().hLineXY(0, 0, getScreen().getWidth(), ' ',
            theme.getColor("tmenu"));
        // Now draw the menus.
        int x = 1;
        for (TMenu menu: menus) {
            CellAttributes menuColor;
            CellAttributes menuMnemonicColor;
            if (menu.isActive()) {
                menuColor = theme.getColor("tmenu.highlighted");
                menuMnemonicColor = theme.getColor("tmenu.mnemonic.highlighted");
                topLevel = menu;
            } else {
                menuColor = theme.getColor("tmenu");
                menuMnemonicColor = theme.getColor("tmenu.mnemonic");
            }
            // Draw the menu title
            getScreen().hLineXY(x, 0, menu.getTitle().length() + 2, ' ',
                menuColor);
            getScreen().putStringXY(x + 1, 0, menu.getTitle(), menuColor);
            // Draw the highlight character
            getScreen().putCharXY(x + 1 + menu.getMnemonic().getShortcutIdx(),
                0, menu.getMnemonic().getShortcut(), menuMnemonicColor);

            if (menu.isActive()) {
                menu.drawChildren();
                // Reset the screen clipping so we can draw the next title.
                getScreen().resetClipping();
            }
            x += menu.getTitle().length() + 2;
        }

        for (TMenu menu: subMenus) {
            // Reset the screen clipping so we can draw the next sub-menu.
            getScreen().resetClipping();
            menu.drawChildren();
        }

        // Draw the status bar of the top-level window
        TStatusBar statusBar = null;
        if (topLevel != null) {
            statusBar = topLevel.getStatusBar();
        }
        if (statusBar != null) {
            getScreen().resetClipping();
            statusBar.setWidth(getScreen().getWidth());
            statusBar.setY(getScreen().getHeight() - topLevel.getY());
            statusBar.draw();
        } else {
            CellAttributes barColor = new CellAttributes();
            barColor.setTo(getTheme().getColor("tstatusbar.text"));
            getScreen().hLineXY(0, desktopBottom, getScreen().getWidth(), ' ',
                barColor);
        }

        // Draw the mouse pointer
        invertCell(mouseX, mouseY);
        oldMouseX = mouseX;
        oldMouseY = mouseY;

        // Place the cursor if it is visible
        TWidget activeWidget = null;
        if (sorted.size() > 0) {
            activeWidget = sorted.get(sorted.size() - 1).getActiveChild();
            if (activeWidget.isCursorVisible()) {
                getScreen().putCursor(true, activeWidget.getCursorAbsoluteX(),
                    activeWidget.getCursorAbsoluteY());
                cursor = true;
            }
        }

        // Kill the cursor
        if (!cursor) {
            getScreen().hideCursor();
        }

        // Flush the screen contents
        if (getScreen().isDirty()) {
            backend.flushScreen();
        }

        repaint = false;
    }

    // ------------------------------------------------------------------------
    // Main loop --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Run this application until it exits.
     */
    public void run() {
        while (!quit) {
            // Timeout is in milliseconds, so default timeout after 1 second
            // of inactivity.
            long timeout = 0;

            // If I've got no updates to render, wait for something from the
            // backend or a timer.
            if (!repaint
                && ((mouseX == oldMouseX) && (mouseY == oldMouseY))
            ) {
                // Never sleep longer than 50 millis.  We need time for
                // windows with background tasks to update the display, and
                // still flip buffers reasonably quickly in
                // backend.flushPhysical().
                timeout = getSleepTime(50);
            }

            if (timeout > 0) {
                // As of now, I've got nothing to do: no I/O, nothing from
                // the consumer threads, no timers that need to run ASAP.  So
                // wait until either the backend or the consumer threads have
                // something to do.
                try {
                    if (debugThreads) {
                        System.err.println("sleep " + timeout + " millis");
                    }
                    synchronized (this) {
                        this.wait(timeout);
                    }
                } catch (InterruptedException e) {
                    // I'm awake and don't care why, let's see what's going
                    // on out there.
                }
                repaint = true;
            }

            // Prevent stepping on the primary or secondary event handler.
            stopEventHandlers();

            // Pull any pending I/O events
            backend.getEvents(fillEventQueue);

            // Dispatch each event to the appropriate handler, one at a time.
            for (;;) {
                TInputEvent event = null;
                if (fillEventQueue.size() == 0) {
                    break;
                }
                event = fillEventQueue.remove(0);
                metaHandleEvent(event);
            }

            // Wake a consumer thread if we have any pending events.
            if (drainEventQueue.size() > 0) {
                wakeEventHandler();
            }

            // Process timers and call doIdle()'s
            doIdle();

            // Update the screen
            synchronized (getScreen()) {
                drawAll();
            }

            // Let the event handlers run again.
            startEventHandlers();

        } // while (!quit)

        // Shutdown the event consumer threads
        if (secondaryEventHandler != null) {
            synchronized (secondaryEventHandler) {
                secondaryEventHandler.notify();
            }
        }
        if (primaryEventHandler != null) {
            synchronized (primaryEventHandler) {
                primaryEventHandler.notify();
            }
        }

        // Shutdown the user I/O thread(s)
        backend.shutdown();

        // Close all the windows.  This gives them an opportunity to release
        // resources.
        closeAllWindows();

    }

    /**
     * Peek at certain application-level events, add to eventQueue, and wake
     * up the consuming Thread.
     *
     * @param event the input event to consume
     */
    private void metaHandleEvent(final TInputEvent event) {

        if (debugEvents) {
            System.err.printf(String.format("metaHandleEvents event: %s\n",
                    event)); System.err.flush();
        }

        if (quit) {
            // Do no more processing if the application is already trying
            // to exit.
            return;
        }

        // Special application-wide events -------------------------------

        // Abort everything
        if (event instanceof TCommandEvent) {
            TCommandEvent command = (TCommandEvent) event;
            if (command.getCmd().equals(cmAbort)) {
                quit = true;
                return;
            }
        }

        // Screen resize
        if (event instanceof TResizeEvent) {
            TResizeEvent resize = (TResizeEvent) event;
            synchronized (getScreen()) {
                getScreen().setDimensions(resize.getWidth(),
                    resize.getHeight());
                desktopBottom = getScreen().getHeight() - 1;
                mouseX = 0;
                mouseY = 0;
                oldMouseX = 0;
                oldMouseY = 0;
            }
            return;
        }

        // Peek at the mouse position
        if (event instanceof TMouseEvent) {
            TMouseEvent mouse = (TMouseEvent) event;
            synchronized (getScreen()) {
                if ((mouseX != mouse.getX()) || (mouseY != mouse.getY())) {
                    oldMouseX = mouseX;
                    oldMouseY = mouseY;
                    mouseX = mouse.getX();
                    mouseY = mouse.getY();
                }
            }
        }

        // Put into the main queue
        drainEventQueue.add(event);
    }

    /**
     * Dispatch one event to the appropriate widget or application-level
     * event handler.  This is the primary event handler, it has the normal
     * application-wide event handling.
     *
     * @param event the input event to consume
     * @see #secondaryHandleEvent(TInputEvent event)
     */
    private void primaryHandleEvent(final TInputEvent event) {

        if (debugEvents) {
            System.err.printf("Handle event: %s\n", event);
        }

        // Special application-wide events -----------------------------------

        // Peek at the mouse position
        if (event instanceof TMouseEvent) {
            // See if we need to switch focus to another window or the menu
            checkSwitchFocus((TMouseEvent) event);
        }

        // Handle menu events
        if ((activeMenu != null) && !(event instanceof TCommandEvent)) {
            TMenu menu = activeMenu;

            if (event instanceof TMouseEvent) {
                TMouseEvent mouse = (TMouseEvent) event;

                while (subMenus.size() > 0) {
                    TMenu subMenu = subMenus.get(subMenus.size() - 1);
                    if (subMenu.mouseWouldHit(mouse)) {
                        break;
                    }
                    if ((mouse.getType() == TMouseEvent.Type.MOUSE_MOTION)
                        && (!mouse.isMouse1())
                        && (!mouse.isMouse2())
                        && (!mouse.isMouse3())
                        && (!mouse.isMouseWheelUp())
                        && (!mouse.isMouseWheelDown())
                    ) {
                        break;
                    }
                    // We navigated away from a sub-menu, so close it
                    closeSubMenu();
                }

                // Convert the mouse relative x/y to menu coordinates
                assert (mouse.getX() == mouse.getAbsoluteX());
                assert (mouse.getY() == mouse.getAbsoluteY());
                if (subMenus.size() > 0) {
                    menu = subMenus.get(subMenus.size() - 1);
                }
                mouse.setX(mouse.getX() - menu.getX());
                mouse.setY(mouse.getY() - menu.getY());
            }
            menu.handleEvent(event);
            return;
        }

        if (event instanceof TKeypressEvent) {
            TKeypressEvent keypress = (TKeypressEvent) event;

            // See if this key matches an accelerator, and is not being
            // shortcutted by the active window, and if so dispatch the menu
            // event.
            boolean windowWillShortcut = false;
            for (TWindow window: windows) {
                if (window.isActive()) {
                    if (window.isShortcutKeypress(keypress.getKey())) {
                        // We do not process this key, it will be passed to
                        // the window instead.
                        windowWillShortcut = true;
                    }
                }
            }

            if (!windowWillShortcut && !modalWindowActive()) {
                TKeypress keypressLowercase = keypress.getKey().toLowerCase();
                TMenuItem item = null;
                synchronized (accelerators) {
                    item = accelerators.get(keypressLowercase);
                }
                if (item != null) {
                    if (item.isEnabled()) {
                        // Let the menu item dispatch
                        item.dispatch();
                        return;
                    }
                }

                // Handle the keypress
                if (onKeypress(keypress)) {
                    return;
                }
            }
        }

        if (event instanceof TCommandEvent) {
            if (onCommand((TCommandEvent) event)) {
                return;
            }
        }

        if (event instanceof TMenuEvent) {
            if (onMenu((TMenuEvent) event)) {
                return;
            }
        }

        // Dispatch events to the active window -------------------------------
        for (TWindow window: windows) {
            if (window.isActive()) {
                if (event instanceof TMouseEvent) {
                    TMouseEvent mouse = (TMouseEvent) event;
                    // Convert the mouse relative x/y to window coordinates
                    assert (mouse.getX() == mouse.getAbsoluteX());
                    assert (mouse.getY() == mouse.getAbsoluteY());
                    mouse.setX(mouse.getX() - window.getX());
                    mouse.setY(mouse.getY() - window.getY());
                }
                if (debugEvents) {
                    System.err.printf("TApplication dispatch event: %s\n",
                        event);
                }
                window.handleEvent(event);
                break;
            }
        }
    }
    /**
     * Dispatch one event to the appropriate widget or application-level
     * event handler.  This is the secondary event handler used by certain
     * special dialogs (currently TMessageBox and TFileOpenBox).
     *
     * @param event the input event to consume
     * @see #primaryHandleEvent(TInputEvent event)
     */
    private void secondaryHandleEvent(final TInputEvent event) {
        secondaryEventReceiver.handleEvent(event);
    }

    /**
     * Enable a widget to override the primary event thread.
     *
     * @param widget widget that will receive events
     */
    public final void enableSecondaryEventReceiver(final TWidget widget) {
        assert (secondaryEventReceiver == null);
        assert (secondaryEventHandler == null);
        assert ((widget instanceof TMessageBox)
            || (widget instanceof TFileOpenBox));
        secondaryEventReceiver = widget;
        secondaryEventHandler = new WidgetEventHandler(this, false);
        (new Thread(secondaryEventHandler)).start();
    }

    /**
     * Yield to the secondary thread.
     */
    public final void yield() {
        assert (secondaryEventReceiver != null);
        // This is where we handoff the event handler lock from the primary
        // to secondary thread.  We unlock here, and in a future loop the
        // secondary thread locks again.  When it gives up, we have the
        // single lock back.
        boolean oldLock = unlockHandleEvent();
        assert (oldLock);

        while (secondaryEventReceiver != null) {
            synchronized (primaryEventHandler) {
                try {
                    primaryEventHandler.wait();
                } catch (InterruptedException e) {
                    // SQUASH
                }
            }
        }
    }

    /**
     * Do stuff when there is no user input.
     */
    private void doIdle() {
        if (debugThreads) {
            System.err.printf("doIdle()\n");
        }

        // Now run any timers that have timed out
        Date now = new Date();
        List<TTimer> keepTimers = new LinkedList<TTimer>();
        for (TTimer timer: timers) {
            if (timer.getNextTick().getTime() <= now.getTime()) {
                timer.tick();
                if (timer.recurring) {
                    keepTimers.add(timer);
                }
            } else {
                keepTimers.add(timer);
            }
        }
        timers = keepTimers;

        // Call onIdle's
        for (TWindow window: windows) {
            window.onIdle();
        }
    }

    // ------------------------------------------------------------------------
    // TWindow management -----------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Close window.  Note that the window's destructor is NOT called by this
     * method, instead the GC is assumed to do the cleanup.
     *
     * @param window the window to remove
     */
    public final void closeWindow(final TWindow window) {
    	// Only allow to close a non-closable window when exiting the application
    	if (!quit && window.isUnclosable()) {
    		return;
    	}
    	
        synchronized (windows) {
            int z = window.getZ();
            window.setZ(-1);
            window.onUnfocus();
            Collections.sort(windows);
            windows.remove(0);
            TWindow activeWindow = null;
            for (TWindow w: windows) {
                if (w.getZ() > z) {
                    w.setZ(w.getZ() - 1);
                    if (w.getZ() == 0) {
                        w.setActive(true);
                        w.onFocus();
                        assert (activeWindow == null);
                        activeWindow = w;
                    } else {
                        if (w.isActive()) {
                            w.setActive(false);
                            w.onUnfocus();
                        }
                    }
                }
            }
        }

        // Perform window cleanup
        window.onClose();

        // Check if we are closing a TMessageBox or similar
        if (secondaryEventReceiver != null) {
            assert (secondaryEventHandler != null);

            // Do not send events to the secondaryEventReceiver anymore, the
            // window is closed.
            secondaryEventReceiver = null;

            // Wake the secondary thread, it will wake the primary as it
            // exits.
            synchronized (secondaryEventHandler) {
                secondaryEventHandler.notify();
            }
        }
    }

    /**
     * Switch to the next window.
     *
     * @param forward if true, then switch to the next window in the list,
     * otherwise switch to the previous window in the list
     */
    public final void switchWindow(final boolean forward) {
        // Only switch if there are multiple windows
        if (windows.size() < 2) {
            return;
        }

        synchronized (windows) {

            // Swap z/active between active window and the next in the list
            int activeWindowI = -1;
            for (int i = 0; i < windows.size(); i++) {
                if (windows.get(i).isActive()) {
                    activeWindowI = i;
                    break;
                }
            }
            assert (activeWindowI >= 0);

            // Do not switch if a window is modal
            if (windows.get(activeWindowI).isModal()) {
                return;
            }

            int nextWindowI;
            if (forward) {
                nextWindowI = (activeWindowI + 1) % windows.size();
            } else {
                if (activeWindowI == 0) {
                    nextWindowI = windows.size() - 1;
                } else {
                    nextWindowI = activeWindowI - 1;
                }
            }
            windows.get(activeWindowI).setActive(false);
            windows.get(activeWindowI).setZ(windows.get(nextWindowI).getZ());
            windows.get(activeWindowI).onUnfocus();
            windows.get(nextWindowI).setZ(0);
            windows.get(nextWindowI).setActive(true);
            windows.get(nextWindowI).onFocus();

        } // synchronized (windows)

    }

    /**
     * Add a window to my window list and make it active.
     *
     * @param window new window to add
     */
    public final void addWindow(final TWindow window) {

        // Do not add menu windows to the window list.
        if (window instanceof TMenu) {
            return;
        }

        synchronized (windows) {
            // Do not allow a modal window to spawn a non-modal window.  If a
            // modal window is active, then this window will become modal
            // too.
            if (modalWindowActive()) {
                window.flags |= TWindow.MODAL;
                window.flags |= TWindow.CENTERED;
            }
            for (TWindow w: windows) {
                if (w.isActive()) {
                    w.setActive(false);
                    w.onUnfocus();
                }
                w.setZ(w.getZ() + 1);
            }
            windows.add(window);
            window.setZ(0);
            window.setActive(true);
            window.onFocus();

            if (((window.flags & TWindow.CENTERED) == 0)
                && smartWindowPlacement) {

                doSmartPlacement(window);
            }
        }
    }

    /**
     * Check if there is a system-modal window on top.
     *
     * @return true if the active window is modal
     */
    private boolean modalWindowActive() {
        if (windows.size() == 0) {
            return false;
        }

        for (TWindow w: windows) {
            if (w.isModal()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Close all open windows.
     */
    private void closeAllWindows() {
        // Don't do anything if we are in the menu
        if (activeMenu != null) {
            return;
        }
        for (TWindow window : new ArrayList<TWindow>(windows)) {
            closeWindow(window);
        }
    }

    /**
     * Re-layout the open windows as non-overlapping tiles.  This produces
     * almost the same results as Turbo Pascal 7.0's IDE.
     */
    private void tileWindows() {
        synchronized (windows) {
            // Don't do anything if we are in the menu
            if (activeMenu != null) {
                return;
            }
            int z = windows.size();
            if (z == 0) {
                return;
            }
            int a = 0;
            int b = 0;
            a = (int)(Math.sqrt(z));
            int c = 0;
            while (c < a) {
                b = (z - c) / a;
                if (((a * b) + c) == z) {
                    break;
                }
                c++;
            }
            assert (a > 0);
            assert (b > 0);
            assert (c < a);
            int newWidth = (getScreen().getWidth() / a);
            int newHeight1 = ((getScreen().getHeight() - 1) / b);
            int newHeight2 = ((getScreen().getHeight() - 1) / (b + c));

            List<TWindow> sorted = new LinkedList<TWindow>(windows);
            Collections.sort(sorted);
            Collections.reverse(sorted);
            for (int i = 0; i < sorted.size(); i++) {
                int logicalX = i / b;
                int logicalY = i % b;
                if (i >= ((a - 1) * b)) {
                    logicalX = a - 1;
                    logicalY = i - ((a - 1) * b);
                }

                TWindow w = sorted.get(i);
                w.setX(logicalX * newWidth);
                w.setWidth(newWidth);
                if (i >= ((a - 1) * b)) {
                    w.setY((logicalY * newHeight2) + 1);
                    w.setHeight(newHeight2);
                } else {
                    w.setY((logicalY * newHeight1) + 1);
                    w.setHeight(newHeight1);
                }
            }
        }
    }

    /**
     * Re-layout the open windows as overlapping cascaded windows.
     */
    private void cascadeWindows() {
        synchronized (windows) {
            // Don't do anything if we are in the menu
            if (activeMenu != null) {
                return;
            }
            int x = 0;
            int y = 1;
            List<TWindow> sorted = new LinkedList<TWindow>(windows);
            Collections.sort(sorted);
            Collections.reverse(sorted);
            for (TWindow window: sorted) {
                window.setX(x);
                window.setY(y);
                x++;
                y++;
                if (x > getScreen().getWidth()) {
                    x = 0;
                }
                if (y >= getScreen().getHeight()) {
                    y = 1;
                }
            }
        }
    }

    /**
     * Place a window to minimize its overlap with other windows.
     *
     * @param window the window to place
     */
    public final void doSmartPlacement(final TWindow window) {
        // This is a pretty dumb algorithm, but seems to work.  The hardest
        // part is computing these "overlap" values seeking a minimum average
        // overlap.
        int xMin = 0;
        int yMin = desktopTop;
        int xMax = getScreen().getWidth() - window.getWidth() + 1;
        int yMax = desktopBottom  - window.getHeight() + 1;
        if (xMax < xMin) {
            xMax = xMin;
        }
        if (yMax < yMin) {
            yMax = yMin;
        }

        if ((xMin == xMax) && (yMin == yMax)) {
            // No work to do, bail out.
            return;
        }

        // Compute the overlap matrix without the new window.
        int width = getScreen().getWidth();
        int height = getScreen().getHeight();
        int overlapMatrix[][] = new int[width][height];
        for (TWindow w: windows) {
            if (window == w) {
                continue;
            }
            for (int x = w.getX(); x < w.getX() + w.getWidth(); x++) {
                if (x == width) {
                    continue;
                }
                for (int y = w.getY(); y < w.getY() + w.getHeight(); y++) {
                    if (y == height) {
                        continue;
                    }
                    overlapMatrix[x][y]++;
                }
            }
        }

        long oldOverlapTotal = 0;
        long oldOverlapN = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                oldOverlapTotal += overlapMatrix[x][y];
                if (overlapMatrix[x][y] > 0) {
                    oldOverlapN++;
                }
            }
        }


        double oldOverlapAvg = (double) oldOverlapTotal / (double) oldOverlapN;
        boolean first = true;
        int windowX = window.getX();
        int windowY = window.getY();

        // For each possible (x, y) position for the new window, compute a
        // new overlap matrix.
        for (int x = xMin; x < xMax; x++) {
            for (int y = yMin; y < yMax; y++) {

                // Start with the matrix minus this window.
                int newMatrix[][] = new int[width][height];
                for (int mx = 0; mx < width; mx++) {
                    for (int my = 0; my < height; my++) {
                        newMatrix[mx][my] = overlapMatrix[mx][my];
                    }
                }

                // Add this window's values to the new overlap matrix.
                long newOverlapTotal = 0;
                long newOverlapN = 0;
                // Start by adding each new cell.
                for (int wx = x; wx < x + window.getWidth(); wx++) {
                    if (wx == width) {
                        continue;
                    }
                    for (int wy = y; wy < y + window.getHeight(); wy++) {
                        if (wy == height) {
                            continue;
                        }
                        newMatrix[wx][wy]++;
                    }
                }
                // Now figure out the new value for total coverage.
                for (int mx = 0; mx < width; mx++) {
                    for (int my = 0; my < height; my++) {
                        newOverlapTotal += newMatrix[x][y];
                        if (newMatrix[mx][my] > 0) {
                            newOverlapN++;
                        }
                    }
                }
                double newOverlapAvg = (double) newOverlapTotal / (double) newOverlapN;

                if (first) {
                    // First time: just record what we got.
                    oldOverlapAvg = newOverlapAvg;
                    first = false;
                } else {
                    // All other times: pick a new best (x, y) and save the
                    // overlap value.
                    if (newOverlapAvg < oldOverlapAvg) {
                        windowX = x;
                        windowY = y;
                        oldOverlapAvg = newOverlapAvg;
                    }
                }

            } // for (int x = xMin; x < xMax; x++)

        } // for (int y = yMin; y < yMax; y++)

        // Finally, set the window's new coordinates.
        window.setX(windowX);
        window.setY(windowY);
    }

    // ------------------------------------------------------------------------
    // TMenu management -------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Check if a mouse event would hit either the active menu or any open
     * sub-menus.
     *
     * @param mouse mouse event
     * @return true if the mouse would hit the active menu or an open
     * sub-menu
     */
    private boolean mouseOnMenu(final TMouseEvent mouse) {
        assert (activeMenu != null);
        List<TMenu> menus = new LinkedList<TMenu>(subMenus);
        Collections.reverse(menus);
        for (TMenu menu: menus) {
            if (menu.mouseWouldHit(mouse)) {
                return true;
            }
        }
        return activeMenu.mouseWouldHit(mouse);
    }

    /**
     * See if we need to switch window or activate the menu based on
     * a mouse click.
     *
     * @param mouse mouse event
     */
    private void checkSwitchFocus(final TMouseEvent mouse) {

        if ((mouse.getType() == TMouseEvent.Type.MOUSE_DOWN)
            && (activeMenu != null)
            && (mouse.getAbsoluteY() != 0)
            && (!mouseOnMenu(mouse))
        ) {
            // They clicked outside the active menu, turn it off
            activeMenu.setActive(false);
            activeMenu = null;
            for (TMenu menu: subMenus) {
                menu.setActive(false);
            }
            subMenus.clear();
            // Continue checks
        }

        // See if they hit the menu bar
        if ((mouse.getType() == TMouseEvent.Type.MOUSE_DOWN)
            && (mouse.isMouse1())
            && (!modalWindowActive())
            && (mouse.getAbsoluteY() == 0)
        ) {

            for (TMenu menu: subMenus) {
                menu.setActive(false);
            }
            subMenus.clear();

            // They selected the menu, go activate it
            for (TMenu menu: menus) {
                if ((mouse.getAbsoluteX() >= menu.getX())
                    && (mouse.getAbsoluteX() < menu.getX()
                        + menu.getTitle().length() + 2)
                ) {
                    menu.setActive(true);
                    activeMenu = menu;
                } else {
                    menu.setActive(false);
                }
            }
            return;
        }

        // See if they hit the menu bar
        if ((mouse.getType() == TMouseEvent.Type.MOUSE_MOTION)
            && (mouse.isMouse1())
            && (activeMenu != null)
            && (mouse.getAbsoluteY() == 0)
        ) {

            TMenu oldMenu = activeMenu;
            for (TMenu menu: subMenus) {
                menu.setActive(false);
            }
            subMenus.clear();

            // See if we should switch menus
            for (TMenu menu: menus) {
                if ((mouse.getAbsoluteX() >= menu.getX())
                    && (mouse.getAbsoluteX() < menu.getX()
                        + menu.getTitle().length() + 2)
                ) {
                    menu.setActive(true);
                    activeMenu = menu;
                }
            }
            if (oldMenu != activeMenu) {
                // They switched menus
                oldMenu.setActive(false);
            }
            return;
        }

        // Only switch if there are multiple windows
        if (windows.size() < 2) {
            return;
        }

        // Switch on the upclick
        if (mouse.getType() != TMouseEvent.Type.MOUSE_UP) {
            return;
        }

        synchronized (windows) {
            Collections.sort(windows);
            if (windows.get(0).isModal()) {
                // Modal windows don't switch
                return;
            }

            for (TWindow window: windows) {
                assert (!window.isModal());
                if (window.mouseWouldHit(mouse)) {
                    if (window == windows.get(0)) {
                        // Clicked on the same window, nothing to do
                        return;
                    }

                    // We will be switching to another window
                    assert (windows.get(0).isActive());
                    assert (!window.isActive());
                    windows.get(0).onUnfocus();
                    windows.get(0).setActive(false);
                    windows.get(0).setZ(window.getZ());
                    window.setZ(0);
                    window.setActive(true);
                    window.onFocus();
                    return;
                }
            }
        }

        // Clicked on the background, nothing to do
        return;
    }

    /**
     * Turn off the menu.
     */
    public final void closeMenu() {
        if (activeMenu != null) {
            activeMenu.setActive(false);
            activeMenu = null;
            for (TMenu menu: subMenus) {
                menu.setActive(false);
            }
            subMenus.clear();
        }
    }

    /**
     * Turn off a sub-menu.
     */
    public final void closeSubMenu() {
        assert (activeMenu != null);
        TMenu item = subMenus.get(subMenus.size() - 1);
        assert (item != null);
        item.setActive(false);
        subMenus.remove(subMenus.size() - 1);
    }

    /**
     * Switch to the next menu.
     *
     * @param forward if true, then switch to the next menu in the list,
     * otherwise switch to the previous menu in the list
     */
    public final void switchMenu(final boolean forward) {
        assert (activeMenu != null);

        for (TMenu menu: subMenus) {
            menu.setActive(false);
        }
        subMenus.clear();

        for (int i = 0; i < menus.size(); i++) {
            if (activeMenu == menus.get(i)) {
                if (forward) {
                    if (i < menus.size() - 1) {
                        i++;
                    }
                } else {
                    if (i > 0) {
                        i--;
                    }
                }
                activeMenu.setActive(false);
                activeMenu = menus.get(i);
                activeMenu.setActive(true);
                return;
            }
        }
    }

    /**
     * Add a menu item to the global list.  If it has a keyboard accelerator,
     * that will be added the global hash.
     *
     * @param item the menu item
     */
    public final void addMenuItem(final TMenuItem item) {
        menuItems.add(item);

        TKeypress key = item.getKey();
        if (key != null) {
            synchronized (accelerators) {
                assert (accelerators.get(key) == null);
                accelerators.put(key.toLowerCase(), item);
            }
        }
    }

    /**
     * Disable one menu item.
     *
     * @param id the menu item ID
     */
    public final void disableMenuItem(final int id) {
        for (TMenuItem item: menuItems) {
            if (item.getId() == id) {
                item.setEnabled(false);
            }
        }
    }

    /**
     * Disable the range of menu items with ID's between lower and upper,
     * inclusive.
     *
     * @param lower the lowest menu item ID
     * @param upper the highest menu item ID
     */
    public final void disableMenuItems(final int lower, final int upper) {
        for (TMenuItem item: menuItems) {
            if ((item.getId() >= lower) && (item.getId() <= upper)) {
                item.setEnabled(false);
            }
        }
    }

    /**
     * Enable one menu item.
     *
     * @param id the menu item ID
     */
    public final void enableMenuItem(final int id) {
        for (TMenuItem item: menuItems) {
            if (item.getId() == id) {
                item.setEnabled(true);
            }
        }
    }

    /**
     * Enable the range of menu items with ID's between lower and upper,
     * inclusive.
     *
     * @param lower the lowest menu item ID
     * @param upper the highest menu item ID
     */
    public final void enableMenuItems(final int lower, final int upper) {
        for (TMenuItem item: menuItems) {
            if ((item.getId() >= lower) && (item.getId() <= upper)) {
                item.setEnabled(true);
            }
        }
    }

    /**
     * Recompute menu x positions based on their title length.
     */
    public final void recomputeMenuX() {
        int x = 0;
        for (TMenu menu: menus) {
            menu.setX(x);
            x += menu.getTitle().length() + 2;
        }
    }

    /**
     * Post an event to process and turn off the menu.
     *
     * @param event new event to add to the queue
     */
    public final void postMenuEvent(final TInputEvent event) {
        synchronized (fillEventQueue) {
            fillEventQueue.add(event);
        }
        closeMenu();
    }

    /**
     * Add a sub-menu to the list of open sub-menus.
     *
     * @param menu sub-menu
     */
    public final void addSubMenu(final TMenu menu) {
        subMenus.add(menu);
    }

    /**
     * Convenience function to add a top-level menu.
     *
     * @param title menu title
     * @return the new menu
     */
    public final TMenu addMenu(final String title) {
        int x = 0;
        int y = 0;
        TMenu menu = new TMenu(this, x, y, title);
        menus.add(menu);
        recomputeMenuX();
        return menu;
    }

    /**
     * Convenience function to add a default "File" menu.
     *
     * @return the new menu
     */
    public final TMenu addFileMenu() {
        TMenu fileMenu = addMenu("&File");
        fileMenu.addDefaultItem(TMenu.MID_OPEN_FILE);
        fileMenu.addSeparator();
        fileMenu.addDefaultItem(TMenu.MID_SHELL);
        fileMenu.addDefaultItem(TMenu.MID_EXIT);
        TStatusBar statusBar = fileMenu.newStatusBar("File-management " +
            "commands (Open, Save, Print, etc.)");
        statusBar.addShortcutKeypress(kbF1, cmHelp, "Help");
        return fileMenu;
    }

    /**
     * Convenience function to add a default "Edit" menu.
     *
     * @return the new menu
     */
    public final TMenu addEditMenu() {
        TMenu editMenu = addMenu("&Edit");
        editMenu.addDefaultItem(TMenu.MID_CUT);
        editMenu.addDefaultItem(TMenu.MID_COPY);
        editMenu.addDefaultItem(TMenu.MID_PASTE);
        editMenu.addDefaultItem(TMenu.MID_CLEAR);
        TStatusBar statusBar = editMenu.newStatusBar("Editor operations, " +
            "undo, and Clipboard access");
        statusBar.addShortcutKeypress(kbF1, cmHelp, "Help");
        return editMenu;
    }

    /**
     * Convenience function to add a default "Window" menu.
     *
     * @return the new menu
     */
    public final TMenu addWindowMenu() {
        TMenu windowMenu = addMenu("&Window");
        windowMenu.addDefaultItem(TMenu.MID_TILE);
        windowMenu.addDefaultItem(TMenu.MID_CASCADE);
        windowMenu.addDefaultItem(TMenu.MID_CLOSE_ALL);
        windowMenu.addSeparator();
        windowMenu.addDefaultItem(TMenu.MID_WINDOW_MOVE);
        windowMenu.addDefaultItem(TMenu.MID_WINDOW_ZOOM);
        windowMenu.addDefaultItem(TMenu.MID_WINDOW_NEXT);
        windowMenu.addDefaultItem(TMenu.MID_WINDOW_PREVIOUS);
        windowMenu.addDefaultItem(TMenu.MID_WINDOW_CLOSE);
        TStatusBar statusBar = windowMenu.newStatusBar("Open, arrange, and " +
            "list windows");
        statusBar.addShortcutKeypress(kbF1, cmHelp, "Help");
        return windowMenu;
    }

    /**
     * Convenience function to add a default "Help" menu.
     *
     * @return the new menu
     */
    public final TMenu addHelpMenu() {
        TMenu helpMenu = addMenu("&Help");
        helpMenu.addDefaultItem(TMenu.MID_HELP_CONTENTS);
        helpMenu.addDefaultItem(TMenu.MID_HELP_INDEX);
        helpMenu.addDefaultItem(TMenu.MID_HELP_SEARCH);
        helpMenu.addDefaultItem(TMenu.MID_HELP_PREVIOUS);
        helpMenu.addDefaultItem(TMenu.MID_HELP_HELP);
        helpMenu.addDefaultItem(TMenu.MID_HELP_ACTIVE_FILE);
        helpMenu.addSeparator();
        helpMenu.addDefaultItem(TMenu.MID_ABOUT);
        TStatusBar statusBar = helpMenu.newStatusBar("Access online help");
        statusBar.addShortcutKeypress(kbF1, cmHelp, "Help");
        return helpMenu;
    }

    // ------------------------------------------------------------------------
    // Event handlers ---------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Method that TApplication subclasses can override to handle menu or
     * posted command events.
     *
     * @param command command event
     * @return if true, this event was consumed
     */
    protected boolean onCommand(final TCommandEvent command) {
        // Default: handle cmExit
        if (command.equals(cmExit)) {
            if (messageBox("Confirmation", "Exit application?",
                    TMessageBox.Type.YESNO).getResult() == TMessageBox.Result.YES) {
                quit = true;
            }
            return true;
        }

        if (command.equals(cmShell)) {
            openTerminal(0, 0, TWindow.RESIZABLE);
            return true;
        }

        if (command.equals(cmTile)) {
            tileWindows();
            return true;
        }
        if (command.equals(cmCascade)) {
            cascadeWindows();
            return true;
        }
        if (command.equals(cmCloseAll)) {
            closeAllWindows();
            return true;
        }

        return false;
    }

    /**
     * Method that TApplication subclasses can override to handle menu
     * events.
     *
     * @param menu menu event
     * @return if true, this event was consumed
     */
    protected boolean onMenu(final TMenuEvent menu) {

        // Default: handle MID_EXIT
        if (menu.getId() == TMenu.MID_EXIT) {
            if (messageBox("Confirmation", "Exit application?",
                    TMessageBox.Type.YESNO).getResult() == TMessageBox.Result.YES) {
                quit = true;
            }
            return true;
        }

        if (menu.getId() == TMenu.MID_SHELL) {
            openTerminal(0, 0, TWindow.RESIZABLE);
            return true;
        }

        if (menu.getId() == TMenu.MID_TILE) {
            tileWindows();
            return true;
        }
        if (menu.getId() == TMenu.MID_CASCADE) {
            cascadeWindows();
            return true;
        }
        if (menu.getId() == TMenu.MID_CLOSE_ALL) {
            closeAllWindows();
            return true;
        }
        if (menu.getId() == TMenu.MID_ABOUT) {
            showAboutDialog();
            return true;
        }
        return false;
    }

    /**
     * Method that TApplication subclasses can override to handle keystrokes.
     *
     * @param keypress keystroke event
     * @return if true, this event was consumed
     */
    protected boolean onKeypress(final TKeypressEvent keypress) {
        // Default: only menu shortcuts

        // Process Alt-F, Alt-E, etc. menu shortcut keys
        if (!keypress.getKey().isFnKey()
            && keypress.getKey().isAlt()
            && !keypress.getKey().isCtrl()
            && (activeMenu == null)
            && !modalWindowActive()
        ) {

            assert (subMenus.size() == 0);

            for (TMenu menu: menus) {
                if (Character.toLowerCase(menu.getMnemonic().getShortcut())
                    == Character.toLowerCase(keypress.getKey().getChar())
                ) {
                    activeMenu = menu;
                    menu.setActive(true);
                    return true;
                }
            }
        }

        return false;
    }

    // ------------------------------------------------------------------------
    // TTimer management ------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Get the amount of time I can sleep before missing a Timer tick.
     *
     * @param timeout = initial (maximum) timeout in millis
     * @return number of milliseconds between now and the next timer event
     */
    private long getSleepTime(final long timeout) {
        Date now = new Date();
        long nowTime = now.getTime();
        long sleepTime = timeout;
        for (TTimer timer: timers) {
            long nextTickTime = timer.getNextTick().getTime();
            if (nextTickTime < nowTime) {
                return 0;
            }

            long timeDifference = nextTickTime - nowTime;
            if (timeDifference < sleepTime) {
                sleepTime = timeDifference;
            }
        }
        assert (sleepTime >= 0);
        assert (sleepTime <= timeout);
        return sleepTime;
    }

    /**
     * Convenience function to add a timer.
     *
     * @param duration number of milliseconds to wait between ticks
     * @param recurring if true, re-schedule this timer after every tick
     * @param action function to call when button is pressed
     * @return the timer
     */
    public final TTimer addTimer(final long duration, final boolean recurring,
        final TAction action) {

        TTimer timer = new TTimer(duration, recurring, action);
        synchronized (timers) {
            timers.add(timer);
        }
        return timer;
    }

    /**
     * Convenience function to remove a timer.
     *
     * @param timer timer to remove
     */
    public final void removeTimer(final TTimer timer) {
        synchronized (timers) {
            timers.remove(timer);
        }
    }

    // ------------------------------------------------------------------------
    // Other TWindow constructors ---------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Convenience function to spawn a message box.
     *
     * @param title window title, will be centered along the top border
     * @param caption message to display.  Use embedded newlines to get a
     * multi-line box.
     * @return the new message box
     */
    public final TMessageBox messageBox(final String title,
        final String caption) {

        return new TMessageBox(this, title, caption, TMessageBox.Type.OK);
    }

    /**
     * Convenience function to spawn a message box.
     *
     * @param title window title, will be centered along the top border
     * @param caption message to display.  Use embedded newlines to get a
     * multi-line box.
     * @param type one of the TMessageBox.Type constants.  Default is
     * Type.OK.
     * @return the new message box
     */
    public final TMessageBox messageBox(final String title,
        final String caption, final TMessageBox.Type type) {

        return new TMessageBox(this, title, caption, type);
    }

    /**
     * Convenience function to spawn an input box.
     *
     * @param title window title, will be centered along the top border
     * @param caption message to display.  Use embedded newlines to get a
     * multi-line box.
     * @return the new input box
     */
    public final TInputBox inputBox(final String title, final String caption) {

        return new TInputBox(this, title, caption);
    }

    /**
     * Convenience function to spawn an input box.
     *
     * @param title window title, will be centered along the top border
     * @param caption message to display.  Use embedded newlines to get a
     * multi-line box.
     * @param text initial text to seed the field with
     * @return the new input box
     */
    public final TInputBox inputBox(final String title, final String caption,
        final String text) {

        return new TInputBox(this, title, caption, text);
    }

    /**
     * Convenience function to open a terminal window.
     *
     * @param x column relative to parent
     * @param y row relative to parent
     * @return the terminal new window
     */
    public final TTerminalWindow openTerminal(final int x, final int y) {
        return openTerminal(x, y, TWindow.RESIZABLE);
    }

    /**
     * Convenience function to open a terminal window.
     *
     * @param x column relative to parent
     * @param y row relative to parent
     * @param flags mask of CENTERED, MODAL, or RESIZABLE
     * @return the terminal new window
     */
    public final TTerminalWindow openTerminal(final int x, final int y,
        final int flags) {

        return new TTerminalWindow(this, x, y, flags);
    }

    /**
     * Convenience function to spawn an file open box.
     *
     * @param path path of selected file
     * @return the result of the new file open box
     * @throws IOException if java.io operation throws
     */
    public final String fileOpenBox(final String path) throws IOException {

        TFileOpenBox box = new TFileOpenBox(this, path, TFileOpenBox.Type.OPEN);
        return box.getFilename();
    }

    /**
     * Convenience function to spawn an file open box.
     *
     * @param path path of selected file
     * @param type one of the Type constants
     * @return the result of the new file open box
     * @throws IOException if java.io operation throws
     */
    public final String fileOpenBox(final String path,
        final TFileOpenBox.Type type) throws IOException {

        TFileOpenBox box = new TFileOpenBox(this, path, type);
        return box.getFilename();
    }

}
