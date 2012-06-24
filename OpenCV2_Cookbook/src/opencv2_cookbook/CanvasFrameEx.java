/*
 * Copyright (C) 2009,2010,2011,2012 Samuel Audet
 *
 * This file is part of JavaCV.
 *
 * JavaCV is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version (subject to the "Classpath" exception
 * as provided in the LICENSE.txt file that accompanied this code).
 *
 * JavaCV is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaCV.  If not, see <http://www.gnu.org/licenses/>.
 */

package opencv2_cookbook;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_ProfileRGB;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

import static com.googlecode.javacv.cpp.opencv_core.CvScalar;
import static com.googlecode.javacv.cpp.opencv_core.IplImage;

/**
 * @author Samuel Audet
 *         <p/>
 *         Make sure OpenGL is enabled to get low latency, something like
 *         export _JAVA_OPTIONS=-Dsun.java2d.opengl=True
 */
public class CanvasFrameEx extends JFrame {

    public static class Exception extends java.lang.Exception {
        public Exception(String message) {
            super(message);
        }

        public Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static String[] getScreenDescriptions() {
        GraphicsDevice[] screens = getScreenDevices();
        String[] descriptions = new String[screens.length];
        for (int i = 0; i < screens.length; i++) {
            descriptions[i] = screens[i].getIDstring();
        }
        return descriptions;
    }

    public static DisplayMode getDisplayMode(int screenNumber) {
        GraphicsDevice[] screens = getScreenDevices();
        if (screenNumber >= 0 && screenNumber < screens.length) {
            return screens[screenNumber].getDisplayMode();
        } else {
            return null;
        }
    }

    public static double getGamma(int screenNumber) {
        GraphicsDevice[] screens = getScreenDevices();
        if (screenNumber >= 0 && screenNumber < screens.length) {
            return getGamma(screens[screenNumber]);
        } else {
            return 0.0;
        }
    }

    public static double getDefaultGamma() {
        return getGamma(getDefaultScreenDevice());
    }

    public static double getGamma(GraphicsDevice screen) {
        ColorSpace cs = screen.getDefaultConfiguration().getColorModel().getColorSpace();
        if (cs.isCS_sRGB()) {
            return 2.2;
        } else {
            try {
                return ((ICC_ProfileRGB) ((ICC_ColorSpace) cs).getProfile()).getGamma(0);
            } catch (RuntimeException e) {
            }
        }
        return 0.0;
    }

    public static GraphicsDevice getScreenDevice(int screenNumber) throws Exception {
        GraphicsDevice[] screens = getScreenDevices();
        if (screenNumber >= screens.length) {
            throw new Exception("CanvasFrame Error: Screen number " + screenNumber + " not found. " +
                    "There are only " + screens.length + " screens.");
        }
        return screens[screenNumber];//.getDefaultConfiguration();
    }

    public static GraphicsDevice[] getScreenDevices() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    }

    public static GraphicsDevice getDefaultScreenDevice() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    public CanvasFrameEx(String title) {
        this(title, 0.0);
    }

    public CanvasFrameEx(String title, double gamma) {
        super(title);
        init(false, null, gamma);
    }

    public CanvasFrameEx(String title, GraphicsConfiguration gc) {
        this(title, gc, 0.0);
    }

    public CanvasFrameEx(String title, GraphicsConfiguration gc, double gamma) {
        super(title, gc);
        init(false, null, gamma);
    }

    public CanvasFrameEx(String title, int screenNumber, DisplayMode displayMode) throws Exception {
        this(title, screenNumber, displayMode, 0.0);
    }

    public CanvasFrameEx(String title, int screenNumber, DisplayMode displayMode, double gamma) throws Exception {
        super(title, getScreenDevice(screenNumber).getDefaultConfiguration());
        init(true, displayMode, gamma);
    }

