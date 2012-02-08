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

package javafx.stage;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.image.Image;

import com.sun.javafx.beans.annotations.Default;
import com.sun.javafx.collections.TrackableObservableList;
import com.sun.javafx.robot.impl.FXRobotHelper;
import com.sun.javafx.stage.StageHelper;
import com.sun.javafx.stage.StagePeerListener;
import com.sun.javafx.tk.TKStage;
import com.sun.javafx.tk.Toolkit;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * The JavaFX {@code Stage} class is the top level JavaFX container.
 * The primary Stage is constructed by the platform. Additional Stage
 * objects may be constructed by the application.
 *
 * <p>
 * Stage objects must be constructed and modified on the
 * JavaFX Application Thread.
 * </p>
 *
 * <p>Example:</p>
 *
 *
<pre><code>
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene; 
import javafx.scene.text.Text; 
import javafx.stage.Stage; 

public class HelloWorld extends Application {

    &#64;Override public void start(Stage stage) {
        Scene scene = new Scene(new Group(new Text(25, 25, "Hello World!"))); 

        stage.setTitle("Welcome to JavaFX!"); 
        stage.setScene(scene); 
        stage.sizeToScene(); 
        stage.show(); 
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}

 * </code></pre>
 * <p>produces the following on Mac OSX:</p>
 * <p><img src="doc-files/Stage0-mac.png"/></p>
 *
 * <p>produces the following on Windows XP:</p>
 * <p><img src="doc-files/Stage0-xp.png"/></p>
 *
 * <p>produces the following on Windows Vista:</p>
 * <p><img src="doc-files/Stage0-vista.png"/></p>
 *
 */
public class Stage extends Window {

    private boolean inNestedEventLoop = false;

    private static ObservableList<Stage> stages = FXCollections.<Stage>observableArrayList();

    static {
        FXRobotHelper.setStageAccessor(new FXRobotHelper.FXRobotStageAccessor() {
            @Override public ObservableList<Stage> getStages() {
                return stages;
            }
        });
        StageHelper.setStageAccessor(new StageHelper.StageAccessor() {
            @Override public ObservableList<Stage> getStages() {
                return stages;
            }
        });
    }

    /**
     * Creates a new instance of decorated {@code Stage}.
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     */
    public Stage() {
        this(StageStyle.DECORATED);
    }

    /**
     * Creates a new instance of {@code Stage}.
     *
     * @param style The style of the {@code Stage}
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     */
    public Stage(@Default("javafx.stage.StageStyle.DECORATED") StageStyle style) {
        super();

        Toolkit.getToolkit().checkFxUserThread();

        // Set the style
        initStyle(style);
    }
    
    /**
     * Specify the scene to be used on this stage.
     */
    @Override final public void setScene(Scene value) {
        super.setScene(value);
    }
    
    /**
     * @InheritDoc
     */
    @Override public final void show() {
        super.show();
    }
    
    // TODO do I also want to expose the model as being a writable model?
    
    private boolean primary = false;

    /**
     * sets this stage to be the primary stage.
     * When run as an applet, this stage will appear in the broswer
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_setPrimary(boolean primary) {
        this.primary = primary;
    }

    // TODO: consider making this public
    /**
     * Returns whether this stage is the primary stage.
     * When run as an applet, the primary stage will appear in the broswer
     *
     * @return true if this stage is the primary stage for the application.
     */
    boolean isPrimary() {
        return primary;
    }
    
    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override
    public String impl_getMXWindowType() {
        return (primary) ? "PrimaryStage" : getClass().getSimpleName();
    }

    private boolean important = true;

