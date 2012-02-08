/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.application;

import com.sun.javafx.jmx.SGMXBeanAccessor;
import com.sun.javafx.runtime.SystemProperties;
import com.sun.javafx.PlatformUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Application;
import javafx.application.Preloader;
import javafx.application.Preloader.ErrorNotification;
import javafx.application.Preloader.PreloaderNotification;
import javafx.application.Preloader.StateChangeNotification;
import javafx.stage.Stage;


public class LauncherImpl {

    // Set to true to simulate a slow download progress
    private static final boolean simulateSlowProgress = false;

    // Ensure that launchApplication method is only called once
    // TODO: what about preloader?
    private static AtomicBoolean launchCalled = new AtomicBoolean(false);

    // Exception found during launching
    private static volatile RuntimeException launchException = null;

    // The current preloader, used for notification in the standalone
    // launcher mode
    private static Preloader currentPreloader = null;

    /**
     * This method is called by the Application.launch method.
     * It must not be called more than once or an exception will be thrown.
     *
     * Note that it is always called on a thread other than the FX application
     * thread, since that thread is only created at startup.
     *
     * @param appClass application class
     * @param args command line arguments
     */
    public static void launchApplication(final Class<? extends Application> appClass,
            final String[] args) {

        launchApplication(appClass, null, args);
    }

