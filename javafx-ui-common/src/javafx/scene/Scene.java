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


package javafx.scene;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javafx.runtime.SystemProperties;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodRequests;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.Mnemonic;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Window;
import javafx.util.Duration;

import com.sun.javafx.Logging;
import com.sun.javafx.Utils;
import com.sun.javafx.beans.annotations.Default;
import com.sun.javafx.beans.event.AbstractNotifyListener;
import com.sun.javafx.collections.TrackableObservableList;
import com.sun.javafx.css.StyleManager;
import com.sun.javafx.cursor.CursorFrame;
import com.sun.javafx.geom.PickRay;
import com.sun.javafx.logging.PlatformLogger;
import com.sun.javafx.perf.PerformanceTracker;
import com.sun.javafx.robot.impl.FXRobotHelper;
import com.sun.javafx.scene.CSSFlags;
import com.sun.javafx.scene.SceneEventDispatcher;
import com.sun.javafx.scene.traversal.Direction;
import com.sun.javafx.scene.traversal.TraversalEngine;
import com.sun.javafx.tk.TKDragGestureListener;
import com.sun.javafx.tk.TKDragSourceListener;
import com.sun.javafx.tk.TKDropTargetListener;
import com.sun.javafx.tk.TKPulseListener;
import com.sun.javafx.tk.TKScene;
import com.sun.javafx.tk.TKSceneListener;
import com.sun.javafx.tk.TKScenePaintListener;
import com.sun.javafx.tk.TKStage;
import com.sun.javafx.tk.Toolkit;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.ScrollEvent;


/**
 * The JavaFX {@code Scene} class is the container for all content in a scene graph.
 * The background of the scene is filled as specified by the {@code fill} property.
 * <p>
 * The application must specify the root {@code Node} for the scene graph by setting
 * the {@code root} property.   If a {@code Group} is used as the root, the
 * contents of the scene graph will be clipped by the scene's width and height and
 * changes to the scene's size (if user resizes the stage) will not alter the
 * layout of the scene graph.    If a resizable node (layout {@code Region} or
 * {@code Control} is set as the root, then the root's size will track the
 * scene's size, causing the contents to be relayed out as necessary.
 * <p>
 * The scene's size may be initialized by the application during construction.
 * If no size is specified, the scene will automatically compute its initial
 * size based on the preferred size of its content.
 *
 * <p>
 * Scene objects must be constructed and modified on the
 * JavaFX Application Thread.
 * </p>
 *
 * <p>Example:</p>
 *
 * <p>
 * <pre>
import javafx.scene.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;

Group root = new Group();
Scene s = new Scene(root, 300, 300, Color.BLACK);

Rectangle r = new Rectangle(25,25,250,250);
r.setFill(Color.BLUE);

root.getChildren().add(r);
 * </pre>
 * </p>
 *
 * @profile common
 */
@DefaultProperty("root")
public class Scene implements EventTarget {

    private double widthSetByUser = -1.0;
    private double heightSetByUser = -1.0;
    private boolean sizeInitialized = false;
    private boolean depthBuffer = false;

    private int dirtyBits;

    //Neither width nor height are initialized and will be calculated according to content when this Scene
    //is shown for the first time.
//    public Scene() {
//        //this(-1, -1, (Parent) new Group());
//        this(-1, -1, (Parent)null);
//    }

    /**
     * Creates a Scene for a specific root Node.
     *
     * @param root The root node of the scene graph
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     */
    public Scene(Parent root) {
        this(root, -1, -1, Color.WHITE, false);
    }

//Public constructor initializing public-init properties
//When width < 0, and or height < 0 is passed, then width and/or height are understood as unitialized
//Unitialized dimension is calculated when Scene is shown for the first time.
//    public Scene(
//            @Default("-1") double width,
//            @Default("-1") double height) {
//        //this(width, height, (Parent)new Group());
//        this(width, height, (Parent)null);
//    }
//
//    public Scene(double width, double height, Paint fill) {
//        //this(width, height, (Parent) new Group());
//        this(width, height, (Parent)null);
//        setFill(fill);
//    }

    /**
     * Creates a Scene for a specific root Node with a specific size.
     *
     * @param root The root node of the scene graph
     * @param width The width of the scene
     * @param height The height of the scene
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     */
    public Scene(Parent root, double width, double height) {
        this(root, width, height, Color.WHITE, false);
    }

    /**
     * Creates a Scene for a specific root Node with a fill.
     *
     * @param root The parent
     * @param fill The fill
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     */
    public Scene(Parent root, @Default("javafx.scene.paint.Color.WHITE") Paint fill) {
        this(root, -1, -1, fill, false);
    }

    /**
     * Creates a Scene for a specific root Node with a specific size and fill.
     *
     * @param root The root node of the scene graph
     * @param width The width of the scene
     * @param height The height of the scene
     * @param fill The fill
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     */
    public Scene(Parent root, double width, double height,
            @Default("javafx.scene.paint.Color.WHITE") Paint fill) {
        this(root, width, height, fill, false);
    }

    /**
     * Constructs a scene consisting of a root, with a dimension of width and
     * height, and specifies whether a depth buffer is created for this scene.
     *
     * @param root The root node of the scene graph
     * @param width The width of the scene
     * @param height The height of the scene
     * @param depthBuffer The depth buffer flag
     * <p>
     * The depthBuffer flag is a conditional feature and its default value is
     * false. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *
     * @throws IllegalStateException if this constructor is called on a thread
     * other than the JavaFX Application Thread.
     *
     * @see javafx.scene.Node#setDepthTest(DepthTest)
     * @profile common conditional scene3d
     */
    public Scene(Parent root, @Default("-1") double width, @Default("-1") double height, boolean depthBuffer) {
        this(root, width, height, Color.WHITE, depthBuffer);
    }

    private Scene(Parent root, double width, double height,
            @Default("javafx.scene.paint.Color.WHITE") Paint fill,
            boolean depthBuffer) {
        Toolkit.getToolkit().checkFxUserThread();
        setRoot(root);
        init(width, height, depthBuffer);
        setFill(fill);
    }

    static {
            PerformanceTracker.setSceneAccessor(new PerformanceTracker.SceneAccessor() {
                public void setPerfTracker(Scene scene, PerformanceTracker tracker) {
                    synchronized (trackerMonitor) {
                        scene.tracker = tracker;
                    }
                }
                public PerformanceTracker getPerfTracker(Scene scene) {
                    synchronized (trackerMonitor) {
                        return scene.tracker;
                    }
                }
            });
            FXRobotHelper.setSceneAccessor(new FXRobotHelper.FXRobotSceneAccessor() {
                public void processKeyEvent(Scene scene, KeyEvent keyEvent) {
                    scene.impl_processKeyEvent(keyEvent);
                }
                public void processMouseEvent(Scene scene, MouseEvent mouseEvent) {
                    scene.impl_processMouseEvent(mouseEvent);
                }
                public void processScrollEvent(Scene scene, ScrollEvent scrollEvent) {
                    scene.processScrollEvent(scrollEvent);
                }
                public ObservableList<Node> getChildren(Parent parent) {
                    return parent.getChildren(); //was impl_getChildren
                }
                public Object renderToImage(Scene scene, Object platformImage) {
                    return scene.renderToImage(platformImage);
                }
            });
        }

        // Reserve space for 30 nodes in the dirtyNodes and dirtyCSSNodes sets.
        // We need to account for the default HashSet load factor.
        private static final double HASH_LOAD = 0.75f; // default load factor for HashSet
        private static final int MIN_DIRTY_CAPACITY = (int) (30.0f / HASH_LOAD);

        // For debugging
        private static boolean inSynchronizer = false;
        private static boolean inMousePick = false;
        private static boolean allowPGAccess = false;
        private static int pgAccessCount = 0;

        /**
         * Used for debugging purposes. Returns true if we are in either the
         * mouse event code (picking) or the synchronizer, or if the scene is
         * not yet initialized,
         *
         * @treatasprivate implementation detail
         * @deprecated This is an internal API that is not intended for use and will be removed in the next version
         */
        @Deprecated
        public static boolean impl_isPGAccessAllowed() {
            return inSynchronizer || inMousePick || allowPGAccess;
        }

        /**
         * @treatasprivate implementation detail
         * @deprecated This is an internal API that is not intended for use and will be removed in the next version
         */
        @Deprecated
        public static void impl_setAllowPGAccess(boolean flag) {
            if (Utils.assertionEnabled()) {
                if (flag) {
                    pgAccessCount++;
                    allowPGAccess = true;
                }
                else {
                    if (pgAccessCount <= 0) {
                        throw new java.lang.AssertionError("*** pgAccessCount underflow");
                    }
                    if (--pgAccessCount == 0) {
                        allowPGAccess = false;
                    }
                }
            }
        }

        /**
         * If true, use the platform's drag gesture detection
         * else use Scene-level detection as per DnDGesture.process(MouseEvent, List)
         */
        private static final boolean PLATFORM_DRAG_GESTURE_INITIATION = false;

    /**
     * Set of dirty nodes; processed once per frame by the synchronizer.
     * When a node's state changes such that it becomes "dirty" with respect
     * to the graphics stack and requires synchronization, then that node
     * is added to this list. Note that if state on the Node changes, but it
     * was already dirty, then the Node doesn't add itself again.
     * <p>
     * We need this to be a set so that adding and removing nodes from the list
     * will be inexpensive (constant time); we use a LinkedHashSet so that
     * iteration performance won't suffer.
     * <p>
     * Because at initialization time every node in the scene graph is dirty,
     * we have a special state and special code path during initialization
     * that does not involve adding each node to the dirtyNodes list. When
     * dirtyNodes is null, that means this Scene has not yet been synchronized.
     * A good default size is then created for the dirtyNodes list.
     * <p>
     * We double-buffer the set so that we can add new nodes to the
     * set while processing the existing set. This avoids our having to
     * take a snapshot of the set (e.g., with toArray()) and reduces garbage.
     */
    private LinkedHashSet dirtyNodesA;
    private LinkedHashSet dirtyNodesB;
    private LinkedHashSet dirtyNodes; // refers to dirtyNodesA or dirtyNodesB

    /**
     * Add the specified node to this scene's dirty list. Called by the
     * markDirty method in Node or when the Node's scene changes.
     */
    void addToDirtyList(Node n) {
        if (dirtyNodes == null || dirtyNodes.isEmpty()) {
            if (impl_peer != null) {
                Toolkit.getToolkit().requestNextPulse();
            }
        }

        if (dirtyNodes != null) dirtyNodes.add(n);
    }

    void removeFromDirtyList(Node n) {
        if (dirtyNodes != null)
            dirtyNodes.remove(n);
    }

    private void doCSSPass() {
        StyleManager.getInstance().clearCachedValues(this);
        final Parent sceneRoot = getRoot();
        //
        // RT-17547: when the tree is synchronized, the dirty bits are
        // are cleared but the cssFlag might still be something other than
        // clean. 
        //
        // Before RT-17547, the code checked the dirty bit. But this is
        // superfluous since the dirty bit will be set if the flag is not clean,
        // but the flag will never be anything other than clean if the dirty
        // bit is not set. The dirty bit is still needed, however, since setting 
        // it ensures a pulse if no other dirty bits have been set. 
        //
        // For the purpose of showing the change, the dirty bit 
        // check code was commented out and not removed. 
        //
//        if (sceneRoot.impl_isDirty(com.sun.javafx.scene.DirtyBits.NODE_CSS)) {
        if (sceneRoot.cssFlag != CSSFlags.CLEAN) {
            // The dirty bit isn't checked but we must ensure it is cleared.
            // The cssFlag is set to clean in either Node.processCSS or 
            // Node.impl_processCSS(boolean)
            sceneRoot.impl_clearDirty(com.sun.javafx.scene.DirtyBits.NODE_CSS);
            sceneRoot.processCSS();
        }
    }

    /**
     * List of dirty layout roots.
     * When a parent is either marked as a layout root or is unmanaged and it
     * has its needsLayout flag set to true, then that node is added to this list
     * so that it can be laid out on the next pulse without requiring its
     * ancestors to be laid out.
     */
    private List dirtyLayoutRoots = new ArrayList(10);

    /**
     * Add the specified parent to this scene's dirty layout list.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void addToDirtyLayoutList(Parent p) {
        // If the current size of the list is 0 then we will need to schedule
        // a pulse event because a layout pass is needed.
        if (dirtyLayoutRoots.isEmpty()) {
            Toolkit.getToolkit().requestNextPulse();
        }
        // Add the node.
        if (!dirtyLayoutRoots.contains(p)) {
            dirtyLayoutRoots.add(p);
        }
    }

    /**
     * Remove the specified parent from this scene's dirty layout list.
     */
    void removeFromDirtyLayoutList(Parent p) {
        if (dirtyLayoutRoots.contains(p)) {
            dirtyLayoutRoots.remove(p);
        }
    }

    private void doLayoutPass() {
        // sometimes a layout pass with cause scene-graph changes (bounds/structure)
        // that leave some branches needing further layout, so pass through roots twice
        layoutDirtyRoots();
        layoutDirtyRoots();

        // we don't want to spin too long in layout, so if there are still dirty
        // roots, we'll leave those for next pulse.
        if (dirtyLayoutRoots.size() > 0) {
            PlatformLogger logger = Logging.getLayoutLogger();
            if (logger.isLoggable(PlatformLogger.FINER)) {
                logger.finer("after layout pass, "+dirtyLayoutRoots.size()+" layout root nodes still dirty");
            }
            Toolkit.getToolkit().requestNextPulse();
        }
    }

    private void layoutDirtyRoots() {
        if (dirtyLayoutRoots.size() > 0) {
            PlatformLogger logger = Logging.getLayoutLogger();
            ArrayList temp = new ArrayList(dirtyLayoutRoots);
            dirtyLayoutRoots.clear();
            int cnt = temp.size();

            for (int i = 0; i < cnt; i++) {
                final Parent parent = (Parent) temp.get(i);
                if (parent.getScene() == this && parent.isNeedsLayout()) {
                    if (logger.isLoggable(PlatformLogger.FINE)) {
                        logger.fine("<<< START >>> root = "+parent.toString());
                    }
                    parent.layout();
                    if (logger.isLoggable(PlatformLogger.FINE)) {
                        logger.fine("<<<  END  >>> root = "+parent.toString());
                    }
                }
            }
        }
    }

    /**
     * The peer of this scene
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    private TKScene impl_peer;

    /**
     * Get Scene's peer
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public TKScene impl_getPeer() {
        return impl_peer;
    }

    /**
     * The scene pulse listener that gets called on toolkit pulses
     */
    ScenePulseListener scenePulseListener = new ScenePulseListener();

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public TKPulseListener impl_getScenePulseListener() {
        if (SystemProperties.isDebug()) {
            return scenePulseListener;
        }
        return null;
    }