    /**
     * Sets a flag indicating whether this stage is an "important" window for
     * the purpose of determining whether the application is idle and should
     * exit. The application is considered finished when the last important
     * window is closed.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_setImportant(boolean important) {
        this.important = important;
    }

    private boolean isImportant() {
        return important;
    }

    /**
     * Show the stage and wait for it to be closed before returning to the
     * caller. This must be called on the FX Application thread. The stage
     * must not already be visible prior to calling this method. This must not
     * be called on the primary stage.
     *
     * @throws IllegalStateException if this method is called on a thread
     * other than the JavaFX Application Thread.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_showAndWait() {

        Toolkit.getToolkit().checkFxUserThread();

        if (isPrimary()) {
            throw new IllegalStateException("Cannot call this method on primary stage");
        }

        if (isShowing()) {
            throw new IllegalStateException("Stage already visible");
        }

        show();
        inNestedEventLoop = true;
        Toolkit.getToolkit().enterNestedEventLoop(this);
    }

    private StageStyle style; // default is set in constructor

    /**
     * Specifies the style for this stage. This must be done prior to making
     * the stage visible. The style is one of: StageStyle.DECORATED,
     * StageStyle.UNDECORATED, StageStyle.TRANSPARENT, or StageStyle.UTILITY.
     *
     * @param style the style for this stage.
     *
     * @throws IllegalStateException if this property is set after the stage
     * has ever been made visible.
     *
     * @defaultvalue StageStyle.DECORATED
     */
    public final void initStyle(StageStyle style) {
        if (hasBeenVisible) {
            throw new IllegalStateException("Cannot set style once stage has been set visible");
        }
        this.style = style;
    }

    /**
     * Retrieves the style attribute for this stage.
     *
     * @return the stage style.
     */
    public final StageStyle getStyle() {
        return style;
    }

    private Modality modality = Modality.NONE;

    /**
     * Specifies the modality for this stage. This must be done prior to making
     * the stage visible. The modality is one of: Modality.NONE,
     * Modality.WINDOW_MODAL, or Modality.APPLICATION_MODAL.
     *
     * @param modality the modality for this stage.
     *
     * @throws IllegalStateException if this property is set after the stage
     * has ever been made visible.
     *
     * @throws IllegalStateException if this stage is the primary stage.
     *
     * @defaultvalue Modality.NONE
     */
    public final void initModality(Modality modality) {
        if (hasBeenVisible) {
            throw new IllegalStateException("Cannot set modality once stage has been set visible");
        }

        if (isPrimary()) {
            throw new IllegalStateException("Cannot set modality for the primary stage");
        }

        this.modality = modality;
    }

    /**
     * Retrieves the modality attribute for this stage.
     *
     * @return the modality.
     */
    public final Modality getModality() {
        return modality;
    }

    private Window owner = null;

    /**
     * Specifies the owner Window for this stage, or null for a top-level,
     * unowned stage. This must be done prior to making the stage visible.
     *
     * @param owner the owner for this stage.
     *
     * @throws IllegalStateException if this property is set after the stage
     * has ever been made visible.
     *
     * @throws IllegalStateException if this stage is the primary stage.
     *
     * @defaultvalue null
     */
    public final void initOwner(Window owner) {
        if (hasBeenVisible) {
            throw new IllegalStateException("Cannot set owner once stage has been set visible");
        }

        if (isPrimary()) {
            throw new IllegalStateException("Cannot set owner for the primary stage");
        }

        this.owner = owner;

    }

    /**
     * Retrieves the owner Window for this stage, or null for an unowned stage.
     *
     * @return the owner Window.
     */
    public final Window getOwner() {
        return owner;
    }