    /**
     * This method is called by the standalone launcher.
     * It must not be called more than once or an exception will be thrown.
     *
     * Note that it is always called on a thread other than the FX application
     * thread, since that thread is only created at startup.
     *
     * @param appClass application class
     * @param preloaderClass preloader class, may be null
     * @param args command line arguments
     */
    public static void launchApplication(final Class<? extends Application> appClass,
            final Class<? extends Preloader> preloaderClass,
            final String[] args) {

        if (launchCalled.getAndSet(true)) {
            throw new IllegalStateException("Application launch must not be called more than once");
        }

        if (! Application.class.isAssignableFrom(appClass)) {
            throw new IllegalArgumentException("Error: " + appClass.getName()
                    + " is not a subclass of javafx.application.Application");
        }

        if (preloaderClass != null && ! Preloader.class.isAssignableFrom(preloaderClass)) {
            throw new IllegalArgumentException("Error: " + preloaderClass.getName()
                    + " is not a subclass of javafx.application.Preloader");
        }

//        System.err.println("launch standalone app: preloader class = "
//                + preloaderClass);

        // Create a new Launcher thread and then wait for that thread to finish
        // TODO: consider just doing this on the calling thread
        final CountDownLatch launchLatch = new CountDownLatch(1);
        Thread launcherThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    launchApplication1(appClass, preloaderClass, args);
                } catch (RuntimeException rte) {
                    launchException = rte;
                } catch (Exception ex) {
                    launchException =
                        new RuntimeException("Application launch exception", ex);
                } catch (Error err) {
                    launchException =
                        new RuntimeException("Application launch error", err);
                } finally {
                    launchLatch.countDown();
                }
            }
        });
        launcherThread.setName("JavaFX-Launcher");
        launcherThread.start();

        // Wait for FX launcher thread to finish before returning to user
        try {
            launchLatch.await();
        } catch (InterruptedException ex) {
            throw new RuntimeException("Unexpected exception: ", ex);
        }

        if (launchException != null) {
            throw launchException;
        }
    }

    private static volatile boolean error = false;
    private static volatile Throwable pConstructorError = null;
    private static volatile Throwable pInitError = null;
    private static volatile Throwable pStartError = null;
    private static volatile Throwable pStopError = null;
    private static volatile Throwable constructorError = null;
    private static volatile Throwable initError = null;
    private static volatile Throwable startError = null;
    private static volatile Throwable stopError = null;

    private static void launchApplication1(final Class<? extends Application> appClass,
            final Class<? extends Preloader> preloaderClass,
            final String[] args) throws Exception {

        if (SystemProperties.isDebug()) {
            SGMXBeanAccessor.registerSGMXBean();
        }

        final CountDownLatch startupLatch = new CountDownLatch(1);
        PlatformImpl.startup(new Runnable() {
            // Note, this method is called on the FX Application Thread
            @Override public void run() {
                startupLatch.countDown();
            }
        });

        // Wait for FX platform to start
        startupLatch.await();

        final AtomicBoolean pStartCalled = new AtomicBoolean(false);
        final AtomicBoolean startCalled = new AtomicBoolean(false);
        final AtomicBoolean exitCalled = new AtomicBoolean(false);
        final AtomicBoolean pExitCalled = new AtomicBoolean(false);
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        final CountDownLatch pShutdownLatch = new CountDownLatch(1);

        final PlatformImpl.FinishListener listener = new PlatformImpl.FinishListener() {
            @Override public void idle() {
//                System.err.println("JavaFX Launcher: system is idle");
                if (startCalled.get()) {
                    shutdownLatch.countDown();
                } else if (pStartCalled.get()) {
                    pShutdownLatch.countDown();
                }
            }

            @Override public void exitCalled() {
//                System.err.println("JavaFX Launcher: received exit notification");
                exitCalled.set(true);
                shutdownLatch.countDown();
                // TODO: handle exit call from preloader
            }
        };
        PlatformImpl.addListener(listener);

        try {
            Preloader pldr = null;
            if (preloaderClass != null) {
                // Construct an instance of the preloader and call its init
                // method on this thread. Then call the start method on the FX thread.
                try {
                    Constructor<? extends Preloader> c = preloaderClass.getConstructor();
                    pldr = c.newInstance();
                    // Set startup parameters
                    ParametersImpl.registerParameters(pldr, new ParametersImpl(args));
                } catch (Throwable t) {
                    System.err.println("Exception in Preloader constructor");
                    pConstructorError = t;
                    error = true;
                }
            }
            currentPreloader = pldr;

            // Call init method unless exit called or error detected
            if (currentPreloader != null && !error && !exitCalled.get()) {
                try {
                    // Call the application init method (on the Launcher thread)
                    currentPreloader.init();
                } catch (Throwable t) {
                    System.err.println("Exception in Preloader init method");
                    pInitError = t;
                    error = true;
                }
            }

            // Call start method unless exit called or error detected
            if (currentPreloader != null && !error && !exitCalled.get()) {
                // Call the application start method on FX thread
                PlatformImpl.runAndWait(new Runnable() {
                    @Override public void run() {
                        try {
                            pStartCalled.set(true);

                            // Create primary stage and call preloader start method
                            final Stage primaryStage = new Stage();
                            primaryStage.impl_setPrimary(true);
                            currentPreloader.start(primaryStage);
                        } catch (Throwable t) {
                            System.err.println("Exception in Preloader start method");
                            pStartError = t;
                            error = true;
                        }
                    }
                });

                // Notify preloader of progress
                if (!error && !exitCalled.get()) {
                    notifyProgress(currentPreloader, 0.0);
                }
            }

            // Construct an instance of the application and call its init
            // method on this thread. Then call the start method on the FX thread.
            Application app = null;
            if (!error && !exitCalled.get()) {
                if (currentPreloader != null) {
                    if (simulateSlowProgress) {
                        for (int i = 0; i < 100; i++) {
                            notifyProgress(currentPreloader, (double)i / 100.0);
                            Thread.sleep(10);
                        }
                    }
                    notifyProgress(currentPreloader, 1.0);
                    notifyStateChange(currentPreloader,
                            StateChangeNotification.Type.BEFORE_LOAD, null);
                }

                try {
                    Constructor<? extends Application> c = appClass.getConstructor();
                    app = c.newInstance();
                    // Set startup parameters
                    ParametersImpl.registerParameters(app, new ParametersImpl(args));
                } catch (Throwable t) {
                    System.err.println("Exception in Application constructor");
                    constructorError = t;
                    error = true;
                }
            }
            final Application theApp = app;

            // Call init method unless exit called or error detected
            if (!error && !exitCalled.get()) {
                if (currentPreloader != null) {
                    notifyStateChange(currentPreloader,
                            StateChangeNotification.Type.BEFORE_INIT, theApp);
                }

                try {
                    // Call the application init method (on the Launcher thread)
                    theApp.init();
                } catch (Throwable t) {
                    System.err.println("Exception in Application init method");
                    initError = t;
                    error = true;
                }
            }

            // Call start method unless exit called or error detected
            if (!error && !exitCalled.get()) {
                if (currentPreloader != null) {
                    notifyStateChange(currentPreloader,
                            StateChangeNotification.Type.BEFORE_START, theApp);
                }
                // Call the application start method on FX thread
                PlatformImpl.runAndWait(new Runnable() {
                    @Override public void run() {
                        try {
                            startCalled.set(true);

                            // Create primary stage and call application start method
                            final Stage primaryStage = new Stage();
                            primaryStage.impl_setPrimary(true);
                            theApp.start(primaryStage);
                        } catch (Throwable t) {
                            System.err.println("Exception in Application start method");
                            startError = t;
                            error = true;
                        }
                    }
                });
            }

            if (!error) {
                shutdownLatch.await();
//                System.err.println("JavaFX Launcher: time to call stop");
            }

            // Call stop method if start was called
            if (startCalled.get()) {
                // Call Application stop method on FX thread
                PlatformImpl.runAndWait(new Runnable() {
                    @Override public void run() {
                        try {
                            theApp.stop();
                        } catch (Throwable t) {
                            System.err.println("Exception in Application stop method");
                            stopError = t;
                            error = true;
                        }
                    }
                });
            }

            if (error) {
                if (pConstructorError != null) {
                    throw new RuntimeException("Unable to construct Preloader instance: "
                            + appClass, pConstructorError);
                } else if (pInitError != null) {
                    throw new RuntimeException("Exception in Preloader init method",
                            pInitError);
                } else if(pStartError != null) {
                    throw new RuntimeException("Exception in Preloader start method",
                            pStartError);
                } else if (pStopError != null) {
                    throw new RuntimeException("Exception in Preloader stop method",
                            pStopError);
                } else if (constructorError != null) {
                    String msg = "Unable to construct Application instance: " + appClass;
                    if (!notifyError(msg, constructorError)) {
                        throw new RuntimeException(msg, constructorError);
                    }
                } else if (initError != null) {
                    String msg = "Exception in Application init method";
                    if (!notifyError(msg, initError)) {
                        throw new RuntimeException(msg, initError);
                    }
                } else if(startError != null) {
                    String msg = "Exception in Application start method";
                    if (!notifyError(msg, startError)) {
                        throw new RuntimeException(msg, startError);
                    }
                } else if (stopError != null) {
                    String msg = "Exception in Application stop method";
                    if (!notifyError(msg, stopError)) {
                        throw new RuntimeException(msg, stopError);
                    }
                }
            }
        } finally {
            PlatformImpl.removeListener(listener);
            // Workaround until RT-13281 is implemented
            // Don't call exit if we detect an error in javaws mode
//            PlatformImpl.tkExit();
            final boolean isJavaws = System.getSecurityManager() != null;
            if (error && isJavaws) {
                System.err.println("Workaround until RT-13281 is implemented: keep toolkit alive");
            } else {
                PlatformImpl.tkExit();
            }
        }
    }

    private static void notifyStateChange(final Preloader preloader,
            final StateChangeNotification.Type type,
            final Application app) {

        PlatformImpl.runAndWait(new Runnable() {
            @Override public void run() {
                preloader.handleStateChangeNotification(
                    new Preloader.StateChangeNotification(type, app));
            }
        });
    }

    private static void notifyProgress(final Preloader preloader, final double d) {
        PlatformImpl.runAndWait(new Runnable() {
            @Override public void run() {
                preloader.handleProgressNotification(
                        new Preloader.ProgressNotification(d));
            }
        });
    }

    private static boolean notifyError(final String msg, final Throwable constructorError) {
        final AtomicBoolean result = new AtomicBoolean(false);
        PlatformImpl.runAndWait(new Runnable() {
            @Override public void run() {
                if (currentPreloader != null) {
                    try {
                        ErrorNotification evt = new ErrorNotification(null, msg, constructorError);
                        boolean rval = currentPreloader.handleErrorNotification(evt);
                        result.set(rval);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        });

        return result.get();
    }

    private static void notifyCurrentPreloader(final PreloaderNotification pe) {
        PlatformImpl.runAndWait(new Runnable() {
            @Override public void run() {
                if (currentPreloader != null) {
                    currentPreloader.handleApplicationNotification(pe);
                }
            }
        });
    }

    private static Method notifyMethod = null;

    public static void notifyPreloader(Application app, final PreloaderNotification info) {
        if (launchCalled.get()) {
            // Standalone launcher mode
            notifyCurrentPreloader(info);
            return;
        }

        synchronized (LauncherImpl.class) {
            if (notifyMethod == null) {
                final String fxPreloaderClassName =
                        "com.sun.deploy.uitoolkit.impl.fx.FXPreloader";
                try {
                    Class fxPreloaderClass = Class.forName(fxPreloaderClassName);
                    notifyMethod = fxPreloaderClass.getMethod(
                            "notifyCurrentPreloader", PreloaderNotification.class);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return;
                }
            }
        }

        try {
            // Call using reflection: FXPreloader.notifyCurrentPreloader(pe)
            notifyMethod.invoke(null, info);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Not an instantiable class.
    private LauncherImpl() {
        // Should never get here.
        throw new InternalError();
    }

}