    /**
     * The {@code Window} for this {@code Scene}
     */
    private ReadOnlyObjectWrapper<Window> window;

    private void setWindow(Window value) {
        windowPropertyImpl().set(value);
    }

    public final Window getWindow() {
        return window == null ? null : window.get();
    }

    public final ReadOnlyObjectProperty<Window> windowProperty() {
        return windowPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<Window> windowPropertyImpl() {
        if (window == null) {
            window = new ReadOnlyObjectWrapper<Window>() {
                @Override protected void invalidated() {
                    if (get() != null) {
                        impl_disposePeer();
                    }
                    Window oldWindow = get();
                    KeyHandler kh = getKeyHandler();
                    kh.windowForSceneChanged(oldWindow, get());
                    if (get() != null) {
                        impl_initPeer();
                    }
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "window";
                }
            };
        }
        return window;
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_setWindow(Window value) {
        setWindow(value);
        if (impl_peer != null) {
            impl_peer.markDirty();
        }
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_initPeer() {
        if (impl_peer != null) {
            return;
        }
        PerformanceTracker.logEvent("Scene.initPeer started");
        Toolkit tk = Toolkit.getToolkit();
        if (getWindow() == null) {
            return;
        }
        TKStage windowPeer = getWindow().impl_getPeer();
        if (windowPeer == null) {
            return;
        }

        impl_setAllowPGAccess(true);

        impl_peer = windowPeer.createTKScene(isDepthBuffer());
        PerformanceTracker.logEvent("Scene.initPeer TKScene created");
        impl_peer.setTKSceneListener(new ScenePeerListener());
        impl_peer.setTKScenePaintListener(new ScenePeerPaintListener());
        impl_peer.setScene(this);
        PerformanceTracker.logEvent("Scene.initPeer TKScene set");
        impl_peer.setRoot(getRoot().impl_getPGNode());
        if (!sizeInitialized) {
            // content may have been added after scene was constructed, so
            // try again to set size based on content if the scene or window
            // weren't explicitly sized;
            // (TODO): ideally we'd do this just once, at the latest point
            // we could, before the window was assigned a size and made visible
            preferredSize();
        }
        impl_peer.setFillPaint(getFill() == null ? null : tk.getPaint(getFill()));
        impl_peer.setCamera(getCamera() == null ? null : getCamera().getPlatformCamera());

        impl_setAllowPGAccess(false);

        PerformanceTracker.logEvent("Scene.initPeer TKScene initialized");
        tk.addSceneTkPulseListener(scenePulseListener);
        // listen to dnd gestures coming from the platform
        if (PLATFORM_DRAG_GESTURE_INITIATION) {
            if (dragGestureListener == null) {
                dragGestureListener = new DragGestureListener();
            }
            tk.registerDragGestureListener(impl_peer, EnumSet.allOf(TransferMode.class), dragGestureListener);
        }
        tk.enableDrop(impl_peer, new DropTargetListener());
        tk.installInputMethodRequests(impl_peer, new InputMethodRequestsDelegate());
        PerformanceTracker.logEvent("Scene.initPeer finished");
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_disposePeer() {
        if (impl_peer == null) {
            return;
        }
        Toolkit tk = Toolkit.getToolkit();
        tk.removeSceneTkPulseListener(scenePulseListener);
        impl_peer.setScene(null);
        impl_peer = null;
    }

    DnDGesture dndGesture = null;
    DragGestureListener dragGestureListener;
    /**
     * The horizontal location of this {@code Scene} on the {@code Window}.
     */
    private ReadOnlyDoubleWrapper x;

    private final void setX(double value) {
        xPropertyImpl().set(value);
    }

    public final double getX() {
        return x == null ? 0.0 : x.get();
    }

    public final ReadOnlyDoubleProperty xProperty() {
        return xPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyDoubleWrapper xPropertyImpl() {
        if (x == null) {
            x = new ReadOnlyDoubleWrapper(this, "x");
        }
        return x;
    }

    /**
     * The vertical location of this {@code Scene} on the {@code Window}.
     */
    private ReadOnlyDoubleWrapper y;

    private final void setY(double value) {
        yPropertyImpl().set(value);
    }

    public final double getY() {
        return y == null ? 0.0 : y.get();
    }

    public final ReadOnlyDoubleProperty yProperty() {
        return yPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyDoubleWrapper yPropertyImpl() {
        if (y == null) {
            y = new ReadOnlyDoubleWrapper(this, "y");
        }
        return y;
    }

    /**
     * The width of this {@code Scene}
     *
     * @profile common
     */
    private ReadOnlyDoubleWrapper width;

    private final void setWidth(double value) {
        widthPropertyImpl().set(value);
    }

    public final double getWidth() {
        return width == null ? 0.0 : width.get();
    }

    public final ReadOnlyDoubleProperty widthProperty() {
        return widthPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyDoubleWrapper widthPropertyImpl() {
        if (width == null) {
            width = new ReadOnlyDoubleWrapper() {

                @Override
                protected void invalidated() {
                    final Parent _root = getRoot();
                    if (_root.isResizable()) {
                        _root.resize(get() - _root.getLayoutX() - _root.getTranslateX(), _root.getLayoutBounds().getHeight());
                    }
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "width";
                }
            };
        }
        return width;
    }

    /**
     * The height of this {@code Scene}
     *
     * @profile common
     */
    private ReadOnlyDoubleWrapper height;

    private final void setHeight(double value) {
        heightPropertyImpl().set(value);
    }

    public final double getHeight() {
        return height == null ? 0.0 : height.get();
    }

    public final ReadOnlyDoubleProperty heightProperty() {
        return heightPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyDoubleWrapper heightPropertyImpl() {
        if (height == null) {
            height = new ReadOnlyDoubleWrapper() {

                @Override
                protected void invalidated() {
                    final Parent _root = getRoot();
                    if (_root.isResizable()) {
                        _root.resize(_root.getLayoutBounds().getWidth(), get() - _root.getLayoutY() - _root.getTranslateY());
                    }
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "height";
                }
            };
        }
        return height;
    }

    /**
     * Specifies the type of camera use for rendering this {@code Scene}.
     * If {@code camera} is null, a parallel camera is used for rendering.
     * <p>
     * Note: this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *
     * @profile common conditional scene3d
     * @defaultvalue null
     * @since JavaFX 1.3
     */
    private ObjectProperty<Camera> camera;

    public final void setCamera(Camera value) {
        cameraProperty().set(value);
    }

    public final Camera getCamera() {
        return camera == null ? null : camera.get();
    }

    private Camera oldCamera;
    public final ObjectProperty<Camera> cameraProperty() {
        if (camera == null) {
            camera = new ObjectPropertyBase<Camera>() {

                @Override
                protected void invalidated() {
                    if (oldCamera != null) {
                        oldCamera.dirtyProperty().removeListener(cameraChangeListener.getWeakListener());
                    }
                    oldCamera = get();
                    if (get() != null) {
                        get().dirtyProperty().addListener(cameraChangeListener.getWeakListener());
                    }
                    markDirty(DirtyBits.CAMERA_DIRTY);
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "camera";
                }
            };
        }
        return camera;
    }

    private final AbstractNotifyListener cameraChangeListener =
            new AbstractNotifyListener() {

        @Override public void invalidated(Observable valueModel) {
            if (getCamera().impl_isDirty()) {
                markDirty(DirtyBits.CAMERA_DIRTY);
            }
        }
    };

    /**
     * Defines the background fill of this {@code Scene}. Both a {@code null}
     * value meaning paint no background and a {@link javafx.scene.paint.Paint}
     * with transparency are supported, but what is painted behind it will
     * depend on the platform.  The default value is the color white.
     *
     * @profile common
     * @defaultvalue WHITE
     */
    private ObjectProperty<Paint> fill;

    public final void setFill(Paint value) {
        fillProperty().set(value);
    }

    public final Paint getFill() {
        return fill == null ? Color.WHITE : fill.get();
    }

    public final ObjectProperty<Paint> fillProperty() {
        if (fill == null) {
            fill = new ObjectPropertyBase<Paint>(Color.WHITE) {

                @Override
                protected void invalidated() {
                    markDirty(DirtyBits.FILL_DIRTY);
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "fill";
                }
            };
        }
        return fill;
    }

    /**
     * Defines the root {@code Node} of the scene graph.
     * If a {@code Group} is used as the root, the
     * contents of the scene graph will be clipped by the scene's width and height and
     * changes to the scene's size (if user resizes the stage) will not alter the
     * layout of the scene graph.    If a resizable node (layout {@code Region} or
     * {@code Control}) is set as the root, then the root's size will track the
     * scene's size, causing the contents to be relayed out as necessary.
     */
    private ObjectProperty<Parent> root;

    public final void setRoot(Parent value) {
        rootProperty().set(value);
    }

    public final Parent getRoot() {
        return root == null ? null : root.get();
    }

    Parent oldRoot;
    public final ObjectProperty<Parent> rootProperty() {
        if (root == null) {
            root = new ObjectPropertyBase<Parent>() {

                private void forceUnbind() {
                    System.err.println("Unbinding illegal root.");
                    unbind();
                }

                @Override
                protected void invalidated() {
                    Parent _value = get();

                    if (_value != null && _value.getParent() != null) {
                        if (isBound()) forceUnbind();
                        throw new IllegalArgumentException(_value +
                                "is already inside a scene-graph and cannot be set as root");
                    }
                    if (_value != null && _value.impl_getClipParent() != null) {
                        if (isBound()) forceUnbind();
                        throw new IllegalArgumentException(_value +
                                "is set as a clip on another node, so cannot be set as root");
                    }
                    if (_value != null && _value.isSceneRoot() && _value.getScene() != Scene.this) {
                        if (isBound()) forceUnbind();
                        throw new IllegalArgumentException(_value +
                                "is already set as root of another scene");
                    }

                    if (oldRoot != null) {
                        oldRoot.setScene(null);
                        oldRoot.setImpl_traversalEngine(null);
                    }
                    oldRoot = _value;
                    if (_value != null) {
                        if (_value.getImpl_traversalEngine() == null) {
                            _value.setImpl_traversalEngine(new TraversalEngine(_value, true));
                        }
                        _value.getStyleClass().add(0, "root");
                        _value.setScene(Scene.this);
                        markDirty(DirtyBits.ROOT_DIRTY);
                        if (impl_peer != null && !sizeInitialized) {
                            // Root was set after scene was initialized and peer created
                            preferredSize();
                        }
                        _value.resize(getWidth(), getHeight()); // maybe no-op if root is not resizable
                        _value.requestLayout();
                    }
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "root";
                }
            };
        }
        return root;
    }

    /**
     * Renders this {@code Scene} to the given platform-specific image
     * (e.g. a {@code BufferedImage} in the case of the Swing profile)
     * at a 1:1 scale.
     * If {@code platformImage} is null, a new platform-specific image
     * is returned.
     * If the contents of the scene have not changed since the last time
     * this method was called, this method returns null.
     *
     * WARNING: This method is not part of the public API and is
     * subject to change!  It is intended for use by the designer tool only.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Object renderToImage(Object platformImage) {
        return renderToImage(platformImage, 1.0f);
    }

    /**
     * Renders this {@code Scene} to the given platform-specific image
     * (e.g. a {@code BufferedImage} in the case of the Swing profile)
     * using the specified scaling factor.
     * If {@code platformImage} is null, a new platform-specific image
     * is returned.
     * If the contents of the scene have not changed since the last time
     * this method was called, this method returns null.
     *
     * WARNING: This method is not part of the public API and is
     * subject to change!  It is intended for use by the designer tool only.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Object renderToImage(Object platformImage, float scale) {
        return renderToImage(platformImage, scale, true);
    }

    private void doLayoutPassWithoutPulse(int maxAttempts) {
        for (int i = 0; dirtyLayoutRoots.size() > 0 && i != maxAttempts; ++i) {
            layoutDirtyRoots();
        }
    }
    
    /**
     * WARNING: This method is not part of the public API and is subject to change!
     * It is intended for use by the internal tools only.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Object renderToImage(Object platformImage, float scale, boolean syncNeeded) {
        
        if (!sizeInitialized) {
            preferredSize();
        } else {
            doCSSPass();
        }

        // we do not need pulse in the renderToImage code
        // because this scene can be stage-less
        doLayoutPassWithoutPulse(3);

        if (syncNeeded) {
            scenePulseListener.synchronizeSceneNodes();
        }
        
        impl_setAllowPGAccess(true);
        
        Toolkit tk = Toolkit.getToolkit();
        Toolkit.ImageRenderingContext context = new Toolkit.ImageRenderingContext();
        context.width = getWidth();
        context.height = getHeight();
        context.scale = scale;
        context.depthBuffer = isDepthBuffer();
        context.root = getRoot().impl_getPGNode();
        context.platformPaint = (getFill() == null ? null : tk.getPaint(getFill()));
        if (getCamera() != null) {
            getCamera().impl_update();
            context.camera = getCamera().getPlatformCamera();
        }
        context.platformImage = platformImage;
        impl_setAllowPGAccess(false);
        Object result = tk.renderToImage(context);
        
        // if this scene belongs to some stage 
        // we need to mark the entire scene as dirty 
        // because dirty logic is buggy
        
        if (this.impl_peer != null) {
            impl_peer.entireSceneNeedsRepaint();
        }
                
        return result;
    }

    // lets us know when initialized so our triggers can be a bit more effective
    boolean initialized = false;
    // This does not push changes to peer because cursor updates are pushed on mouse events

    /**
     * Defines the mouse cursor for this {@code Scene}.
     */
    private ObjectProperty<Cursor> cursor;

    public final void setCursor(Cursor value) {
        cursorProperty().set(value);
    }

    public final Cursor getCursor() {
        return cursor == null ? null : cursor.get();
    }

    public final ObjectProperty<Cursor> cursorProperty() {
        if (cursor == null) {
            cursor = new SimpleObjectProperty<Cursor>(this, "cursor");
        }
        return cursor;
    }

    /**
     * Looks for any node within the scene graph based on the specified CSS selector.
     * If more than one node matches the specified selector, this function
     * returns the first of them.
     * If no nodes are found with this id, then null is returned.
     *
     * @param selector The css selector to look up
     * @return the {@code Node} in the scene which matches the CSS {@code selector},
     * or {@code null} if none is found.
     *
     * @profile common
     */
     public Node lookup(String selector) {
         return getRoot().lookup(selector);
     }
    /**
     * A ObservableList of string URLs linking to the stylesheets to use with this scene's
     * contents. For additional information about using CSS with the
     * scene graph, see the <a href="doc-files/cssref.html">CSS Reference
     * Guide</a>.
     *
     * @profile common
     */
    private final ObservableList<String> stylesheets = new TrackableObservableList<String>() {
        @Override
        protected void onChanged(Change<String> c) {
            StyleManager.getInstance().updateStylesheets(Scene.this);
            getRoot().impl_reapplyCSS();
            // we'll immediately reapply the style for this scene. It might be
            // better to defer eventually
            // TODO needs to be wired up
            // StyleManager.getInstance().getStyleHelper(this);
        }
    };

    /**
     * Gets an observable list of string URLs linking to the stylesheets to use 
     * with this scene's contents. For additional information about using CSS 
     * with the scene graph, see the <a href="doc-files/cssref.html">CSS Reference
     * Guide</a>.
     *
     * @returns the list of stylesheets to use with this scene
     */
    public final ObservableList<String> getStylesheets() { return stylesheets; }

    /**
     * Invalidate all css styles in the scene, this will cause all css to be reapplied
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_invalidateCSS() {
        getRoot().impl_reapplyCSS();
    }

    /**
     * Retrieves the depth buffer attribute for this scene.
     * @return the depth buffer attribute.
     */
    public final boolean isDepthBuffer() {
        return depthBuffer;
    }

    private void init(double width, double height, boolean depthBuffer) {
        if (width >= 0) {
            widthSetByUser = width;
            setWidth((float)width);
        }
        if (height >= 0) {
            heightSetByUser = height;
            setHeight((float)height);
        }
        sizeInitialized = (widthSetByUser >= 0 && heightSetByUser >= 0);
        this.depthBuffer = depthBuffer;
        init();
    }

    private void init() {
        if (PerformanceTracker.isLoggingEnabled()) {
            PerformanceTracker.logEvent("Scene.init for [" + this + "]");
        }
        // TODO JASPER sure there is more init needed here

        mouseHandler = new MouseHandler();
        clickGenerator = new ClickGenerator();

        // TODO need to reimplement
        //StyleManager.getInstance().getStyleHelper(this);

        initialized = true;

        if (PerformanceTracker.isLoggingEnabled()) {
            PerformanceTracker.logEvent("Scene.init for [" + this + "] - finished");
        }
    }

    private void preferredSize() {
        final Parent root = getRoot();

        // one or the other isn't initialized, need to perform layout in
        // order to ensure we can properly measure the preferred size of the
        // scene
        doCSSPass();

        boolean computeWidth = false;
        boolean computeHeight = false;

        double rootWidth = widthSetByUser;
        double rootHeight = heightSetByUser;

        if (widthSetByUser < 0) {
            rootWidth = root.prefWidth(heightSetByUser >= 0 ? heightSetByUser : -1);
            rootWidth = root.boundedSize(rootWidth,
                    root.minWidth(heightSetByUser >= 0 ? heightSetByUser : -1),
                    root.maxWidth(heightSetByUser >= 0 ? heightSetByUser : -1));
            computeWidth = true;
        }
        if (heightSetByUser < 0) {
            rootHeight = root.prefHeight(widthSetByUser >= 0 ? widthSetByUser : -1);
            rootHeight = root.boundedSize(rootHeight,
                    root.minHeight(widthSetByUser >= 0 ? widthSetByUser : -1),
                    root.maxHeight(widthSetByUser >= 0 ? widthSetByUser : -1));
            computeHeight = true;
        }
        root.resize(rootWidth, rootHeight);
        doLayoutPass();

        if (computeWidth) {
            setWidth(root.isResizable()? root.getLayoutX() + root.getTranslateX() + root.getLayoutBounds().getWidth() :
                            root.getBoundsInParent().getMaxX());
        } else {
            setWidth(widthSetByUser);
        }

        if (computeHeight) {
            setHeight(root.isResizable()? root.getLayoutY() + root.getTranslateY() + root.getLayoutBounds().getHeight() :
                            root.getBoundsInParent().getMaxY());
        } else {
            setHeight(heightSetByUser);
        }

        sizeInitialized = (getWidth() > 0) && (getHeight() > 0);

        PerformanceTracker.logEvent("Scene preferred bounds computation complete");
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_preferredSize() {
        preferredSize();
    }

    private PerformanceTracker tracker;
    private static final Object trackerMonitor = new Object();

    // mouse events handling
    private MouseHandler mouseHandler;
    private ClickGenerator clickGenerator;

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_processMouseEvent(MouseEvent e) {
        if (e.getEventType() == MouseEvent.MOUSE_CLICKED) {
            // Ignore click generated by platform, we are generating
            // smarter clicks here by ClickGenerator
            return;
        }
        mouseHandler.process(e);
    }

    private void processMenuEvent(double x2, double y2, double xAbs, double yAbs, boolean isKeyboardTrigger) {
        final EventTarget eventTarget;
        if (!isKeyboardTrigger) Scene.inMousePick = true;
        if (isKeyboardTrigger) {
            Node sceneFocusOwner = keyHandler.getFocusOwner();
            eventTarget = sceneFocusOwner != null ? sceneFocusOwner : Scene.this;
        } else {
            EventTarget pickedTarget = null;
            if (getCamera() instanceof PerspectiveCamera) {
                final PickRay pickRay = new PickRay();
                Scene.this.impl_peer.computePickRay((float)x2, (float)y2, pickRay);
                pickedTarget = mouseHandler.pickNode(pickRay);
            }
            else {
                pickedTarget = mouseHandler.pickNode(x2, y2);
            }
            if (pickedTarget == null) {
                pickedTarget = Scene.this;
            }
            eventTarget = pickedTarget;
        }
        ContextMenuEvent context = ContextMenuEvent.impl_contextEvent(
                x2, y2, xAbs, yAbs, isKeyboardTrigger, ContextMenuEvent.CONTEXT_MENU_REQUESTED);
        Event.fireEvent(eventTarget, context);
        if (!isKeyboardTrigger) Scene.inMousePick = false;
    }

    private void processScrollEvent(ScrollEvent e) {
        EventTarget pickedTarget = null;

        if (e.getEventType() != MouseEvent.MOUSE_EXITED) {
            if (getCamera() instanceof PerspectiveCamera) {
                final PickRay pickRay = new PickRay();
                Scene.this.impl_peer.computePickRay((float)e.getX(), (float)e.getY(), pickRay);
                pickedTarget = mouseHandler.pickNode(pickRay);
            }
            else {
                pickedTarget = mouseHandler.pickNode(e.getX(), e.getY());
            }
        }

        if (pickedTarget == null) {
            pickedTarget = Scene.this;
        }

        Event.fireEvent(pickedTarget, e);
    }
    
    /**
     * Note: The only user of this method is in unit test: PickAndContainTest.
     */
    Node test_pick(double x, double y) {
        inMousePick = true;
        Node pickedNode = mouseHandler.pickNode(x, y);
        inMousePick = false;
        return pickedNode;
    }

    /***************************************************************************
     *                                                                         *
     * Key Events and Focus Traversal                                          *
     *                                                                         *
     **************************************************************************/

    /*
     * We cannot initialize keyHandler in init because some of the triggers
     * access it before the init block.
     * No clue why def keyHandler = bind lazy {KeyHandler{scene:this};}
     * does not compile.
     */
    private KeyHandler keyHandler = null;
    private KeyHandler getKeyHandler() {
        if (keyHandler == null) {
            keyHandler = new KeyHandler();
        }
        return keyHandler;
    }
    /**
     * Set to true if something has happened to the focused node that makes
     * it no longer eligible to have the focus.
     *
     * TODO: need to schedule a pulse if this turns true?
     */
    private boolean focusDirty = true;

    final void setFocusDirty(boolean value) {
        focusDirty = value;
    }

    final boolean isFocusDirty() {
        return focusDirty;
    }

    /**
     * This is a map from focusTraversable nodes within this scene
     * to instances of a traversal engine. The traversal engine is
     * either the instance for the scene itself, or for a Parent
     * nested somewhere within this scene.
     *
     * This has package access for testing purposes.
     */
    Map traversalRegistry; // Map<Node,TraversalEngine>

    /**
     * Searches up the scene graph for a Parent with a traversal engine.
     */
    private TraversalEngine lookupTraversalEngine(Node node) {
        Parent p = node.getParent();

        while (p != null) {
            if (p.getImpl_traversalEngine() != null) {
                return p.getImpl_traversalEngine();
            }
            p = p.getParent();
        }

        // This shouldn't ever occur, since walking up the tree
        // should always find the Scene's root, which always has
        // a traversal engine. But if for some reason we get here,
        // just return the root's traversal engine.

        return getRoot().getImpl_traversalEngine();
    }

    /**
     * Registers a traversable node with a traversal engine
     * on this scene.
     */
    void registerTraversable(Node n) {
        final TraversalEngine te = lookupTraversalEngine(n);
        if (te != null) {
            if (traversalRegistry == null) {
                traversalRegistry = new HashMap();
            }
            traversalRegistry.put(n, te);
            te.reg(n);
        }
    }

    /**
     * Unregisters a traversable node from this scene.
     */
    void unregisterTraversable(Node n) {
        final TraversalEngine te = (TraversalEngine) traversalRegistry.remove(n);
        if (te != null) {
            te.unreg(n);
        }
    }

    /**
     * Traverses focus from the given node in the given direction.
     */
    void traverse(Node node, Direction dir) {
        /*
        ** if the registry is null then there are no
        ** registered traversable nodes in this scene
        */
        if (traversalRegistry != null) {
            TraversalEngine te = (TraversalEngine) traversalRegistry.get(node);
            if (te == null) {
                te = lookupTraversalEngine(node);
            }
            te.trav(node, dir);
        }
    }

    /**
     * Moves the focus to a reasonable initial location. Called when a scene's
     * focus is dirty and there's no current owner, or if the owner has been
     * removed from the scene.
     */
    private void focusInitial() {
        getRoot().getImpl_traversalEngine().getTopLeftFocusableNode();
    }

    /**
     * Moves the focus to a reasonble location "near" the given node.
     * Called when the focused node is no longer eligible to have
     * the focus because it has become invisible or disabled. This
     * function assumes that it is still a member of the same scene.
     */
    private void focusIneligible(Node node) {
        traverse(node, Direction.NEXT);
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_processKeyEvent(KeyEvent e) {
        if (dndGesture != null) {
            if (!dndGesture.processKey(e)) {
                dndGesture = null;
            }
        }

        // inform key listeners
        int cnt = keyListeners.size();
        for (int i = 0; i < cnt; i++) {
            ((EventHandler<KeyEvent>)keyListeners.get(i)).handle(e);
        }

        getKeyHandler().process(e);

        // our little secret...
        if (!e.isConsumed() && e.getCode() == KeyCode.DIGIT8 &&
             e.getEventType() == KeyEvent.KEY_PRESSED && e.isControlDown() && e.isShiftDown()) {
            try {
                Class scenicview = Class.forName("com.javafx.experiments.scenicview.ScenicView");
                Class params[] = new Class[1];
                params[0] = Scene.class;
                java.lang.reflect.Method method = scenicview.getDeclaredMethod("show", params);
                method.invoke(null, this);

            } catch (Exception ex) {
                // oh well
                //System.out.println("exception instantiating ScenicView:"+ex);

            }
        }
    }

    private final ObservableList<EventHandler<KeyEvent>> keyListeners =
            FXCollections.<EventHandler<KeyEvent>>observableArrayList();

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_addKeyListener(EventHandler<KeyEvent> listener) {
        if (!keyListeners.contains(listener)) {
            keyListeners.add(listener);
        }
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_removeKeyListener(EventHandler<KeyEvent> listener) {
        if (keyListeners.contains(listener)) {
            keyListeners.remove(listener);
        }
    }

    void requestFocus(Node node) {
        getKeyHandler().requestFocus(node);
    }

    /**
     * Gets the scene's current focus owner node.
     *
     * TODO: probably should be removed in favor of impl_focusOwner below.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Node impl_getFocusOwner() {
        return getKeyHandler().getFocusOwner();
    }
    /**
     * The scene's current focus owner node. This node's "focused"
     * variable might be false if this scene has no window, or if the
     * window is inactive (window.focused == false).
     *
     * TODO this was added because of RT-3930. This needs to be reconciled
     * with impl_getFocusOwner(). We don't need both. Exposing a variable
     * is more powerful because it allows code to bind to it.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    private ObjectProperty<Node> impl_focusOwner;

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public final void setImpl_focusOwner(Node value) {
        impl_focusOwnerProperty().set(value);
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public final Node getImpl_focusOwner() {
        return impl_focusOwner == null ? null : impl_focusOwner.get();
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public final ObjectProperty<Node> impl_focusOwnerProperty() {
        if (impl_focusOwner == null) {
            impl_focusOwner = new SimpleObjectProperty<Node>(this, "impl_focusOwner");
        }
        return impl_focusOwner;
    }

    // For testing.
    void focusCleanup() {
        scenePulseListener.focusCleanup();
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_processInputMethodEvent(InputMethodEvent e) {
        Node node = impl_getFocusOwner();
        if (node != null) {
            node.fireEvent(e);
        }
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_enableInputMethodEvents(boolean enable) {
       if (impl_peer != null) {
           impl_peer.enableInputMethodEvents(enable);
       }
    }

    /**
     * Returns true if this scene is quiescent, i.e. it has no activity
     * pending on it such as CSS processing or layout requests.
     *
     * TODO this is for testing purposes only. It's not clear that
     * the set of things this checks is exactly right. It doesn't check
     * for events pending in the event queue for instance. However, it
     * seems to work reasonably well at present for UI testing.
     *
     * TODO this should be replaced with a better interface, say a
     * package-private interface that can be called from test support
     * code that has been loaded into javafx.scene.
     *
     * @return boolean indicating whether the scene is quiescent
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public boolean impl_isQuiescent() {
        return !isFocusDirty()
               && (getRoot().cssFlag == CSSFlags.CLEAN)
               && dirtyLayoutRoots.isEmpty();
    }

    /**
     * A listener for pulses, used for testing. If non-null, this is called at
     * the very end of ScenePulseListener.pulse().
     *
     * TODO this is public so it can be written to from test code. Ugly,
     * but effective. This should be replaced with a cleaner interface.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Runnable impl_testPulseListener = null;

    /**
     * Set the specified dirty bit and mark the peer as dirty
     */
    private void markDirty(DirtyBits dirtyBit) {
        setDirty(dirtyBit);
        if (impl_peer != null) {
            Toolkit.getToolkit().requestNextPulse();
        }
    }

    /**
     * Set the specified dirty bit
     */
    private void setDirty(DirtyBits dirtyBit) {
        dirtyBits |= dirtyBit.getMask();
    }

    /**
     * Test the specified dirty bit
     */
    private boolean isDirty(DirtyBits dirtyBit) {
        return ((dirtyBits & dirtyBit.getMask()) != 0);
    }

    /**
     * Test whether the dirty bits are empty
     */
    private boolean isDirtyEmpty() {
        return dirtyBits == 0;
    }

    /**
     * Clear all dirty bits
     */
    private void clearDirty() {
        dirtyBits = 0;
    }

    private enum DirtyBits {
        FILL_DIRTY,
        ROOT_DIRTY,
        CAMERA_DIRTY;

        private int mask;

        private DirtyBits() {
            mask = 1 << ordinal();
        }

        public final int getMask() { return mask; }
    }

    //INNER CLASSES

    /*******************************************************************************
     *                                                                             *
     * Scene Pulse Listener                                                        *
     *                                                                             *
     ******************************************************************************/

    class ScenePulseListener implements TKPulseListener {

        private boolean firstPulse = true;

        /**
         * PG synchronizer. Called once per frame from the pulse listener.
         * This function calls the synchronizePGNode method on each node in
         * the dirty list.
         */
        private void synchronizeSceneNodes() {
            Toolkit.getToolkit().checkFxUserThread();

            Scene.inSynchronizer = true;

            // if dirtyNodes is null then that means this Scene has not yet been
            // synchronized, and so we will simply synchronize every node in the
            // scene and then create the dirty nodes array list
            if (Scene.this.dirtyNodes == null) {
                // must do this recursively
                final int size = syncAll(getRoot());
                // Default capacity is hard-coded to minimum capacity
                // This heuristic can be changed over time if we like
                Scene.this.dirtyNodesA = new LinkedHashSet(MIN_DIRTY_CAPACITY);
                Scene.this.dirtyNodesB = new LinkedHashSet(MIN_DIRTY_CAPACITY);
                Scene.this.dirtyNodes = Scene.this.dirtyNodesA;
            } else {
                // This is not the first time this scene has been synchronized,
                // so we will only synchronize those nodes that need it
                LinkedHashSet currDirtyNodes = Scene.this.dirtyNodes;
                // Swap the double buffer
                if (Scene.this.dirtyNodes == Scene.this.dirtyNodesA) {
                    Scene.this.dirtyNodes = Scene.this.dirtyNodesB;
                } else {
                    Scene.this.dirtyNodes = Scene.this.dirtyNodesA;
                }


                final Iterator<Node> it = currDirtyNodes.iterator();

                while(it.hasNext()) {
                    Node node = it.next();
                    if (node.getScene() == Scene.this) {
                        node.impl_syncPGNode();
                    }
                }

                currDirtyNodes.clear();
            }

            Scene.inSynchronizer = false;
        }

        /**
         * Recursive function for synchronizing every node in the scenegraph.
         * The return value is the number of nodes in the graph.
         */
        private int syncAll(Node node) {
            node.impl_syncPGNode();
            int size = 1;
            if (node instanceof Parent) {
                Parent p = (Parent) node;
                final int childrenCount = p.getChildren().size();

                for (int i = 0; i < childrenCount; i++) {
                    Node n = p.getChildren().get(i);
                    if (n != null) {
                        size += syncAll(n);
                    }
                }
            }
            if (node.getClip() != null) {
                size += syncAll(node.getClip());
            }

            return size;
        }

        private void synchronizeSceneProperties() {
            inSynchronizer = true;
            if (isDirty(DirtyBits.ROOT_DIRTY)) {
                impl_peer.setRoot(getRoot().impl_getPGNode());
            }

            if (isDirty(DirtyBits.FILL_DIRTY)) {
                Toolkit tk = Toolkit.getToolkit();
                impl_peer.setFillPaint(getFill() == null ? null : tk.getPaint(getFill()));
            }

            // new camera was set on the scene
            if (isDirty(DirtyBits.CAMERA_DIRTY)) {
                if (getCamera() != null) {
                    getCamera().impl_update();
                    impl_peer.setCamera(getCamera().getPlatformCamera());
                } else {
                    impl_peer.setCamera(null);
                }
            }

            clearDirty();
            inSynchronizer = false;
        }

        /**
         * The focus is considered dirty if something happened to
         * the scene graph that may require the focus to be moved.
         * This must handle cases where (a) the focus owner may have
         * become ineligible to have the focus, and (b) where the focus
         * owner is null and a node may have become traversable and eligible.
         */
        private void focusCleanup() {
            if (Scene.this.isFocusDirty()) {
                final Node oldOwner = Scene.this.impl_getFocusOwner();
                if (oldOwner == null) {
                    Scene.this.focusInitial();
                } else if (oldOwner.getScene() != Scene.this) {
                    Scene.this.requestFocus(null);
                    Scene.this.focusInitial();
                } else if (!oldOwner.isCanReceiveFocus()) {
                    Scene.this.requestFocus(null);
                    Scene.this.focusIneligible(oldOwner);
                }
                Scene.this.setFocusDirty(false);
            }
        }

        @Override
        public void pulse() {
            if (Scene.this.tracker != null) {
                Scene.this.tracker.pulse();
            }
            if (firstPulse) {
                PerformanceTracker.logEvent("Scene - first repaint");
            }

            Scene.this.doCSSPass();
            Scene.this.doLayoutPass();

            boolean dirty = dirtyNodes == null || !dirtyNodes.isEmpty() || !isDirtyEmpty();
            if (dirty) {
                // synchronize scene properties
                synchronizeSceneProperties();
                // Run the synchronizer
                synchronizeSceneNodes();
                Scene.this.mouseHandler.pulse();
                // Tell the sceen peer that it needs to repaint
                impl_peer.markDirty();
            }

            // required for image cursor created from animated image
            Scene.this.mouseHandler.updateCursorFrame();

            focusCleanup();

            if (firstPulse) {
                if (PerformanceTracker.isLoggingEnabled()) {
                    PerformanceTracker.logEvent("Scene - first repaint - layout complete");
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override public Object run() {
                            if (System.getProperty("sun.perflog.fx.firstpaintflush") != null) {
                                PerformanceTracker.outputLog();
                            }
                            return null;
                        }
                    });
                }
                firstPulse = false;
            }

            if (impl_testPulseListener != null) {
                impl_testPulseListener.run();
            }
        }
    }

    /*******************************************************************************
     *                                                                             *
     * Scene Peer Listener                                                         *
     *                                                                             *
     ******************************************************************************/

    class ScenePeerListener implements TKSceneListener {
        @Override
        public void changedLocation(float x, float y) {
            if (x != Scene.this.getX()) Scene.this.setX(x);
            if (y != Scene.this.getY()) Scene.this.setY(y);
        }

        @Override
        public void changedSize(float w, float h) {
            if (w != Scene.this.getWidth()) Scene.this.setWidth(w);
            if (h != Scene.this.getHeight()) Scene.this.setHeight(h);
        }

        @Override
        public void mouseEvent(Object event) {
            Scene.this.impl_processMouseEvent(Toolkit.getToolkit().convertMouseEventToFX(event));
        }

        @Override
        public void keyEvent(Object event) {
            Scene.this.impl_processKeyEvent(Toolkit.getToolkit().convertKeyEventToFX(event));
        }

        @Override
        public void inputMethodEvent(Object event) {
            Scene.this.impl_processInputMethodEvent(Toolkit.getToolkit().convertInputMethodEventToFX(event));
        }

        @Override
        public void scrollEvent(
                double scrollX, double scrollY, 
                double xMultiplier, double yMultiplier,
                int scrollTextX, int scrollTextY,
                int defaultTextX, int defaultTextY,
                double x, double y, double screenX, double screenY,
                boolean _shiftDown, boolean _controlDown, 
                boolean _altDown, boolean _metaDown) {

            ScrollEvent.HorizontalTextScrollUnits xUnits = scrollTextX > 0 ?
                    ScrollEvent.HorizontalTextScrollUnits.CHARACTERS :
                    ScrollEvent.HorizontalTextScrollUnits.NONE;
            
            double xText = scrollTextX < 0 ? 0 : scrollTextX * scrollX;

            ScrollEvent.VerticalTextScrollUnits yUnits = scrollTextY > 0 ?
                    ScrollEvent.VerticalTextScrollUnits.LINES :
                    (scrollTextY < 0 ? 
                        ScrollEvent.VerticalTextScrollUnits.PAGES :
                        ScrollEvent.VerticalTextScrollUnits.NONE);
                    
            double yText = scrollTextY < 0 ? scrollY : scrollTextY * scrollY;

            xMultiplier = defaultTextX > 0 && scrollTextX >= 0
                    ? Math.round(xMultiplier * scrollTextX / defaultTextX)
                    : xMultiplier;

            yMultiplier = defaultTextY > 0 && scrollTextY >= 0
                    ? Math.round(yMultiplier * scrollTextY / defaultTextY)
                    : yMultiplier;
            
            Scene.this.processScrollEvent(ScrollEvent.impl_scrollEvent(
                    scrollX * xMultiplier, scrollY * yMultiplier,
                    xUnits, xText, yUnits, yText, 
                    x, y, screenX, screenY, 
                    _shiftDown, _controlDown, _altDown, _metaDown));
        }

        @Override
        public void menuEvent(double x, double y, double xAbs, double yAbs,
                boolean isKeyboardTrigger) {
            Scene.this.processMenuEvent(x, y, xAbs,yAbs, isKeyboardTrigger);
        }
        
    }

    private class ScenePeerPaintListener implements TKScenePaintListener {
        @Override
        public void frameRendered() {
            // must use tracker with synchronization since this method is called on render thread
            synchronized (trackerMonitor) {
                if (Scene.this.tracker != null) {
                    Scene.this.tracker.frameRendered();
                }
            }
        }
    }

    /*******************************************************************************
     *                                                                             *
     * Drag and Drop                                                               *
     *                                                                             *
     ******************************************************************************/

    class DropTargetListener implements TKDropTargetListener {
        /*
         * This function is called when an drag operation enters a valid drop target.
         * This may be from either an internal or external dnd operation.
         */
        @Override
        public TransferMode dragEnter(Object e) {
            if (Scene.this.dndGesture == null) {
                Scene.this.dndGesture = new DnDGesture();
            }
            return Scene.this.dndGesture.processTargetEnterOver(
                    Toolkit.getToolkit().convertDropTargetEventToFX(
                        e, Scene.this.dndGesture.dragboard));
        }

        @Override
        public TransferMode dragOver(Object e) {
            if (Scene.this.dndGesture == null) {
                System.out.println("GOT A dragOver when dndGesture is null!");
                return null;
            } else {
                return Scene.this.dndGesture.processTargetEnterOver(
                        Toolkit.getToolkit().convertDropTargetEventToFX(
                            e, Scene.this.dndGesture.dragboard));
            }
        }

        @Override
        public void dropActionChanged(Object e) {
            if (Scene.this.dndGesture == null) {
                System.out.println("GOT A dropActionChanged when dndGesture is null!");
            } else {
                Scene.this.dndGesture.processTargetActionChanged(
                        Toolkit.getToolkit().convertDropTargetEventToFX(
                            e, Scene.this.dndGesture.dragboard));
            }
        }


        @Override
        public void dragExit(Object e) {
            if (Scene.this.dndGesture == null) {
                System.out.println("GOT A dragExit when dndGesture is null!");
            } else {
                Scene.this.dndGesture.processTargetExit(
                        Toolkit.getToolkit().convertDropTargetEventToFX(
                            e, Scene.this.dndGesture.dragboard));
            }
        }


        @Override
        public TransferMode drop(Object e) {
            if (Scene.this.dndGesture == null) {
                System.out.println("GOT A drop when dndGesture is null!");
                return null;
            } else {
                TransferMode tm = Scene.this.dndGesture.processTargetDrop(
                        Toolkit.getToolkit().convertDropTargetEventToFX(
                            e, Scene.this.dndGesture.dragboard));

                if (Scene.this.dndGesture.source == null) {
                    Scene.this.dndGesture = null;
                }

                return tm;

            }
        }
    }

    class DragGestureListener implements TKDragGestureListener {
       @Override
       public void dragGestureRecognized(Object e) {

           Scene.this.dndGesture = new DnDGesture();
           final DragEvent de = Toolkit.getToolkit().convertDragRecognizedEventToFX(e, null);
           final Node pickedNode = Scene.this.mouseHandler.pickNode(de.getX(), de.getY());
           Scene.this.dndGesture.dragboard = de.impl_getPlatformDragboard();

            if (Scene.this.dndGesture.processRecognized(pickedNode, de)) {
                return;
            }

            Scene.this.dndGesture = null;
        }
    }

    /**
     * A Drag and Drop gesture has a lifespan that lasts from mouse
     * PRESSED event to mouse RELEASED event.
     */
    class DnDGesture {
        private final double hysteresisSizeX =
                Toolkit.getToolkit().getMultiClickMaxX();
        private final double hysteresisSizeY =
                Toolkit.getToolkit().getMultiClickMaxY();

        private EventTarget source = null;
        private Set<TransferMode> sourceTransferModes = null;
        private TransferMode acceptedTransferMode = null;
        private Dragboard dragboard = null;
        private EventTarget potentialTarget = null;
        private EventTarget target = null;
        private DragDetectedState dragDetected = DragDetectedState.NOT_YET;
        private double pressedX;
        private double pressedY;
        private List<EventTarget> currentTargets = new ArrayList<EventTarget>();
        private List<EventTarget> newTargets = new ArrayList<EventTarget>();
        private EventTarget fullPDRSource = null;

        /**
         * Returns the given target or scene if it's null
         */
        private EventTarget targetOrScene(EventTarget target) {
            return target != null ? target : Scene.this;
        }

        /**
         * Fires event on a given target or on scene if the node is null
         */
        private void fireEvent(EventTarget target, Event e) {
            Event.fireEvent(targetOrScene(target), e);
        }

        /**
         * Called when DRAG_DETECTED event is going to be processed by
         * application
         */
        private void processingDragDetected() {
            dragDetected = DragDetectedState.PROCESSING;
        }

        /**
         * Called after DRAG_DETECTED event has been processed by application
         */
        private void dragDetectedProcessed() {
            dragDetected = DragDetectedState.DONE;
            final boolean hasContent = dragboard != null
                    && dragboard.impl_contentPut();
            if (hasContent) {
                /* start DnD */
                Toolkit.getToolkit().startDrag(Scene.this.impl_peer,
                                                sourceTransferModes,
                                                new DragSourceListener(),
                                                dragboard);
            } else if (fullPDRSource != null) {
                /* start PDR */
                Scene.this.mouseHandler.enterFullPDR(fullPDRSource);
            }

            fullPDRSource = null;
        }

        /**
         * Sets the default dragDetect value
         */
        private void processDragDetection(MouseEvent mouseEvent,
                EventTarget target) {

            if (dragDetected != DragDetectedState.NOT_YET) {
                mouseEvent.setDragDetect(false);
                return;
            }

            if (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED) {
                pressedX = mouseEvent.getSceneX();
                pressedY = mouseEvent.getSceneY();

                mouseEvent.setDragDetect(false);

            } else if (mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED) {

                double deltaX = Math.abs(mouseEvent.getSceneX() - pressedX);
                double deltaY = Math.abs(mouseEvent.getSceneY() - pressedY);
                mouseEvent.setDragDetect(deltaX > hysteresisSizeX ||
                                         deltaY > hysteresisSizeY);

            }
        }

        /**
         * This function is useful for drag gesture recognition from
         * within this Scene (as opposed to in the TK implementation... by the platform)
         */
        private boolean process(MouseEvent mouseEvent, EventTarget target) {
            boolean continueProcessing = true;
            if (!PLATFORM_DRAG_GESTURE_INITIATION) {

                if (dragDetected != DragDetectedState.DONE &&
                        (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED ||
                        mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED) &&
                        mouseEvent.isDragDetect()) {

                    processingDragDetected();

                    final MouseEvent detectedEvent = MouseEvent.impl_copy(
                            mouseEvent.getSource(), target, mouseEvent,
                            MouseEvent.DRAG_DETECTED);

                    fireEvent(target, detectedEvent);

                    dragDetectedProcessed();
                }

                if (mouseEvent.getEventType() == MouseEvent.MOUSE_RELEASED) {
                    continueProcessing = false;
                }
            }
            return continueProcessing;
        }

        /*
         * Called when a drag source is recognized. This occurs at the very start of
         * the publicly visible drag and drop API, as it is responsible for calling
         * the Node.onDragSourceRecognized function.
         */
        private boolean processRecognized(Node n, DragEvent de) {
            //TODO: Should get Mouse Event, for now we have to make up one
            //      this code is not used right now anyway
            MouseEvent me = MouseEvent.impl_mouseEvent(de.getX(), de.getY(),
                    de.getSceneX(), de.getScreenY(), MouseButton.PRIMARY, 1,
                    false, false, false, false, false, true, false, false,
                    MouseEvent.DRAG_DETECTED);

            processingDragDetected();

            fireEvent(n, me);

            dragDetectedProcessed();

            final boolean hasContent = dragboard != null
                    && !dragboard.getContentTypes().isEmpty();
            if (hasContent) {
                Toolkit.getToolkit().startDrag(Scene.this.impl_peer,
                                               sourceTransferModes,
                                               new DragSourceListener(),
                                               dragboard);
                return true;
            }
            return false;
        }

        private void processDropEnd(DragEvent de) {
            if (source == null) {
                System.out.println("Scene.DnDGesture.processDropEnd() - UNEXPECTD - source is NULL");
                return;
            }

            de = DragEvent.impl_copy(de.getSource(), source, source, target, de,
                    DragEvent.DRAG_DONE);

            Event.fireEvent(source, de);

            handleExitEnter(de, null);

            // at this point the drag and drop operation is completely over, so we
            // can tell the toolkit that it can clean up if needs be.
            Toolkit.getToolkit().stopDrag(dragboard);
        }

        private TransferMode processTargetEnterOver(DragEvent de) {
            final Node pickedNode = Scene.this.mouseHandler.pickNode(de.getX(), de.getY());
            if (pickedNode == null || pickedNode.impl_isTreeVisible()) {

                if (dragboard == null) {
                    dragboard = createDragboard();
                }

                de = DragEvent.impl_copy(de.getSource(), pickedNode, source,
                        potentialTarget, dragboard, de);

                handleExitEnter(de, targetOrScene(pickedNode));

                de = DragEvent.impl_copy(de.getSource(), pickedNode, source,
                        potentialTarget, de, DragEvent.DRAG_OVER);

                fireEvent(pickedNode, de);

                Object acceptingObject = de.impl_getAcceptingObject();
                potentialTarget = acceptingObject instanceof EventTarget
                        ? (EventTarget) acceptingObject : null;
                acceptedTransferMode = de.getAcceptedTransferMode();
                return acceptedTransferMode;
            } else {
                processTargetExit(de);
            }

            acceptedTransferMode = null;
            return null;
        }

        private void processTargetActionChanged(DragEvent de) {
            // Do we want DRAG_TRANSFER_MODE_CHANGED event?
//            final Node pickedNode = Scene.this.mouseHandler.pickNode(de.getX(), de.getY());
//            if (pickedNode != null && pickedNode.impl_isTreeVisible()) {
//                de = DragEvent.impl_copy(de.getSource(), pickedNode, source,
//                        pickedNode, de, DragEvent.DRAG_TRANSFER_MODE_CHANGED);
//
//                if (dragboard == null) {
//                    dragboard = createDragboard();
//                }
//                dragboard = de.impl_getPlatformDragboard();
//
//                fireEvent(pickedNode, de);
//            }
        }

        private void processTargetExit(DragEvent de) {
            if (currentTargets.size() > 0) {
                potentialTarget = null;
                handleExitEnter(de, null);
            }
        }

        private TransferMode processTargetDrop(DragEvent de) {
            final Node pickedNode = Scene.this.mouseHandler.pickNode(de.getX(), de.getY());
            if (pickedNode == null || pickedNode.impl_isTreeVisible()) {

                de = DragEvent.impl_copy(de.getSource(), pickedNode, source,
                        potentialTarget, acceptedTransferMode, de,
                        DragEvent.DRAG_DROPPED);

                if (dragboard == null) {
                    dragboard = createDragboard();
                }

                handleExitEnter(de, targetOrScene(pickedNode));

                fireEvent(pickedNode, de);

                Object acceptingObject = de.impl_getAcceptingObject();
                potentialTarget = acceptingObject instanceof EventTarget
                        ? (EventTarget) acceptingObject : null;
                target = potentialTarget;

                TransferMode result = de.isDropCompleted() ?
                    de.getAcceptedTransferMode() : null;

                handleExitEnter(de, null);

                return result;
            } else {
                processTargetExit(de);
            }
            return null;
        }

        private void handleExitEnter(DragEvent e, EventTarget target) {
            EventTarget currentTarget =
                    currentTargets.size() > 0 ? currentTargets.get(0) : null;

            if (target != currentTarget) {

                newTargets.clear();
                if (target instanceof Node) {
                    Node newNode = (Node) target;
                    while(newNode != null) {
                        newTargets.add(newNode);
                        newNode = newNode.getParent();
                    }
                }
                if (target != null) {
                    newTargets.add(Scene.this);
                }

                int i = currentTargets.size() - 1;
                int j = newTargets.size() - 1;

                while (i >= 0 && j >= 0 && currentTargets.get(i) == newTargets.get(j)) {
                    i--;
                    j--;
                }

                for (; i >= 0; i--) {
                    EventTarget t = currentTargets.get(i);
                    if (potentialTarget == t) {
                        potentialTarget = null;
                    }
                    e = DragEvent.impl_copy(e.getSource(), t, source,
                            potentialTarget, e, DragEvent.DRAG_EXITED_TARGET);
                    Event.fireEvent(t, e);
                }

                potentialTarget = null;
                for (; j >= 0; j--) {
                    EventTarget t = newTargets.get(j);
                    e = DragEvent.impl_copy(e.getSource(), t, source,
                            potentialTarget, e, DragEvent.DRAG_ENTERED_TARGET);
                    Object acceptingObject = e.impl_getAcceptingObject();
                    if (acceptingObject instanceof EventTarget) {
                        potentialTarget = (EventTarget) acceptingObject;
                    }
                    Event.fireEvent(t, e);
                }

                currentTargets.clear();
                currentTargets.addAll(newTargets);
            }
        }

//        function getIntendedTransferMode(e:MouseEvent):TransferMode {
//            return if (e.altDown) TransferMode.COPY else TransferMode.MOVE;
//        }

        /*
         * Function that hooks into the key processing code in Scene to handle the
         * situation where a drag and drop event is taking place and the user presses
         * the escape key to cancel the drag and drop operation.
         */
        private boolean processKey(KeyEvent e) {
            //note: this seems not to be called, the DnD cancelation is provided by platform
            if ((e.getEventType() == KeyEvent.KEY_PRESSED) && (e.getCode() == KeyCode.ESCAPE)) {

                // cancel drag and drop
                DragEvent de = DragEvent.impl_create(DragEvent.DRAG_DONE,
                        source, source, source, null, 0, 0, 0, 0, null,
                        dragboard, null);

                if (source != null) {
                    Event.fireEvent(source, de);
                }

                handleExitEnter(de, null);

                return false;
            }
            return true;
        }

        /*
         * This starts the drag gesture running, creating the dragboard used for
         * the remainder of this drag and drop operation.
         */
        private Dragboard startDrag(EventTarget source, Set<TransferMode> t) {
            if (dragDetected != DragDetectedState.PROCESSING) {
                throw new IllegalStateException("Cannot start drag and drop "
                        + "outside of DRAG_DETECTED event handler");
            }

            if (t.isEmpty()) {
                dragboard = null;
            } else if (dragboard == null) {
                dragboard = createDragboard();
            }
            this.source = source;
            potentialTarget = source;
            sourceTransferModes = t;
            return dragboard;
        }

        /*
         * This starts the full PDR gesture.
         */
        private void startFullPDR(EventTarget source) {
            fullPDRSource = source;
        }

        private Dragboard createDragboard() {
            return Toolkit.getToolkit().createDragboard();
        }
    }

    /**
     * State of a drag gesture with regards to DRAG_DETECTED event.
     */
    private enum DragDetectedState {
        NOT_YET,
        PROCESSING,
        DONE
    }

    class DragSourceListener implements TKDragSourceListener {

        @Override
        public void dropActionChanged(Object e) {
            //System.out.println("Scene.DragSourceListener.dropActionChanged() - no action taken");
        }

        @Override
        public void dragDropEnd(Object e) {
            if (Scene.this.dndGesture != null) {
                Scene.this.dndGesture.processDropEnd(Toolkit.getToolkit().convertDragSourceEventToFX(e, Scene.this.dndGesture.dragboard));
                Scene.this.dndGesture = null;
            }/* else {
                System.out.println("Scene.DragSourceListener.dragDropEnd() - UNEXPECTD - dndGesture is NULL");
            }*/
        }
    }

    /*******************************************************************************
     *                                                                             *
     * Mouse Event Handling                                                        *
     *                                                                             *
     ******************************************************************************/

    class ClickCounter {
        Toolkit toolkit = Toolkit.getToolkit();
        private int count;
        private boolean out;
        private boolean still;
        private Timeline timeout;
        private double pressedX, pressedY;
        private EventTarget clickTarget;

        private void inc() { count++; }
        private int get() { return count; }
        private boolean isStill() { return still; }

        private void clear() {
            count = 0;
            clickTarget = null;
            stopTimeout();
        }

        private void out() {
            out = true;
            stopTimeout();
        }

        private void applyOut() {
            if (out) clear();
            out = false;
        }

        private void moved(double x, double y) {
            if (Math.abs(x - pressedX) > toolkit.getMultiClickMaxX() ||
                    Math.abs(y - pressedY) > toolkit.getMultiClickMaxY()) {
                out();
                still = false;
            }
        }

        private void start(double x, double y) {
            pressedX = x;
            pressedY = y;
            out = false;

            if (timeout != null) {
                timeout.stop();
            }
            timeout = new Timeline();
            timeout.getKeyFrames().add(
                    new KeyFrame(new Duration(toolkit.getMultiClickTime()),
                    new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    out = true;
                    timeout = null;
                }

            }));
            timeout.play();
            still = true;
        }

        private void setTarget(EventTarget target) {
            this.clickTarget = target;
        }

        private void checkTarget(EventTarget target) {
            if (clickTarget == null) {
                return;
            }

            boolean isThere = false;
            if (target instanceof Node) {
                Node n = (Node) target;
                while(n != null) {
                    if (n == clickTarget) {
                        isThere = true;
                        break;
                    }
                    n = n.getParent();
                }
            }
            if (clickTarget == Scene.this) {
                isThere = true;
            }
            if (!isThere) {
                out();
            }
        }

        private void stopTimeout() {
            if (timeout != null) {
                timeout.stop();
                timeout = null;
            }
        }
    }

    class ClickGenerator {
        private ClickCounter lastPress = null;

        private Map<MouseButton, ClickCounter> counters =
                new EnumMap<MouseButton, ClickCounter>(MouseButton.class);

        public ClickGenerator() {
            for (MouseButton mb : MouseButton.values()) {
                if (mb != MouseButton.NONE) {
                    counters.put(mb, new ClickCounter());
                }
            }
        }

        private void preProcess(MouseEvent e, EventTarget target) {
            for (ClickCounter cc : counters.values()) {
                cc.moved(e.getSceneX(), e.getSceneY());
            }

            ClickCounter cc = counters.get(e.getButton());
            boolean still = lastPress != null ? lastPress.isStill() : false;

            if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {

                if (! e.isPrimaryButtonDown()) { counters.get(MouseButton.PRIMARY).clear(); }
                if (! e.isSecondaryButtonDown()) { counters.get(MouseButton.SECONDARY).clear(); }
                if (! e.isMiddleButtonDown()) { counters.get(MouseButton.MIDDLE).clear(); }

                cc.checkTarget(target);
                cc.applyOut();
                cc.inc();
                cc.start(e.getSceneX(), e.getSceneY());
                lastPress = cc;
            }

            e.impl_setClickParams(
                    cc != null && e.getEventType() != MouseEvent.MOUSE_MOVED ? cc.get() : 0,
                    still);
        }

        private void postProcess(MouseEvent e, EventTarget target) {
            if (e.getEventType() == MouseEvent.MOUSE_RELEASED) {
                ClickCounter cc = counters.get(e.getButton());
                EventTarget clickedTarget = null;
                if (target instanceof Node) {
                    Node n = (Node) target;
                    while(n != null) {
                        if (n.contains(n.sceneToLocal(e.getSceneX(), e.getSceneY()))) {
                            clickedTarget = n;
                            break;
                        }
                        n = n.getParent();
                    }
                }
                if (clickedTarget == null &&
                        e.getSceneX() >= 0 && e.getSceneY() >= 0 &&
                        e.getSceneX() <= Scene.this.getWidth() &&
                        e.getSceneY() <= Scene.this.getHeight())  {
                    clickedTarget = Scene.this;
                }

                if (clickedTarget != null) {
                    MouseEvent click = MouseEvent.impl_copy(null, clickedTarget, e,
                            MouseEvent.MOUSE_CLICKED);
                    click.impl_setClickParams(cc.get(), lastPress.isStill());
                    cc.setTarget(clickedTarget);
                    Event.fireEvent(clickedTarget, click);
                }
            }
        }
    }

    class MouseHandler {
        private EventTarget pdrEventTarget = null; // pdr - press-drag-release
        private boolean pdrInProgress = false;
        private boolean fullPDREntered = false;

        private EventTarget currentEventTarget = null;
        private MouseEvent lastEvent;
        private boolean hover = false;

        private boolean primaryButtonDown = false;
        private boolean secondaryButtonDown = false;
        private boolean middleButtonDown = false;

        private EventTarget fullPDRSource = null;

        /* lists needed for enter/exit events generation */
        private final List<EventTarget> pdrEventTargets = new ArrayList<EventTarget>();
        private final List<EventTarget> currentEventTargets = new ArrayList<EventTarget>();
        private final List<EventTarget> newEventTargets = new ArrayList<EventTarget>();

        private final List<EventTarget> fullPDRCurrentEventTargets = new ArrayList<EventTarget>();
        private final List<EventTarget> fullPDRNewEventTargets = new ArrayList<EventTarget>();
        private EventTarget fullPDRCurrentTarget = null;

        private Cursor currCursor;
        private CursorFrame currCursorFrame;

        private void pulse() {
            if (hover && lastEvent != null) {
                process(lastEvent, true);
            }
        }

        private void process(MouseEvent e) {
            process(e, false);
        }

        private void clearPDREventTargets() {
            pdrInProgress = false;
            currentEventTarget = currentEventTargets.size() > 0
                    ? currentEventTargets.get(0) : null;
            pdrEventTarget = null;
        }

        public void enterFullPDR(EventTarget gestureSource) {
            fullPDREntered = true;
            fullPDRSource = gestureSource;
            fullPDRCurrentTarget = null;
            fullPDRCurrentEventTargets.clear();
        }

        public void exitFullPDR(MouseEvent e) {
            if (!fullPDREntered) {
                return;
            }
            fullPDREntered = false;
            for (int i = fullPDRCurrentEventTargets.size() - 1; i >= 0; i--) {
                EventTarget entered = fullPDRCurrentEventTargets.get(i);
                Event.fireEvent(entered, MouseDragEvent.impl_copy(
                        entered, entered, fullPDRSource, e,
                        MouseDragEvent.MOUSE_DRAG_EXITED_TARGET));
            }
            fullPDRSource = null;
            fullPDRCurrentEventTargets.clear();
            fullPDRCurrentTarget = null;
        }
        
        private void handleEnterExit(MouseEvent e, EventTarget pickedTarget) {
            if (pickedTarget != currentEventTarget ||
                    e.getEventType() == MouseEvent.MOUSE_EXITED) {

                newEventTargets.clear();

                if (e.getEventType() != MouseEvent.MOUSE_EXITED) {
                    if (pickedTarget instanceof Node) {
                        Node newNode = (Node) pickedTarget;
                        while(newNode != null) {
                            newEventTargets.add(newNode);
                            newNode = newNode.getParent();
                        }
                    }
                    newEventTargets.add(Scene.this);
                }

                int newTargetsSize = newEventTargets.size();
                int i = currentEventTargets.size() - 1;
                int j = newTargetsSize - 1;
                int k = pdrEventTargets.size() - 1;

                while (i >= 0 && j >= 0 && currentEventTargets.get(i) == newEventTargets.get(j)) {
                    i--;
                    j--;
                    k--;
                }

                final int memk = k;
                for (; i >= 0; i--, k--) {
                    final EventTarget exitedEventTarget = currentEventTargets.get(i);
                    if (pdrInProgress &&
                            (k < 0 || exitedEventTarget != pdrEventTargets.get(k))) {
                         break;
                    }
                    Event.fireEvent(exitedEventTarget, MouseEvent.impl_copy(
                            exitedEventTarget, exitedEventTarget, e,
                            MouseEvent.MOUSE_EXITED_TARGET));
                }

                k = memk;
                for (; j >= 0; j--, k--) {
                    final EventTarget enteredEventTarget = newEventTargets.get(j);
                    if (pdrInProgress &&
                            (k < 0 || enteredEventTarget != pdrEventTargets.get(k))) {
                        break;
                    }
                    Event.fireEvent(enteredEventTarget, MouseEvent.impl_copy(
                            enteredEventTarget, enteredEventTarget, e,
                            MouseEvent.MOUSE_ENTERED_TARGET));
                }

                currentEventTarget = pickedTarget;
                currentEventTargets.clear();
                for (j++; j < newTargetsSize; j++) {
                    currentEventTargets.add(newEventTargets.get(j));
                }
            }
        }

        private void process(MouseEvent e, boolean onPulse) {
            Toolkit.getToolkit().checkFxUserThread();
            Scene.inMousePick = true;

            boolean gestureStarted = false;
            if (!onPulse) {
                if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
                    if (!(primaryButtonDown || secondaryButtonDown || middleButtonDown)) {
                        //old gesture ended and new one started
                        gestureStarted = true;
                        if (!PLATFORM_DRAG_GESTURE_INITIATION) {
                            Scene.this.dndGesture = new DnDGesture();
                        }
                        clearPDREventTargets();
                    }
                } else if (e.getEventType() == MouseEvent.MOUSE_MOVED) {
                    // gesture ended
                    clearPDREventTargets();
                } else if (e.getEventType() == MouseEvent.MOUSE_ENTERED) {
                    hover = true;
                } else if (e.getEventType() == MouseEvent.MOUSE_EXITED) {
                    hover = false;
                }

                primaryButtonDown = e.isPrimaryButtonDown();
                secondaryButtonDown = e.isSecondaryButtonDown();
                middleButtonDown = e.isMiddleButtonDown();
            }

            //maps parent to most visible child
            EventTarget pickedTarget = null;

            if (e.getEventType() != MouseEvent.MOUSE_EXITED) {
                if (getCamera() instanceof PerspectiveCamera) {
                    final PickRay pickRay = new PickRay();
                    Scene.this.impl_peer.computePickRay((float)e.getX(), (float)e.getY(), pickRay);
    //                System.out.println("** 3D: origin = " + pickRay.getOriginNoClone()
    //                        + ", direction = " + pickRay.getDirectionNoClone());
                    pickedTarget = pickNode(pickRay);
                }
                else {
                    pickedTarget = pickNode(e.getX(), e.getY());
                }
            }

            if (pickedTarget == null) {
                pickedTarget = Scene.this;
            }

            EventTarget target;
            if (pdrInProgress) {
                // TODO I believe this is bogus. We should still deliver mouse
                // enter / exit / move events to nodes that are not part of the
                // press-drag-release event, but only pdr nodes get the mouse
                // dragged events, and other nodes DO NOT (?) get pressed events...
                // The use case here is that I press a button and a popup is visible
                // and while the button is still depressed I "drag" over the item
                // in the popup I want and release. So in this case I need to get
                // some events on the items in the popup, just not the drag events.
                target = pdrEventTarget;
            } else {
                target = pickedTarget;
            }

            if (gestureStarted) {
                pdrEventTarget = target;

                pdrEventTargets.clear();
                if (pdrEventTarget instanceof Node) {
                    Node n = (Node) pdrEventTarget;
                    while(n != null) {
                        pdrEventTargets.add(n);
                        n = n.getParent();
                    }
                }

                pdrEventTargets.add(Scene.this);
            }

            if (!onPulse) {
                clickGenerator.preProcess(e, target);
            }

            // enter/exit handling
            handleEnterExit(e, pickedTarget);

            Cursor cursor = null;

            //deliver event to the target node
            if (target instanceof Node) {

                if (cursor == null) {
                    cursor = ((Node) target).getCursor();
                    Parent p = ((Node) target).getParent();
                    while (cursor == null && p != null) {
                        cursor = p.getCursor();
                        p = p.getParent();
                    }
                }
            }

            if (Scene.this.dndGesture != null) {
                Scene.this.dndGesture.processDragDetection(e, target);
            }

            if (fullPDREntered && e.getEventType() == MouseEvent.MOUSE_RELEASED) {
                processFullPDR(e, onPulse);
            }

            if (target != null) {
                if (e.getEventType() != MouseEvent.MOUSE_ENTERED
                        && e.getEventType() != MouseEvent.MOUSE_EXITED
                        && !onPulse) {
                    Event.fireEvent(target, e);
                }
            }

            if (fullPDREntered && e.getEventType() != MouseEvent.MOUSE_RELEASED) {
                processFullPDR(e, onPulse);
            }

            if (!onPulse) {
                clickGenerator.postProcess(e, target);
            }

            // handle drag and drop

            if (!PLATFORM_DRAG_GESTURE_INITIATION && !onPulse) {
                if (Scene.this.dndGesture != null) {
                    if (!Scene.this.dndGesture.process(e, target)) {
                        dndGesture = null;
                    }
                }
            }


            if (cursor == null && hover) {
                cursor = Scene.this.getCursor();
            }

            updateCursor(cursor);
            updateCursorFrame();

            if (gestureStarted) {
                pdrInProgress = true;
            }

            if (pdrInProgress &&
                    !(primaryButtonDown || secondaryButtonDown || middleButtonDown)) {
                clearPDREventTargets();
                exitFullPDR(e);
                handleEnterExit(e, pickedTarget);
            }

            lastEvent = e;
            Scene.inMousePick = false;
        }

        private void processFullPDR(MouseEvent e, boolean onPulse) {

            // picking
            EventTarget target = null;

            if (e.getEventType() != MouseEvent.MOUSE_EXITED) {
                if (getCamera() instanceof PerspectiveCamera) {
                    final PickRay pickRay = new PickRay();
                    Scene.this.impl_peer.computePickRay(
                            (float)e.getX(), (float)e.getY(), pickRay);
                    target = pickNode(pickRay);
                } else {
                    target = pickNode(e.getX(), e.getY());
                }
            }

            if (target == null &&
                    e.getSceneX() >= 0 && e.getSceneY() >= 0 &&
                    e.getSceneX() <= Scene.this.getWidth() &&
                    e.getSceneY() <= Scene.this.getHeight())  {
                target = Scene.this;
            }

            // enter/exit handling
            if (target != fullPDRCurrentTarget) {

                fullPDRNewEventTargets.clear();

                if (target != null) {
                    if (target instanceof Node) {
                        Node newNode = (Node) target;
                        while(newNode != null) {
                            fullPDRNewEventTargets.add(newNode);
                            newNode = newNode.getParent();
                        }
                    }
                    fullPDRNewEventTargets.add(Scene.this);
                }

                int newTargetsSize = fullPDRNewEventTargets.size();
                int i = fullPDRCurrentEventTargets.size() - 1;
                int j = newTargetsSize - 1;

                while (i >= 0 && j >= 0 &&
                        fullPDRCurrentEventTargets.get(i) == fullPDRNewEventTargets.get(j)) {
                    i--;
                    j--;
                }

                for (; i >= 0; i--) {
                    final EventTarget exitedEventTarget = fullPDRCurrentEventTargets.get(i);
                    Event.fireEvent(exitedEventTarget, MouseDragEvent.impl_copy(
                            exitedEventTarget, exitedEventTarget, fullPDRSource, e,
                            MouseDragEvent.MOUSE_DRAG_EXITED_TARGET));
                }

                for (; j >= 0; j--) {
                    final EventTarget enteredEventTarget = fullPDRNewEventTargets.get(j);
                    Event.fireEvent(enteredEventTarget, MouseDragEvent.impl_copy(
                            enteredEventTarget, enteredEventTarget, fullPDRSource, e,
                            MouseDragEvent.MOUSE_DRAG_ENTERED_TARGET));
                }

                fullPDRCurrentTarget = target;
                fullPDRCurrentEventTargets.clear();
                fullPDRCurrentEventTargets.addAll(fullPDRNewEventTargets);
            }
            // done enter/exit handling

            // event delivery
            if (target != null && !onPulse) {
                if (e.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                    Event.fireEvent(target, MouseDragEvent.impl_copy(
                            target, target, fullPDRSource, e,
                            MouseDragEvent.MOUSE_DRAG_OVER));
                }
                if (e.getEventType() == MouseEvent.MOUSE_RELEASED) {
                    Event.fireEvent(target, MouseDragEvent.impl_copy(
                            target, target, fullPDRSource, e,
                            MouseDragEvent.MOUSE_DRAG_RELEASED));
                }
            }
        }

        private void updateCursor(Cursor newCursor) {
            if (currCursor != newCursor) {
                if (currCursor != null) {
                    currCursor.deactivate();
                }

                if (newCursor != null) {
                    newCursor.activate();
                }

                currCursor = newCursor;
            }
        }

        public void updateCursorFrame() {
            final CursorFrame newCursorFrame =
                    (currCursor != null)
                           ? currCursor.impl_getCurrentFrame()
                           : Cursor.DEFAULT.impl_getCurrentFrame();
            if (currCursorFrame != newCursorFrame) {
                if (Scene.this.impl_peer != null) {
                    Scene.this.impl_peer.setCursor(newCursorFrame);
                }

                currCursorFrame = newCursorFrame;
            }
        }

        private Node pickNode(double x, double y) {
            return Scene.this.getRoot().impl_pickNode(x, y);
        }

        private Node pickNode(PickRay pickRay) {
            return Scene.this.getRoot().impl_pickNode(pickRay);
        }
    }

    /*******************************************************************************
     *                                                                             *
     * Key Event Handling                                                          *
     *                                                                             *
     ******************************************************************************/

    class KeyHandler implements InvalidationListener {
        private Node focusOwner = null;
        private Node getFocusOwner() { return focusOwner; }

        private void setFocusOwner(Node value) {
            Node oldFocusOwner = focusOwner;
            if (oldFocusOwner != null) {
                oldFocusOwner.setFocused(false);
            }
            focusOwner = value;

            Scene.this.setImpl_focusOwner(focusOwner);// = Scene{ impl_focusOwner = bind keyHandler.focusOwner };

            if (focusOwner != null) {
                focusOwner.setFocused(windowFocused);
                if (focusOwner != oldFocusOwner) {
                    focusOwner.getScene().impl_enableInputMethodEvents(
                        focusOwner.getInputMethodRequests() != null &&
                        focusOwner.getOnInputMethodTextChanged() != null);
                }
            }

            PlatformLogger logger = Logging.getFocusLogger();
            if (logger.isLoggable(PlatformLogger.FINE)) {
                logger.fine("Changed focus from "
                            + oldFocusOwner + " to "
                            + focusOwner);
            }
        }

        private boolean windowFocused = true;
        protected boolean isWindowFocused() { return windowFocused; }
        protected void setWindowFocused(boolean value) {
            windowFocused = value;
            if (getFocusOwner() != null) {
                getFocusOwner().setFocused(windowFocused);
            }
        }

        private void windowForSceneChanged(Window oldWindow, Window window) {
            if (oldWindow != null) {
                oldWindow.focusedProperty().removeListener(sceneWindowFocusedListener);
            }

            if (window != null) {
                window.focusedProperty().addListener(sceneWindowFocusedListener);
                setWindowFocused(window.isFocused());
            } else {
                setWindowFocused(false);
            }
        }

        private final InvalidationListener sceneWindowFocusedListener = new InvalidationListener() {
            @Override public void invalidated(Observable valueModel) {
                setWindowFocused(((ReadOnlyBooleanProperty)valueModel).get());
            }
        };

        public KeyHandler() {
            windowForSceneChanged(Scene.this.getWindow(), Scene.this.getWindow()); // to init windowFocused properly
        }

        private void process(KeyEvent e) {
            final Node sceneFocusOwner = getFocusOwner();
            final EventTarget eventTarget =
                    (sceneFocusOwner != null) ? sceneFocusOwner
                                              : Scene.this;

            // send the key event to the current focus owner or to scene if
            // the focus owner is not set
            Event.fireEvent(eventTarget, e);
        }

        private void requestFocus(Node node) {
            if (getFocusOwner() == node || (node != null && !node.isCanReceiveFocus())) {
                return;
            }
            setFocusOwner(node);

            if (getFocusOwner() != null) {
                getFocusOwner().impl_requestFocusImpl(new Runnable() {
                    @Override
                    public void run() {
                        if (impl_peer != null) {
                            impl_peer.requestFocus();
                        }
                    }
                });
            }
        }

        // TODO: What is the point of extending a listener, if handle() is not overridden?
        @Override
        public void invalidated(Observable valueModel) {
            //nothing to do, implemented because of extending ChangeListener
        }
    }
    /***************************************************************************
     *                                                                         *
     *                         Event Dispatch                                  *
     *                                                                         *
     **************************************************************************/
    // PENDING_DOC_REVIEW
    /**
     * Specifies the event dispatcher for this scene. When replacing the value
     * with a new {@code EventDispatcher}, the new dispatcher should forward
     * events to the replaced dispatcher to keep the scene's default event
     * handling behavior.
     */
    private ObjectProperty<EventDispatcher> eventDispatcher;

    public final void setEventDispatcher(EventDispatcher value) {
        eventDispatcherProperty().set(value);
    }

    public final EventDispatcher getEventDispatcher() {
        return eventDispatcherProperty().get();
    }

    public final ObjectProperty<EventDispatcher>
            eventDispatcherProperty() {
        initializeInternalEventDispatcher();
        return eventDispatcher;
    }

    private SceneEventDispatcher internalEventDispatcher;

    // Delegates requests from platform input method to the focused
    // node's one, if any.
    class InputMethodRequestsDelegate implements InputMethodRequests {
        @Override
        public Point2D getTextLocation(int offset) {
            InputMethodRequests requests = getClientRequests();
            if (requests != null) {
                return requests.getTextLocation(offset);
            } else {
                return new Point2D(0, 0);
            }
        }

        @Override
        public int getLocationOffset(int x, int y) {
            InputMethodRequests requests = getClientRequests();
            if (requests != null) {
                return requests.getLocationOffset(x, y);
            } else {
                return 0;
            }
        }

        @Override
        public void cancelLatestCommittedText() {
            InputMethodRequests requests = getClientRequests();
            if (requests != null) {
                requests.cancelLatestCommittedText();
            }
        }

        @Override
        public String getSelectedText() {
            InputMethodRequests requests = getClientRequests();
            if (requests != null) {
                return requests.getSelectedText();
            }
            return null;
        }

        private InputMethodRequests getClientRequests() {
            Node focusOwner = impl_getFocusOwner();
            if (focusOwner != null) {
                return focusOwner.getInputMethodRequests();
            }
            return null;
        }
    }

    // PENDING_DOC_REVIEW
    /**
     * Registers an event handler to this scene. The handler is called when the
     * scene receives an {@code Event} of the specified type during the bubbling
     * phase of event delivery.
     *
     * @param <T> the specific event class of the handler
     * @param eventType the type of the events to receive by the handler
     * @param eventHandler the handler to register
     * @throws NullPointerException if the event type or handler is null
     */
    public final <T extends Event> void addEventHandler(
            final EventType<T> eventType,
            final EventHandler<? super T> eventHandler) {
        getInternalEventDispatcher().getEventHandlerManager()
                                    .addEventHandler(eventType, eventHandler);
    }

    // PENDING_DOC_REVIEW
    /**
     * Unregisters a previously registered event handler from this scene. One
     * handler might have been registered for different event types, so the
     * caller needs to specify the particular event type from which to
     * unregister the handler.
     *
     * @param <T> the specific event class of the handler
     * @param eventType the event type from which to unregister
     * @param eventHandler the handler to unregister
     * @throws NullPointerException if the event type or handler is null
     */
    public final <T extends Event> void removeEventHandler(
            final EventType<T> eventType,
            final EventHandler<? super T> eventHandler) {
        getInternalEventDispatcher().getEventHandlerManager()
                                    .removeEventHandler(eventType,
                                                        eventHandler);
    }

    // PENDING_DOC_REVIEW
    /**
     * Registers an event filter to this scene. The filter is called when the
     * scene receives an {@code Event} of the specified type during the
     * capturing phase of event delivery.
     *
     * @param <T> the specific event class of the filter
     * @param eventType the type of the events to receive by the filter
     * @param eventFilter the filter to register
     * @throws NullPointerException if the event type or filter is null
     */
    public final <T extends Event> void addEventFilter(
            final EventType<T> eventType,
            final EventHandler<? super T> eventFilter) {
        getInternalEventDispatcher().getEventHandlerManager()
                                    .addEventFilter(eventType, eventFilter);
    }

    // PENDING_DOC_REVIEW
    /**
     * Unregisters a previously registered event filter from this scene. One
     * filter might have been registered for different event types, so the
     * caller needs to specify the particular event type from which to
     * unregister the filter.
     *
     * @param <T> the specific event class of the filter
     * @param eventType the event type from which to unregister
     * @param eventFilter the filter to unregister
     * @throws NullPointerException if the event type or filter is null
     */
    public final <T extends Event> void removeEventFilter(
            final EventType<T> eventType,
            final EventHandler<? super T> eventFilter) {
        getInternalEventDispatcher().getEventHandlerManager()
                                    .removeEventFilter(eventType, eventFilter);
    }

    /**
     * Sets the handler to use for this event type. There can only be one such
     * handler specified at a time. This handler is guaranteed to be called
     * first. This is used for registering the user-defined onFoo event
     * handlers.
     *
     * @param <T> the specific event class of the handler
     * @param eventType the event type to associate with the given eventHandler
     * @param eventHandler the handler to register, or null to unregister
     * @throws NullPointerException if the event type is null
     */
    protected final <T extends Event> void setEventHandler(
            final EventType<T> eventType,
            final EventHandler<? super T> eventHandler) {
        getInternalEventDispatcher().getEventHandlerManager()
                                    .setEventHandler(eventType, eventHandler);
    }

    private SceneEventDispatcher getInternalEventDispatcher() {
        initializeInternalEventDispatcher();
        return internalEventDispatcher;
    }

    private void initializeInternalEventDispatcher() {
        if (internalEventDispatcher == null) {
            internalEventDispatcher = createInternalEventDispatcher();
            eventDispatcher = new SimpleObjectProperty<EventDispatcher>(
                                          this,
                                          "eventDispatcher",
                                          internalEventDispatcher);
        }
    }

    private SceneEventDispatcher createInternalEventDispatcher() {
        return new SceneEventDispatcher(this);
    }

    /**
     * Registers the specified mnemonic.
     *
     * @param m The mnemonic
     */
    public void addMnemonic(Mnemonic m) {
        getInternalEventDispatcher().getKeyboardShortcutsHandler()
                                    .addMnemonic(m);
    }


    /**
     * Unregisters the specified mnemonic.
     *
     * @param m The mnemonic
     */
    public void removeMnemonic(Mnemonic m) {
        getInternalEventDispatcher().getKeyboardShortcutsHandler()
                                    .removeMnemonic(m);
    }

    /**
     * Gets the list of mnemonics for this {@code Scene}.
     *
     * @return the list of mnemonics
     */
    public ObservableMap<KeyCombination, ObservableList<Mnemonic>> getMnemonics() {
        return getInternalEventDispatcher().getKeyboardShortcutsHandler()
                                           .getMnemonics();
    }

    /**
     * Gets the list of accelerators for this {@code Scene}.
     *
     * @return the list of accelerators
     */
    public ObservableMap<KeyCombination, Runnable> getAccelerators() {
        return getInternalEventDispatcher().getKeyboardShortcutsHandler()
                                           .getAccelerators();
    }

    // PENDING_DOC_REVIEW
    /**
     * Construct an event dispatch chain for this scene. The event dispatch
     * chain contains all event dispatchers from the stage to this scene.
     *
     * @param tail the initial chain to build from
     * @return the resulting event dispatch chain for this scene
     */
    @Override
    public EventDispatchChain buildEventDispatchChain(
            EventDispatchChain tail) {
        if (eventDispatcher != null) {
            tail = tail.prepend(eventDispatcher.get());
        }

        if (getWindow() != null) {
            tail = getWindow().buildEventDispatchChain(tail);
        }

        return tail;
    }
    
    /***************************************************************************
     *                                                                         *
     *                             Context Menus                               *
     *                                                                         *
     **************************************************************************/

    /**
     * Defines a function to be called when a mouse button has been clicked
     * (pressed and released) on this {@code Scene}.
     *
     * @profile common
     */

    private ObjectProperty<EventHandler<? super ContextMenuEvent>> onContextMenuRequested;

    public final void setOnContextMenuRequested(EventHandler<? super ContextMenuEvent> value) {
        onContextMenuRequestedProperty().set(value);
    }

    public final EventHandler<? super ContextMenuEvent> getOnContextMenuRequested() {
        return onContextMenuRequested == null ? null : onContextMenuRequested.get();
    }

    public final ObjectProperty<EventHandler<? super ContextMenuEvent>> onContextMenuRequestedProperty() {
        if (onContextMenuRequested == null) {
            onContextMenuRequested = new ObjectPropertyBase<EventHandler<? super ContextMenuEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onContextMenuRequested";
                }
            };
        }
        return onContextMenuRequested;
    }

    /***************************************************************************
     *                                                                         *
     *                             Mouse Handling                              *
     *                                                                         *
     **************************************************************************/

    /**
     * Defines a function to be called when a mouse button has been clicked
     * (pressed and released) on this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMouseClicked;

    public final void setOnMouseClicked(EventHandler<? super MouseEvent> value) {
        onMouseClickedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseClicked() {
        return onMouseClicked == null ? null : onMouseClicked.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMouseClickedProperty() {
        if (onMouseClicked == null) {
            onMouseClicked = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_CLICKED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseClicked";
                }
            };
        }
        return onMouseClicked;
    }

    /**
     * Defines a function to be called when a mouse button is pressed
     * on this {@code Scene} and then dragged.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMouseDragged;

    public final void setOnMouseDragged(EventHandler<? super MouseEvent> value) {
        onMouseDraggedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseDragged() {
        return onMouseDragged == null ? null : onMouseDragged.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMouseDraggedProperty() {
        if (onMouseDragged == null) {
            onMouseDragged = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_DRAGGED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseDragged";
                }
            };
        }
        return onMouseDragged;
    }

    /**
     * Defines a function to be called when the mouse enters this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMouseEntered;

    public final void setOnMouseEntered(EventHandler<? super MouseEvent> value) {
        onMouseEnteredProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseEntered() {
        return onMouseEntered == null ? null : onMouseEntered.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMouseEnteredProperty() {
        if (onMouseEntered == null) {
            onMouseEntered = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_ENTERED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseEntered";
                }
            };
        }
        return onMouseEntered;
    }

    /**
     * Defines a function to be called when the mouse exits this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMouseExited;

    public final void setOnMouseExited(EventHandler<? super MouseEvent> value) {
        onMouseExitedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseExited() {
        return onMouseExited == null ? null : onMouseExited.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMouseExitedProperty() {
        if (onMouseExited == null) {
            onMouseExited = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_EXITED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseExited";
                }
            };
        }
        return onMouseExited;
    }

    /**
     * Defines a function to be called when mouse cursor moves within
     * this {@code Scene} but no buttons have been pushed.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMouseMoved;

    public final void setOnMouseMoved(EventHandler<? super MouseEvent> value) {
        onMouseMovedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseMoved() {
        return onMouseMoved == null ? null : onMouseMoved.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMouseMovedProperty() {
        if (onMouseMoved == null) {
            onMouseMoved = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_MOVED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseMoved";
                }
            };
        }
        return onMouseMoved;
    }

    /**
     * Defines a function to be called when a mouse button
     * has been pressed on this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMousePressed;

    public final void setOnMousePressed(EventHandler<? super MouseEvent> value) {
        onMousePressedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMousePressed() {
        return onMousePressed == null ? null : onMousePressed.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMousePressedProperty() {
        if (onMousePressed == null) {
            onMousePressed = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_PRESSED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMousePressed";
                }
            };
        }
        return onMousePressed;
    }

    /**
     * Defines a function to be called when a mouse button
     * has been released on this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onMouseReleased;

    public final void setOnMouseReleased(EventHandler<? super MouseEvent> value) {
        onMouseReleasedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseReleased() {
        return onMouseReleased == null ? null : onMouseReleased.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onMouseReleasedProperty() {
        if (onMouseReleased == null) {
            onMouseReleased = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.MOUSE_RELEASED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseReleased";
                }
            };
        }
        return onMouseReleased;
    }

    /**
     * Defines a function to be called when drag gesture has been
     * detected. This is the right place to start drag and drop operation.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseEvent>> onDragDetected;

    public final void setOnDragDetected(EventHandler<? super MouseEvent> value) {
        onDragDetectedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnDragDetected() {
        return onDragDetected == null ? null : onDragDetected.get();
    }

    public final ObjectProperty<EventHandler<? super MouseEvent>> onDragDetectedProperty() {
        if (onDragDetected == null) {
            onDragDetected = new ObjectPropertyBase<EventHandler<? super MouseEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseEvent.DRAG_DETECTED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onDragDetected";
                }
            };
        }
        return onDragDetected;
    }

    /**
     * Defines a function to be called when user performs a scrolling action. 
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super ScrollEvent>> onScroll;

    public final void setOnScroll(EventHandler<? super ScrollEvent> value) {
        onScrollProperty().set(value);
    }

    public final EventHandler<? super ScrollEvent> getOnScroll() {
        return onScroll == null ? null : onScroll.get();
    }

    public final ObjectProperty<EventHandler<? super ScrollEvent>> onScrollProperty() {
        if (onScroll == null) {
            onScroll = new ObjectPropertyBase<EventHandler<? super ScrollEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(ScrollEvent.SCROLL, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onScroll";
                }
            };
        }
        return onScroll;
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * progresses within this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragOver;

    public final void setOnMouseDragOver(EventHandler<? super MouseDragEvent> value) {
        onMouseDragOverProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragOver() {
        return onMouseDragOver == null ? null : onMouseDragOver.get();
    }

    public final ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragOverProperty() {
        if (onMouseDragOver == null) {
            onMouseDragOver = new ObjectPropertyBase<EventHandler<? super MouseDragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseDragEvent.MOUSE_DRAG_OVER, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseDragOver";
                }
            };
        }
        return onMouseDragOver;
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * ends within this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragReleased;

    public final void setOnMouseDragReleased(EventHandler<? super MouseDragEvent> value) {
        onMouseDragReleasedProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragReleased() {
        return onMouseDragReleased == null ? null : onMouseDragReleased.get();
    }

    public final ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragReleasedProperty() {
        if (onMouseDragReleased == null) {
            onMouseDragReleased = new ObjectPropertyBase<EventHandler<? super MouseDragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseDragEvent.MOUSE_DRAG_RELEASED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseDragReleased";
                }
            };
        }
        return onMouseDragReleased;
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * enters this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragEntered;

    public final void setOnMouseDragEntered(EventHandler<? super MouseDragEvent> value) {
        onMouseDragEnteredProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragEntered() {
        return onMouseDragEntered == null ? null : onMouseDragEntered.get();
    }

    public final ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragEnteredProperty() {
        if (onMouseDragEntered == null) {
            onMouseDragEntered = new ObjectPropertyBase<EventHandler<? super MouseDragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseDragEvent.MOUSE_DRAG_ENTERED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseDragEntered";
                }
            };
        }
        return onMouseDragEntered;
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * exits this {@code Scene}.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragExited;

    public final void setOnMouseDragExited(EventHandler<? super MouseDragEvent> value) {
        onMouseDragExitedProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragExited() {
        return onMouseDragExited == null ? null : onMouseDragExited.get();
    }

    public final ObjectProperty<EventHandler<? super MouseDragEvent>> onMouseDragExitedProperty() {
        if (onMouseDragExited == null) {
            onMouseDragExited = new ObjectPropertyBase<EventHandler<? super MouseDragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(MouseDragEvent.MOUSE_DRAG_EXITED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onMouseDragExited";
                }
            };
        }
        return onMouseDragExited;
    }


    /***************************************************************************
     *                                                                         *
     *                         Drag and Drop Handling                          *
     *                                                                         *
     **************************************************************************/

    private ObjectProperty<EventHandler<? super DragEvent>> onDragEntered;

    public final void setOnDragEntered(EventHandler<? super DragEvent> value) {
        onDragEnteredProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragEntered() {
        return onDragEntered == null ? null : onDragEntered.get();
    }

    /**
     * Defines a function to be called when drag gesture
     * enters this {@code Scene}.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>> onDragEnteredProperty() {
        if (onDragEntered == null) {
            onDragEntered = new ObjectPropertyBase<EventHandler<? super DragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(DragEvent.DRAG_ENTERED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onDragEntered";
                }
            };
        }
        return onDragEntered;
    }

    private ObjectProperty<EventHandler<? super DragEvent>> onDragExited;

    public final void setOnDragExited(EventHandler<? super DragEvent> value) {
        onDragExitedProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragExited() {
        return onDragExited == null ? null : onDragExited.get();
    }

    /**
     * Defines a function to be called when drag gesture
     * exits this {@code Scene}.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>> onDragExitedProperty() {
        if (onDragExited == null) {
            onDragExited = new ObjectPropertyBase<EventHandler<? super DragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(DragEvent.DRAG_EXITED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onDragExited";
                }
            };
        }
        return onDragExited;
    }

    private ObjectProperty<EventHandler<? super DragEvent>> onDragOver;

    public final void setOnDragOver(EventHandler<? super DragEvent> value) {
        onDragOverProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragOver() {
        return onDragOver == null ? null : onDragOver.get();
    }

    /**
     * Defines a function to be called when drag gesture progresses
     * within this {@code Scene}.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>> onDragOverProperty() {
        if (onDragOver == null) {
            onDragOver = new ObjectPropertyBase<EventHandler<? super DragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(DragEvent.DRAG_OVER, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onDragOver";
                }
            };
        }
        return onDragOver;
    }

    // Do we want DRAG_TRANSFER_MODE_CHANGED event?
//    private ObjectProperty<EventHandler<? super DragEvent>> onDragTransferModeChanged;
//
//    public final void setOnDragTransferModeChanged(EventHandler<? super DragEvent> value) {
//        onDragTransferModeChangedProperty().set(value);
//    }
//
//    public final EventHandler<? super DragEvent> getOnDragTransferModeChanged() {
//        return onDragTransferModeChanged == null ? null : onDragTransferModeChanged.get();
//    }
//
//    /**
//     * Defines a function to be called this {@code Scene} if it is a potential
//     * drag-and-drop target when the user takes action to change the intended
//     * {@code TransferMode}.
//     * The user can change the intended {@link TransferMode} by holding down
//     * or releasing key modifiers.
//     */
//    public ObjectProperty<EventHandler<? super DragEvent>> onDragTransferModeChangedProperty() {
//        if (onDragTransferModeChanged == null) {
//            onDragTransferModeChanged = new SimpleObjectProperty<EventHandler<? super DragEvent>>() {
//
//                @Override
//                protected void invalidated() {
//                    setEventHandler(DragEvent.DRAG_TRANSFER_MODE_CHANGED, get());
//                }
//            };
//        }
//        return onDragTransferModeChanged;
//    }

    private ObjectProperty<EventHandler<? super DragEvent>> onDragDropped;

    public final void setOnDragDropped(EventHandler<? super DragEvent> value) {
        onDragDroppedProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragDropped() {
        return onDragDropped == null ? null : onDragDropped.get();
    }

    /**
     * Defines a function to be called when the mouse button is released
     * on this {@code Scene} during drag and drop gesture. Transfer of data from
     * the {@link DragEvent}'s {@link DragEvent#dragboard dragboard} should
     * happen in this function.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>> onDragDroppedProperty() {
        if (onDragDropped == null) {
            onDragDropped = new ObjectPropertyBase<EventHandler<? super DragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(DragEvent.DRAG_DROPPED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onDragDropped";
                }
            };
        }
        return onDragDropped;
    }

    private ObjectProperty<EventHandler<? super DragEvent>> onDragDone;

    public final void setOnDragDone(EventHandler<? super DragEvent> value) {
        onDragDoneProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragDone() {
        return onDragDone == null ? null : onDragDone.get();
    }

    /**
     * Defines a function to be called when this @{code Scene} is a
     * drag and drop gesture source after its data has
     * been dropped on a drop target. The {@code transferMode} of the
     * event shows what just happened at the drop target.
     * If {@code transferMode} has the value {@code MOVE}, then the source can
     * clear out its data. Clearing the source's data gives the appropriate
     * appearance to a user that the data has been moved by the drag and drop
     * gesture. A {@code transferMode} that has the value {@code NONE}
     * indicates that no data was transferred during the drag and drop gesture.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>> onDragDoneProperty() {
        if (onDragDone == null) {
            onDragDone = new ObjectPropertyBase<EventHandler<? super DragEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(DragEvent.DRAG_DONE, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onDragDone";
                }
            };
        }
        return onDragDone;
    }

    /**
     * Confirms a potential drag and drop gesture that is recognized over this
     * {@code Scene}.
     * Can be called only from a DRAG_DETECTED event handler. The returned
     * {@link Dragboard} is used to transfer data during
     * the drag and drop gesture. Placing this {@code Scene}'s data on the
     * {@link Dragboard} also identifies this {@code Scene} as the source of
     * the drag and drop gesture.
     * More detail about drag and drop gestures is described in the overivew
     * of {@link DragEvent}.
     *
     * @see DragEvent
     * @param transferModes The supported {@code TransferMode}(s) of this {@code Node}
     * @return A {@code Dragboard} to place this {@code Scene}'s data on
     * @throws IllegalStateException if drag and drop cannot be started at this
     * moment (it's called outside of {@code DRAG_DETECTED} event handling).
     */
    public Dragboard startDragAndDrop(TransferMode... transferModes) {
        return impl_startDragAndDrop(this, transferModes);
    }

    /**
     * Starts a full press-drag-release gesture with this scene as gesture
     * source. This method can be called only from a {@code DRAG_DETECTED} mouse
     * event handler. More detail about dragging gestures can be found
     * in the overview of {@link MouseEvent} and {@link MouseDragEvent}.
     *
     * @see MosueEvent
     * @see MouseDragEvent
     * @throws IllegalStateException if the full press-drag-release gesture
     * cannot be started at this moment (it's called outside of
     * {@code DRAG_DETECTED} event handling).
     */
    public void startFullDrag() {
        impl_startFullDrag(this);
    }


    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Dragboard impl_startDragAndDrop(EventTarget source,
            TransferMode... transferModes) {

        if (dndGesture.dragDetected != DragDetectedState.PROCESSING) {
            throw new IllegalStateException("Cannot start drag and drop " +
                    "outside of DRAG_DETECTED event handler");
        }

        if (dndGesture != null) {
            Set<TransferMode> set = EnumSet.noneOf(TransferMode.class);
            for (TransferMode tm : transferModes) {
                set.add(tm);
            }
            return dndGesture.startDrag(source, set);
        }

        throw new IllegalStateException("Cannot start drag and drop when "
                + "mouse button is not pressed");
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_startFullDrag(EventTarget source) {

        if (dndGesture.dragDetected != DragDetectedState.PROCESSING) {
            throw new IllegalStateException("Cannot start full drag " +
                    "outside of DRAG_DETECTED event handler");
        }

        if (dndGesture != null) {
            dndGesture.startFullPDR(source);
            return;
        }

        throw new IllegalStateException("Cannot start full drag when "
                + "mouse button is not pressed");
    }

    /***************************************************************************
     *                                                                         *
     *                           Keyboard Handling                             *
     *                                                                         *
     **************************************************************************/

    /**
     * Defines a function to be called when some {@code Node} of this
     * {@code Scene} has input focus and a key has been pressed. The function
     * is called only if the event hasn't been already consumed during its
     * capturing or bubbling phase.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super KeyEvent>> onKeyPressed;

    public final void setOnKeyPressed(EventHandler<? super KeyEvent> value) {
        onKeyPressedProperty().set(value);
    }

    public final EventHandler<? super KeyEvent> getOnKeyPressed() {
        return onKeyPressed == null ? null : onKeyPressed.get();
    }

    public final ObjectProperty<EventHandler<? super KeyEvent>> onKeyPressedProperty() {
        if (onKeyPressed == null) {
            onKeyPressed = new ObjectPropertyBase<EventHandler<? super KeyEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(KeyEvent.KEY_PRESSED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onKeyPressed";
                }
            };
        }
        return onKeyPressed;
    }

    /**
     * Defines a function to be called when some {@code Node} of this
     * {@code Scene} has input focus and a key has been released. The function
     * is called only if the event hasn't been already consumed during its
     * capturing or bubbling phase.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super KeyEvent>> onKeyReleased;

    public final void setOnKeyReleased(EventHandler<? super KeyEvent> value) {
        onKeyReleasedProperty().set(value);
    }

    public final EventHandler<? super KeyEvent> getOnKeyReleased() {
        return onKeyReleased == null ? null : onKeyReleased.get();
    }

    public final ObjectProperty<EventHandler<? super KeyEvent>> onKeyReleasedProperty() {
        if (onKeyReleased == null) {
            onKeyReleased = new ObjectPropertyBase<EventHandler<? super KeyEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(KeyEvent.KEY_RELEASED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onKeyReleased";
                }
            };
        }
        return onKeyReleased;
    }

    /**
     * Defines a function to be called when some {@code Node} of this
     * {@code Scene} has input focus and a key has been typed. The function
     * is called only if the event hasn't been already consumed during its
     * capturing or bubbling phase.
     *
     * @profile common
     */
    private ObjectProperty<EventHandler<? super KeyEvent>> onKeyTyped;

    public final void setOnKeyTyped(
            EventHandler<? super KeyEvent> value) {
        onKeyTypedProperty().set( value);

    }

    public final EventHandler<? super KeyEvent> getOnKeyTyped(
            ) {
        return onKeyTyped == null ? null : onKeyTyped.get();
    }

    public final ObjectProperty<EventHandler<? super KeyEvent>> onKeyTypedProperty(
    ) {
        if (onKeyTyped == null) {
            onKeyTyped = new ObjectPropertyBase<EventHandler<? super KeyEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(KeyEvent.KEY_TYPED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onKeyTyped";
                }
            };
        }
        return onKeyTyped;
    }

    /***************************************************************************
     *                                                                         *
     *                           Input Method Handling                         *
     *                                                                         *
     **************************************************************************/

    /**
     * Defines a function to be called when this {@code Node}
     * has input focus and the input method text has changed.  If this
     * function is not defined in this {@code Node}, then it
     * receives the result string of the input method composition as a
     * series of {@code onKeyTyped} function calls.
     * </p>
     * When the {@code Node} loses the input focus, the JavaFX runtime
     * automatically commits the existing composed text if any.
     *
     * @profile common conditional input_method
     */
    private ObjectProperty<EventHandler<? super InputMethodEvent>> onInputMethodTextChanged;

    public final void setOnInputMethodTextChanged(
            EventHandler<? super InputMethodEvent> value) {
        onInputMethodTextChangedProperty().set( value);
    }

    public final EventHandler<? super InputMethodEvent> getOnInputMethodTextChanged() {
        return onInputMethodTextChanged == null ? null : onInputMethodTextChanged.get();
    }

    public final ObjectProperty<EventHandler<? super InputMethodEvent>> onInputMethodTextChangedProperty() {
        if (onInputMethodTextChanged == null) {
            onInputMethodTextChanged = new ObjectPropertyBase<EventHandler<? super InputMethodEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, get());
                }

                @Override
                public Object getBean() {
                    return Scene.this;
                }

                @Override
                public String getName() {
                    return "onInputMethodTextChanged";
                }
            };
        }
        return onInputMethodTextChanged;
    }
}