    /**
     * Specifies whether this {@code Stage} should be a full-screen,
     * undecorated window.
     * <p>
     * The implementation of full-screen mode is platform and profile-dependent.
     * </p>
     * <p>
     * When set to {@code true}, the {@code Stage} will attempt to enter
     * full-screen mode when visible. Set to {@code false} to return {@code Stage}
     * to windowed mode.
     * An {@link IllegalStateException} is thrown if this property is set
     * on a thread other than the JavaFX Application Thread.
     * </p>
     * <p>
     * The full-screen mode will be exited (and the {@code fullScreen} attribute
     * will be set to {@code false}) if the full-screen
     * {@code Stage} loses focus or if another {@code Stage} enters
     * full-screen mode on the same {@link Screen}. Note that a {@code Stage}
     * in full-screen mode can become invisible without losing its
     * full-screen status and will again enter full-screen mode when the
     * {@code Stage} becomes visible.
     * </p>
     * If the platform supports multiple screens an application can control
     * which {@code Screen} the Stage will enter full-screen mode on by
     * setting its position to be within the bounds of that {@code Screen}
     * prior to entering full-screen mode.
     * <p>
     * However once in full-screen mode, {@code Stage}'s {@code x}, {@code y},
     * {@code width}, and {@code height} variables will continue to represent
     * the non-full-screen position and size of the window; the same for
     * {@code iconified}, {@code resizable}, {@code style}, and {@code
     * opacity}. If changes are made to any of these attributes while in
     * full-screen mode, upon exiting full-screen mode the {@code Stage} will
     * assume those attributes.
     * </p>
     *
     * Notes regarding desktop profile implementation.
     * <p>
     * For desktop profile the runtime will attempt to enter full-screen
     * exclusive mode (FSEM) if such is supported by the platform and it is
     * allowed for this application. If either is not the case a
     * simulated full-screen window will be used instead; the window will be
     * maximized, made undecorated if possible, and moved to the front.
     * </p>
     * The user can unconditionally exit full-screen mode at any time by
     * pressing {@code ESC}.
     * <p>
     * There are differences in behavior between signed and unsigned
     * applications. Signed applications are allowed to enter full-screen
     * exclusive mode unrestricted while unsigned applications will
     * have the following restrictions:
     * </p>
     * <ul>
     *  <li>Applications can only enter FSEM in response
     *   to user input. More specifically, entering is allowed from mouse
     *   ({@code Node.mousePressed/mouseReleased/mouseClicked}) or keyboard
     *   ({@code Node.keyPressed/keyReleased/keyTyped}) event handlers. It is
     *   not allowed to enter FSEM in response to {@code ESC}
     *   key. Attempting to enter FSEM from any other context will result in
     *   emulated full-screen mode.
     *   <p>
     *   If {@code Stage} was constructed as full-screen but not visible
     *   it will enter full-screen mode upon becoming visible, with the same
     *   limitations to when this is allowed to happen as when setting
     *   {@code fullScreen} to {@code true}.
     *   </p>
     *  </li>
     *  <li> If the application was allowed to enter FSEM
     *   it will have limited keyboard input. It will only receive KEY_PRESSED
     *   and KEY_RELEASED events from the following keys:
     *   {@code UP, DOWN, LEFT, RIGHT, SPACE, TAB, PAGE_UP, PAGE_DOWN, HOME, END, ENTER}
     *  </li>
     * </ul>
     * @defaultvalue false
     * @profile common
     */
    private ReadOnlyBooleanWrapper fullScreen;

    public final void setFullScreen(boolean value) {
        Toolkit.getToolkit().checkFxUserThread();
        fullScreenPropertyImpl().set(value);
    }

    public final boolean isFullScreen() {
        return fullScreen == null ? false : fullScreen.get();
    }

    public final ReadOnlyBooleanProperty fullScreenProperty() {
        return fullScreenPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyBooleanWrapper fullScreenPropertyImpl () {
        if (fullScreen == null) {
            fullScreen = new ReadOnlyBooleanWrapper() {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setFullScreen(get());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "fullScreen";
                }
            };
        }
        return fullScreen;
    }

    /**
     * Defines the icon images to be used in the window decorations and when
     * minimized. The images should be different sizes of the same image and
     * the best size will be chosen, eg. 16x16, 32,32.
     *
     * @defaultvalue empty
     */
    private ObservableList<Image> icons = new TrackableObservableList<Image>() {
        @Override protected void onChanged(Change<Image> c) {
            List<Object> platformImages = new ArrayList<Object>();
            for (Image icon : icons) {
                platformImages.add(icon.impl_getPlatformImage());
            }
            if (impl_peer != null) {
                impl_peer.setIcons(platformImages);
            }
        }
    };