    private void init(final boolean fullScreen, final DisplayMode displayMode, final double gamma) {
        Runnable r = new Runnable() {
            public void run() {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().
                        addKeyEventDispatcher(keyEventDispatch);

                GraphicsDevice gd = getGraphicsConfiguration().getDevice();
                DisplayMode d = gd.getDisplayMode(), d2 = null;
                if (displayMode != null && d != null) {
                    int w = displayMode.getWidth();
                    int h = displayMode.getHeight();
                    int b = displayMode.getBitDepth();
                    int r = displayMode.getRefreshRate();
                    d2 = new DisplayMode(w > 0 ? w : d.getWidth(), h > 0 ? h : d.getHeight(),
                            b > 0 ? b : d.getBitDepth(), r > 0 ? r : d.getRefreshRate());
                }
                if (fullScreen) {
                    setUndecorated(true);
                    getRootPane().setWindowDecorationStyle(JRootPane.NONE);
                    setResizable(false);
                    gd.setFullScreenWindow(CanvasFrameEx.this);
                } else {
                    setLocationByPlatform(true);
                }
                if (d2 != null && !d2.equals(d)) {
                    gd.setDisplayMode(d2);
                }
                double g = gamma == 0.0 ? getGamma(gd) : gamma;
                inverseGamma = g == 0.0 ? 1.0 : 1.0 / g;

                // Must be called after the fullscreen stuff, but before
                // getting our BufferStrategy or even creating our Canvas
                setVisible(true);

                if (CanvasFrameEx.this.getClass() == CanvasFrameEx.class) {
                    canvas = new Canvas() {
                        @Override
                        public void paint(Graphics g) {
                            // Try to redraw the front buffer when the OS says it has stomped
                            // on it, using the back buffer. Calling bufferStrategy.show() here
                            // sometimes throws NullPointerException or IllegalStateException,
                            // but otherwise seems to work fine.
                            try {
                                bufferStrategy.show();
                            } catch (NullPointerException e) {
                            } catch (IllegalStateException e) {
                            }
                        }
                    };
                    if (fullScreen) {
                        canvas.setSize(getSize());
                        needInitialResize = false;
                    } else {
                        needInitialResize = true;
                    }
                    getContentPane().add(canvas);
                    canvas.setVisible(true);
                    canvas.createBufferStrategy(2);
                    //canvas.setIgnoreRepaint(true);
                    bufferStrategy = canvas.getBufferStrategy();
                }
            }
        };

        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            try {
                EventQueue.invokeAndWait(r);
            } catch (java.lang.Exception ex) {
            }
        }
    }

    // used for example as debugging console...
    public static CanvasFrameEx global = null;

    // Latency is about 60 ms on Metacity and Windows XP, and 90 ms on Compiz Fusion,
    // but we set the default to twice as much to take into account the roundtrip
    // camera latency as well, just to be sure
    public static final long DEFAULT_LATENCY = 200;
    private long latency = DEFAULT_LATENCY;

    private KeyEvent keyEvent = null;
    private KeyEventDispatcher keyEventDispatch = new KeyEventDispatcher() {
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                synchronized (CanvasFrameEx.this) {
                    keyEvent = e;
                    CanvasFrameEx.this.notify();
                }
            }
            return false;
        }
    };

    protected Canvas canvas = null;
    protected boolean needInitialResize = false;
    protected double initialScale = 1.0;
    protected double inverseGamma = 1.0;
    private BufferStrategy bufferStrategy = null;

    public long getLatency() {
        // if there exists some way to estimate the latency in real time,
        // add it here
        return latency;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    public void waitLatency() throws InterruptedException {
        Thread.sleep(getLatency());
    }

    public KeyEvent waitKey() throws InterruptedException {
        return waitKey(0);
    }

    public synchronized KeyEvent waitKey(int delay) throws InterruptedException {
        keyEvent = null;
        wait(delay);
        KeyEvent e = keyEvent;
        keyEvent = null;
        return e;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    @Override
    public BufferStrategy getBufferStrategy() {
        return bufferStrategy;
    }

    public Dimension getCanvasSize() {
        return canvas.getSize();
    }

    public void setCanvasSize(final int width, final int height) {
        Dimension d = getCanvasSize();
        if (d.width == width && d.height == height) {
            return;
        }

        Runnable r = new Runnable() {
            public void run() {
                // There is apparently a bug in Java code for Linux, and what happens goes like this:
                // 1. Canvas gets resized, checks the visible area (has not changed) and updates
                // BufferStrategy with the same size. 2. pack() resizes the frame and changes
                // the visible area 3. We call Canvas.setSize() with different dimensions, to make
                // it check the visible area and reallocate the BufferStrategy almost correctly
                // 4. Finally, we resize the Canvas to the desired size... phew!
                setExtendedState(NORMAL); // force unmaximization
                canvas.setSize(width, height);
                pack();
                canvas.setSize(width + 1, height + 1);
                canvas.setSize(width, height);
                needInitialResize = false;
            }
        };

        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            try {
                EventQueue.invokeAndWait(r);
            } catch (java.lang.Exception ex) {
            }
        }
    }

    public double getCanvasScale() {
        return initialScale;
    }

    public void setCanvasScale(double initialScale) {
        this.initialScale = initialScale;
        this.needInitialResize = true;
    }

    public Graphics2D createGraphics() {
        return (Graphics2D) bufferStrategy.getDrawGraphics();
    }

    public void releaseGraphics(Graphics2D g) {
        g.dispose();
        bufferStrategy.show();
    }

    public void showColor(CvScalar color) {
        showColor(new Color((int) color.red(), (int) color.green(), (int) color.blue()));
    }

    public void showColor(Color color) {
        Graphics2D g = createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        releaseGraphics(g);
    }

    // Java2D will do gamma correction for TYPE_CUSTOM BufferedImage, but
    // not for the standard types, so we need to do it manually.
    public void showImage(IplImage image) {
        showImage(image, false);
    }

    public void showImage(IplImage image, boolean flipChannels) {
        showImage(image.getBufferedImage(image.getBufferedImageType() ==
                BufferedImage.TYPE_CUSTOM ? 1.0 : inverseGamma, flipChannels));
    }

    public void showImage(Image image) {
        if (image == null) {
            return;
        } else if (isResizable() && needInitialResize) {
            int w = (int) Math.round(image.getWidth(null) * initialScale);
            int h = (int) Math.round(image.getHeight(null) * initialScale);
            setCanvasSize(w, h);
        }
        Graphics2D g = createGraphics();
        g.drawImage(image, 0, 0, canvas.getWidth(), canvas.getHeight(), null);
        releaseGraphics(g);
    }

    // This should not be called from the event dispatch thread (EDT),
    // but if it is, it should not totally crash... In the worst case,
    // it will simply timeout waiting for the moved events.
    public static void tile(final CanvasFrameEx[] frames) {

        class MovedListener extends ComponentAdapter {
            boolean moved = false;

            @Override
            public void componentMoved(ComponentEvent e) {
                moved = true;
                Component c = e.getComponent();
                synchronized (c) {
                    c.notify();
                }
            }
        }
        final MovedListener movedListener = new MovedListener();

        // layout the canvas frames for the cameras in tiles
        int canvasCols = (int) Math.round(Math.sqrt(frames.length));
        if (canvasCols * canvasCols < frames.length) {
            // if we don't get a square, favor horizontal layouts
            // since screens are usually wider than cameras...
            // and we also have title bars, tasks bar, menus, etc that
            // takes up vertical space
            canvasCols++;
        }
        int canvasX = 0, canvasY = 0;
        int canvasMaxY = 0;
        for (int i = 0; i < frames.length; i++) {
            final int n = i;
            final int x = canvasX;
            final int y = canvasY;
            try {
                movedListener.moved = false;
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        frames[n].addComponentListener(movedListener);
                        frames[n].setLocation(x, y);
                    }
                });
                int count = 0;
                while (!movedListener.moved && count < 5) {
                    // wait until the window manager actually places our window...
                    // wait a maximum of 500 ms since this does not work if
                    // we are on the event dispatch thread. also some window
                    // managers like Windows do not always send us the event...
                    synchronized (frames[n]) {
                        frames[n].wait(100);
                    }
                    count++;
                }
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        frames[n].removeComponentListener(movedListener);
                    }
                });
            } catch (java.lang.Exception ex) {
            }
            canvasX = frames[i].getX() + frames[i].getWidth();
            canvasMaxY = Math.max(canvasMaxY, frames[i].getY() + frames[i].getHeight());
            if ((i + 1) % canvasCols == 0) {
                canvasX = 0;
                canvasY = canvasMaxY;
            }
        }
    }
}