    /**
     * Gets the icon images to be used in the window decorations and when
     * minimized. The images should be different sizes of the same image and
     * the best size will be chosen, eg. 16x16, 32,32.
     * @return An observable list of icons of this window
     */
    public final ObservableList<Image> getIcons() {
        return icons;
    }

    /**
     * Defines the title of the {@code Stage}.
     *
     * @defaultvalue empty string
     */
    private StringProperty title;

    public final void setTitle(String value) {
        titleProperty().set(value);
    }

    public final String getTitle() {
        return title == null ? null : title.get();
    }

    public final StringProperty titleProperty() {
        if (title == null) {
            title = new StringPropertyBase() {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setTitle(get());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "title";
                }
            };
        }
        return title;
    }

    /**
     * Defines whether the {@code Stage} is iconified or not.
     *
     * @profile common
     * @defaultvalue false
     */
    private ReadOnlyBooleanWrapper iconified;

    public final void setIconified(boolean value) {
        iconifiedPropertyImpl().set(value);
    }

    public final boolean isIconified() {
        return iconified == null ? false : iconified.get();
    }

    public final ReadOnlyBooleanProperty iconifiedProperty() {
        return iconifiedPropertyImpl().getReadOnlyProperty();
    }

    private final ReadOnlyBooleanWrapper iconifiedPropertyImpl() {
        if (iconified == null) {
            iconified = new ReadOnlyBooleanWrapper() {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setIconified(get());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "iconified";
                }
            };
        }
        return iconified;
    }

    /**
     * Defines whether the {@code Stage} is resizable or not by the user.
     * Programatically you may still change the size of the Stage. This is
     * a hint which allows the implementation to optionally make the Stage
     * resizable by the user.
     *
     * @profile common
     * @defaultvalue true
     */
    private BooleanProperty resizable;

    public final void setResizable(boolean value) {
        resizableProperty().set(value);
    }

    public final boolean isResizable() {
        return resizable == null ? true : resizable.get();
    }

    public final BooleanProperty resizableProperty() {
        if (resizable == null) {
            resizable = new BooleanPropertyBase(true) {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setResizable(get());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "resizable";
                }
            };
        }
        return resizable;
    }

    /**
     * Defines the minimum width of this {@code Stage}.
     *
     * @profile common
     * @defaultvalue 0
     */
    private DoubleProperty minWidth;

    public final void setMinWidth(double value) {
        minWidthProperty().set(value);
    }

    public final double getMinWidth() {
        return minWidth == null ? 0 : minWidth.get();
    }

    public final DoubleProperty minWidthProperty() {
        if (minWidth == null) {
            minWidth = new DoublePropertyBase(0) {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setMinimumSize((int) Math.ceil(get()),
                                (int) Math.ceil(getMinHeight()));
                    }
                    if (getWidth() < getMinWidth()) {
                        setWidth(getMinWidth());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "minWidth";
                }
            };
        }
        return minWidth;
    }

    /**
     * Defines the minimum height of this {@code Stage}.
     *
     * @profile common
     * @defaultvalue 0
     */
    private DoubleProperty minHeight;

    public final void setMinHeight(double value) {
        minHeightProperty().set(value);
    }

    public final double getMinHeight() {
        return minHeight == null ? 0 : minHeight.get();
    }

    public final DoubleProperty minHeightProperty() {
        if (minHeight == null) {
            minHeight = new DoublePropertyBase(0) {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setMinimumSize(
                                (int) Math.ceil(getMinWidth()),
                                (int) Math.ceil(get()));
                    }
                    if (getHeight() < getMinHeight()) {
                        setHeight(getMinHeight());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "minHeight";
                }
            };
        }
        return minHeight;
    }

    /**
     * Defines the maximum width of this {@code Stage}.
     *
     * @profile common
     * @defaultvalue Double.MAX_VALUE
     */
    private DoubleProperty maxWidth;

    public final void setMaxWidth(double value) {
        maxWidthProperty().set(value);
    }

    public final double getMaxWidth() {
        return maxWidth == null ? Double.MAX_VALUE : maxWidth.get();
    }

    public final DoubleProperty maxWidthProperty() {
        if (maxWidth == null) {
            maxWidth = new DoublePropertyBase(Double.MAX_VALUE) {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setMaximumSize((int) Math.floor(get()),
                                (int) Math.floor(getMaxHeight()));
                    }
                    if (getWidth() > getMaxWidth()) {
                        setWidth(getMaxWidth());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "maxWidth";
                }
            };
        }
        return maxWidth;
    }

    /**
     * Defines the maximum height of this {@code Stage}.
     *
     * @profile common
     * @defaultvalue Double.MAX_VALUE
     */
    private DoubleProperty maxHeight;

    public final void setMaxHeight(double value) {
        maxHeightProperty().set(value);
    }

    public final double getMaxHeight() {
        return maxHeight == null ? Double.MAX_VALUE : maxHeight.get();
    }

    public final DoubleProperty maxHeightProperty() {
        if (maxHeight == null) {
            maxHeight = new DoublePropertyBase(Double.MAX_VALUE) {

                @Override
                protected void invalidated() {
                    if (impl_peer != null) {
                        impl_peer.setMaximumSize(
                                (int) Math.floor(getMaxWidth()),
                                (int) Math.floor(get()));
                    }
                    if (getHeight() > getMaxHeight()) {
                        setHeight(getMaxHeight());
                    }
                }

                @Override
                public Object getBean() {
                    return Stage.this;
                }

                @Override
                public String getName() {
                    return "maxHeight";
                }
            };
        }
        return maxHeight;
    }

    
    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override protected void impl_visibleChanging(boolean value) {
        super.impl_visibleChanging(value);
        Toolkit toolkit = Toolkit.getToolkit();
        if (value && (impl_peer == null)) {
            // Setup the peer
            Window window = getOwner();
            TKStage tkStage = (window == null ? null : window.impl_getPeer());
            impl_peer = toolkit.createTKStage(getStyle(), isPrimary(),
                    getModality(), tkStage);
            impl_peer.setImportant(isImportant());
            peerListener = new StagePeerListener(this);

            // Finish initialization
            impl_peer.setResizable(isResizable());
            impl_peer.setFullScreen(isFullScreen());
            impl_peer.setIconified(isIconified());
            impl_peer.setTitle(getTitle());
            impl_peer.setMinimumSize((int) Math.ceil(getMinWidth()),
                    (int) Math.ceil(getMinHeight()));
            impl_peer.setMaximumSize((int) Math.floor(getMaxWidth()),
                    (int) Math.floor(getMaxHeight()));

            List<Object> platformImages = new ArrayList<Object>();
            for (Image icon : icons) {
                platformImages.add(icon.impl_getPlatformImage());
            }
            if (impl_peer != null) {
                impl_peer.setIcons(platformImages);
            }

            // Insert this into stages so we have a references to all created stages
            stages.add(this);
        }
    }

    
    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override protected void impl_visibleChanged(boolean value) {
        super.impl_visibleChanged(value);
        if (!value && (impl_peer != null)) {
            // Remove form active stage list
            stages.remove(this);
            peerListener = null;
            impl_peer = null;
        }

        if (!value && inNestedEventLoop) {
            inNestedEventLoop = false;
            Toolkit.getToolkit().exitNestedEventLoop(this, null);
        }
    }

    /**
     * Bring the {@code Window} to the foreground.  If the {@code Window} is
     * already in the foreground there is no visible difference.
     *
     * @profile common
     */
    public void toFront() {
        if (impl_peer != null) {
            impl_peer.toFront();
        }
    }

    /**
     * Send the {@code Window} to the background.  If the {@code Window} is
     * already in the background there is no visible difference.  This action
     * places this {@code Window} at the bottom of the stacking order on
     * platforms that support stacking.
     *
     * @profile common
     */
    public void toBack() {
        if (impl_peer != null) {
            impl_peer.toBack();
        }
    }

    // TODO: remove
    /**
     * Closes this {@code Stage}.
     * This call is equivalent to {@code hide()}.
     */
    public void close() {
        hide();
    }
}