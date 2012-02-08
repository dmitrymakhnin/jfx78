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


import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Effect;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.InputEvent;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodRequests;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;

import com.sun.javafx.Logging;
import com.sun.javafx.TempState;
import com.sun.javafx.Utils;
import com.sun.javafx.beans.IDProperty;
import com.sun.javafx.beans.event.AbstractNotifyListener;
import com.sun.javafx.binding.ExpressionHelper;
import com.sun.javafx.collections.TrackableObservableList;
import com.sun.javafx.collections.UnmodifiableListSet;
import com.sun.javafx.css.*;
import com.sun.javafx.css.StyleHelper.StyleCacheKey;
import com.sun.javafx.css.converters.*;
import com.sun.javafx.effect.EffectDirtyBits;
import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.PickRay;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.transform.Affine3D;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.geom.transform.NoninvertibleTransformException;
import com.sun.javafx.jmx.MXNodeAlgorithm;
import com.sun.javafx.jmx.MXNodeAlgorithmContext;
import com.sun.javafx.logging.PlatformLogger;
import com.sun.javafx.perf.PerformanceTracker;
import com.sun.javafx.scene.BoundsAccessor;
import com.sun.javafx.scene.CSSFlags;
import com.sun.javafx.scene.DirtyBits;
import com.sun.javafx.scene.EventHandlerProperties;
import com.sun.javafx.scene.NodeEventDispatcher;
import com.sun.javafx.scene.traversal.Direction;
import com.sun.javafx.sg.PGNode;
import java.util.*;
import javafx.beans.property.*;
import javafx.beans.value.WritableValue;
import javafx.scene.input.ScrollEvent;
import javafx.scene.text.Font;

/**
 * Base class for scene graph nodes. A scene graph is a set of tree data structures
 * where every item has zero or one parent, and each item is either
 * a "leaf" with zero sub-items or a "branch" with zero or more sub-items.
 * <p>
 * Each item in the scene graph is called a {@code Node}. Branch nodes are
 * of type {@link Parent}, whose concrete subclasses are {@link Group},
 * {@link javafx.scene.layout.Region}, and {@link javafx.scene.control.Control},
 * or subclasses thereof.
 * <p>
 * Leaf nodes are classes such as
 * {@link javafx.scene.shape.Rectangle}, {@link javafx.scene.text.Text},
 * {@link javafx.scene.image.ImageView}, {@link javafx.scene.media.MediaView},
 * or other such leaf classes which cannot have children. Only a single node within
 * each scene graph tree will have no parent, which is referred to as the "root" node.
 * <p>
 * There may be several trees in the scene graph. Some trees may be part of
 * a {@link Scene}, in which case they are eligible to be displayed.
 * Other trees might not be part of any {@link Scene}.
 * <p>
 * A node may occur at most once anywhere in the scene graph. Specifically,
 * a node must appear no more than once in all of the following:
 * as the root node of a {@link Scene},
 * the children ObservableList of a {@link Parent},
 * or as the clip of a {@link Node}.
 * <p>
 * The scene graph must not have cycles. A cycle would exist if a node is
 * an ancestor of itself in the tree, considering the {@link Group} content
 * ObservableList, {@link Parent} children ObservableList, and {@link Node} clip relationships
 * mentioned above.
 * <p>
 * If a program adds a child node to a Parent (including Group, Region, etc)
 * and that node is already a child of a different Parent or the root of a Scene,
 * the node is automatically (and silently) removed from its former parent.
 * If a program attempts to modify the scene graph in any other way that violates
 * the above rules, an exception is thrown, the modification attempt is ignored
 * and the scene graph is restored to its previous state.
 * <p>
 * It is possible to rearrange the structure of the scene graph, for
 * example, to move a subtree from one location in the scene graph to
 * another. In order to do this, one would normally remove the subtree from
 * its old location before inserting it at the new location. However, the
 * subtree will be automatically removed as described above if the application
 * doesn't explicitly remove it.
 * <p>
 * Node objects may be constructed and modified on any thread as long they are
 * not yet attached to a {@link Scene}. An application must attach nodes to a
 * Scene, and modify nodes that are already attached to a Scene, on the JavaFX
 * Application Thread.
 *
 * <h4>String ID</h4>
 * <p>
 * Each node in the scene graph can be given a unique {@link #idProperty id}. This id is
 * much like the "id" attribute of an HTML tag in that it is up to the designer
 * and developer to ensure that the {@code id} is unique within the scene graph.
 * A convenience function called {@link #lookup(String)} can be used to find
 * a node with a unique id within the scene graph, or within a subtree of the
 * scene graph. The id can also be used identify nodes for applying styles; see
 * the CSS section below.
 *
 * <h4>Coordinate System</h4>
 * <p>
 * The {@code Node} class defines a traditional computer graphics "local"
 * coordinate system in which the {@code x} axis increases to the right and the
 * {@code y} axis increases downwards.  The concrete node classes for shapes
 * provide variables for defining the geometry and location of the shape
 * within this local coordinate space.  For example,
 * {@link javafx.scene.shape.Rectangle} provides {@code x}, {@code y},
 * {@code width}, {@code height} variables while
 * {@link javafx.scene.shape.Circle} provides {@code centerX}, {@code centerY},
 * and {@code radius}.
 * <p>
 * At the device pixel level, integer coordinates map onto the corners and
 * cracks between the pixels and the centers of the pixels appear at the
 * midpoints between integer pixel locations.  Because all coordinate values
 * are specified with floating point numbers, coordinates can precisely
 * point to these corners (when the floating point values have exact integer
 * values) or to any location on the pixel.  For example, a coordinate of
 * {@code (0.5, 0.5)} would point to the center of the upper left pixel on the
 * {@code Stage}.  Similarly, a rectangle at {@code (0, 0)} with dimensions
 * of {@code 10} by {@code 10} would span from the upper left corner of the
 * upper left pixel on the {@code Stage} to the lower right corner of the
 * 10th pixel on the 10th scanline.  The pixel center of the last pixel
 * inside that rectangle would be at the coordinates {@code (9.5, 9.5)}.
 * <p>
 * In practice, most nodes have transformations applied to their coordinate
 * system as mentioned below.  As a result, the information above describing
 * the alignment of device coordinates to the pixel grid is relative to
 * the transformed coordinates, not the local coordinates of the nodes.
 * The {@link javafx.scene.shape.Shape Shape} class describes some additional
 * important context-specific information about coordinate mapping and how
 * it can affect rendering.
 *
 * <h4>Transformations</h4>
 * <p>
 * Any {@code Node} can have transformations applied to it. These include
 * translation, rotation, scaling, or shearing.
 * <p>
 * A <b>translation</b> transformation is one which shifts the origin of the
 * node's coordinate space along either the x or y axis. For example, if you
 * create a {@link javafx.scene.shape.Rectangle} which is drawn at the origin
 * (x=0, y=0) and has a width of 100 and a height of 50, and then apply a
 * {@link javafx.scene.transform.Translate} with a shift of 10 along the x axis
 * (x=10), then the rectangle will appear drawn at (x=10, y=0) and remain
 * 100 points wide and 50 tall. Note that the origin was shifted, not the
 * {@code x} variable of the rectangle.
 * <p>
 * A common node transform is a translation by an integer distance, most often
 * used to lay out nodes on the stage.  Such integer translations maintain the
 * device pixel mapping so that local coordinates that are integers still
 * map to the cracks between pixels.
 * <p>
 * A <b>rotation</b> transformation is one which rotates the coordinate space of
 * the node about a specified "pivot" point, causing the node to appear rotated.
 * For example, if you create a {@link javafx.scene.shape.Rectangle} which is
 * drawn at the origin (x=0, y=0) and has a width of 100 and height of 30 and
 * you apply a {@link javafx.scene.transform.Rotate} with a 90 degree rotation
 * (angle=90) and a pivot at the origin (pivotX=0, pivotY=0), then
 * the rectangle will be drawn as if its x and y were zero but its height was
 * 100 and its width -30. That is, it is as if a pin is being stuck at the top
 * left corner and the rectangle is rotating 90 degrees clockwise around that
 * pin. If the pivot point is instead placed in the center of the rectangle
 * (at point x=50, y=15) then the rectangle will instead appear to rotate about
 * its center.
 * <p>
 * Note that as with all transformations, the x, y, width, and height variables
 * of the rectangle (which remain relative to the local coordinate space) have
 * not changed, but rather the transformation alters the entire coordinate space
 * of the rectangle.
 * <p>
 * A <b>scaling</b> transformation causes a node to either appear larger or
 * smaller depending on the scaling factor. Scaling alters the coordinate space
 * of the node such that each unit of distance along the axis in local
 * coordinates is multipled by the scale factor. As with rotation
 * transformations, scaling transformations are applied about a "pivot" point.
 * You can think of this as the point in the Node around which you "zoom".  For
 * example, if you create a {@link javafx.scene.shape.Rectangle} with a
 * {@code strokeWidth} of 5, and a width and height of 50, and you apply a
 * {@link javafx.scene.transform.Scale} with scale factors (x=2.0, y=2.0) and
 * a pivot at the origin (pivotX=0, pivotY=0), the entire rectangle
 * (including the stroke) will double in size, growing to the right and
 * downwards from the origin.
 * <p>
 * A <b>shearing</b> transformation, sometimes called a skew, effectively
 * rotates one axis so that the x and y axes are no longer perpendicular.
 * <p>
 * Multiple transformations may be applied to a node by specifying an ordered
 * chain of transforms.  The order in which the transforms are applied is
 * defined by the ObservableList specified in the {@link #getTransforms transforms} variable.
 *
 * <h4>Bounding Rectangles</h4>
 * <p>
 * Since every {@code Node} has transformations, every Node's geometric
 * bounding rectangle can be described differently depending on whether
 * transformations are accounted for or not.
 * <p>
 * Each {@code Node} has a read-only {@link #boundsInLocalProperty boundsInLocal}
 * variable which specifies the bounding rectangle of the {@code Node} in
 * untransformed local coordinates. {@code boundsInLocal} includes the
 * Node's shape geometry, including any space required for a
 * non-zero stroke that may fall outside the local position/size variables,
 * and its {@link #clipProperty clip} and {@link #effectProperty effect} variables.
 * <p>
 * Each {@code Node} also has a read-only {@link #boundsInParentProperty boundsInParent} variable which
 * specifies the bounding rectangle of the {@code Node} after all transformations
 * have been applied, including those set in {@link #getTransforms transforms},
 * {@link #scaleXProperty scaleX}/{@link #scaleYProperty scaleY}, {@link #rotateProperty rotate},
 * {@link #translateXProperty translateX}/{@link #translateYProperty translateY}, and {@link #layoutXProperty layoutX}/{@link #layoutYProperty layoutY}.
 * It is called "boundsInParent" because the rectangle will be relative to the
 * parent's coordinate system.  This is the 'visual' bounds of the node.
 * <p>
 * Finally, the {@link #layoutBoundsProperty layoutBounds} variable defines the rectangular bounds of
 * the {@code Node} that should be used as the basis for layout calculations and
 * may differ from the visual bounds of the node.  For shapes, Text, and ImageView,
 * layoutBounds by default includes only the shape geometry, including space required
 * for a non-zero {@code strokeWidth}, but does <i>not</i> include the effect,
 * clip, or any transforms. For resizable classes (Regions and Controls)
 * layoutBounds will always map to {@code 0,0 width x height}.
 *
 * <p> The image shows a node with transformation (rotation by 20 degrees)
 * and its bounds. The red rectangle represents {@code boundsInParent} in the
 * coordinate space of the Node's parent. The green rectangle represents {@code boundsInLocal}
 * in coordinate space of the Node. </p>
 * <p> <img src="doc-files/bounds-complex.png"/> </p>
 *
 * <p> The images show a filled and stroked rectangle and their bounds. The
 * first rectangle {@code [x:10.0 y:10.0 width:100.0 height:100.0 strokeWidth:0]}
 * has the following bounds bounds: {@code [x:10.0 y:10.0 width:100.0 height:100.0]}.
 *
 * The second rectangle {@code [x:10.0 y:10.0 width:100.0 height:100.0 strokeWidth:5]}
 * has the following bounds: {@code [x:5.0 y:5.0 width:110.0 height:110.0]}.
 *
 * Since neither of the rectangles has any transformation applied,
 * {@code boundsInParent} and {@code boundsInLocal} are the same. </p>
 * <p> <img src="doc-files/bounds.png"/> </p>
 *
 * 
 * <h4>CSS</h4>
 * <p>
 * The {@code Node} class contains {@code id}, {@code styleClass}, and
 * {@code style} variables that are used in styling this node from
 * CSS. The {@code id} and {@code styleClass} variables are used in
 * CSS style sheets to identify nodes to which styles should be
 * applied. The {@code style} variable contains style properties and
 * values that are applied directly to this node.
 * <p>
 * For further information about CSS and how to apply CSS styles
 * to nodes, see the <a href="doc-files/cssref.html">CSS Reference
 * Guide</a>.
 *
 * @profile common
 */
@IDProperty("id")
public abstract class Node implements EventTarget {

     // PERF: TODO remove before shipping
     static {
          PerformanceTracker.logEvent("Node.fx class loaded");
     }

    /**************************************************************************
     *                                                                        *
     * Methods and state for managing the dirty bits of a Node. The dirty     *
     * bits are flags used to keep track of what things are dirty on the      *
     * node and therefore need processing on the next pulse. Since the pulse  *
     * happens asynchronously to the change that made the node dirty (for     *
     * performance reasons), we need to keep track of what things have        *
     * changed.                                                               *
     *                                                                        *
     *************************************************************************/

    /**
     * Set of dirty bits that are set when state is invalidated and cleared by
     * the updateState method, which is called from the synchronizer.
     */
    private int dirtyBits;

    /**
     * Mark the specified bit as dirty, and add this node to the scene's dirty list.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected void impl_markDirty(DirtyBits dirtyBit) {
        if ((getScene() != null) && impl_isDirtyEmpty()) {
            getScene().addToDirtyList(this);
        }

        impl_setDirty(dirtyBit);
    }

    /**
     * Test whether the specified dirty bit is set
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected boolean impl_isDirty(DirtyBits dirtyBit) {
        return (dirtyBits & dirtyBit.getMask()) != 0;
    }

    /**
     * Set the specified dirty bit.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    private void impl_setDirty(DirtyBits dirtyBit) {
        dirtyBits |= dirtyBit.getMask();
    }

    /**
     * Clear the specified dirty bit
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected void impl_clearDirty(DirtyBits dirtyBit) {
        dirtyBits &= ~dirtyBit.getMask();
    }

    /**
     * Set all dirty bits
     */
    private void setDirty() {
        dirtyBits = ~0;
    }

    /**
     * Clear all dirty bits
     */
    private void clearDirty() {
        dirtyBits = 0;
    }

    /**
     * Test whether the set of dirty bits is empty
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected boolean impl_isDirtyEmpty() {
        return dirtyBits == 0;
    }

    /**************************************************************************
     *                                                                        *
     * Methods for synchronizing state from this Node to its PG peer. This    *
     * should only *ever* be called during synchronization initialized as a   *
     * result of a pulse. Any attempt to synchronize at any other time may    *
     * cause rendering artifacts.                                             *
     *                                                                        *
     *************************************************************************/

    /**
     * Called only by Text to update the state and clear dirtybits in the PG graph
     * TODO: This must be removed, and TextHelper should work with a PG node
     * directly or some other means rather than using this method. This is a very
     * dangerous method to have.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_syncPGNodeDirect() {
        if (!impl_isDirtyEmpty()) {
            // Workaround for Don't RT-7446 update the state controlled by
            // NODE_TRANSFORM.
            boolean nodeTxDirty = impl_isDirty(DirtyBits.NODE_TRANSFORM);
            impl_clearDirty(DirtyBits.NODE_TRANSFORM);
            boolean boundsDirty = impl_isDirty(DirtyBits.NODE_BOUNDS);
            impl_clearDirty(DirtyBits.NODE_BOUNDS);
            impl_updatePG();
            clearDirty();
            if (nodeTxDirty) impl_setDirty(DirtyBits.NODE_TRANSFORM);
            if (boundsDirty) impl_setDirty(DirtyBits.NODE_BOUNDS);
        }
    }

    /**
     * Called by the synchronizer to update the state and
     * clear dirtybits of this node in the PG graph
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public final void impl_syncPGNode() {
        if (!impl_isDirtyEmpty()) {
            impl_updatePG();
            clearDirty();
        }
    }

    /**
     * This function is called by the synchronizer to update the state of the
     * PG Node from the FX Node. Subclasses of Node should override this method
     * and must call super.impl_updatePG()
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_updatePG() {
        final PGNode peer = impl_getPGNode();
        // TODO: if we find that TempState.getInstance() is too expensive,
        //       we can try to pass it as an argument to this method
        TempState tempState = null;
        if (impl_isDirty(DirtyBits.NODE_TRANSFORM)) {
            tempState = TempState.getInstance();
            updateLocalToParentTransform();
            peer.setTransformMatrix(localToParentTx);
            tempState.bounds = getTransformedBounds(
                                       tempState.bounds,
                                       BaseTransform.IDENTITY_TRANSFORM);
            peer.setTransformedBounds(tempState.bounds);
        }

        if (impl_isDirty(DirtyBits.NODE_BOUNDS)) {
            if (tempState == null) {
                tempState = TempState.getInstance();
            }
             tempState.bounds = getGeomBounds(tempState.bounds,
                                             BaseTransform.IDENTITY_TRANSFORM);
            peer.setContentBounds(tempState.bounds);
            if (!impl_isDirty(DirtyBits.NODE_TRANSFORM)) {
                tempState.bounds = getTransformedBounds(
                                           tempState.bounds,
                                           BaseTransform.IDENTITY_TRANSFORM);
                peer.setTransformedBounds(tempState.bounds);
            }
        }

        if (impl_isDirty(DirtyBits.NODE_OPACITY)) {
            peer.setOpacity((float)Utils.clamp(0, getOpacity(), 1));
        }

        if (impl_isDirty(DirtyBits.NODE_CACHE)) {
            peer.setCachedAsBitmap(isCache(), toPGCacheHint(getCacheHint()));
        }

        if (impl_isDirty(DirtyBits.NODE_CLIP)) {
            peer.setClipNode(getClip() != null ? getClip().impl_getPGNode() : null);
        }

        if (impl_isDirty(DirtyBits.EFFECT_EFFECT)) {
            if (getEffect() != null) {
                getEffect().impl_sync();
                peer.effectChanged();
            }
        }

        if (impl_isDirty(DirtyBits.NODE_EFFECT)) {
            peer.setEffect(getEffect() != null ? getEffect().impl_getImpl() : null);
        }

        if (impl_isDirty(DirtyBits.NODE_VISIBLE)) {
            peer.setVisible(isVisible());
        }

        if (impl_isDirty(DirtyBits.NODE_DEPTH_TEST)) {
            peer.setDepthTest(isDerivedDepthTest());
        }

        if (impl_isDirty(DirtyBits.NODE_BLENDMODE)) {
            BlendMode mode = getBlendMode();
            peer.setNodeBlendMode((mode == null)
                                  ? null
                                  : Blend.impl_getToolkitMode(mode));
        }
    }

    /*************************************************************************
    *                                                                        *
    *                                                                        *
    *                                                                        *
    *************************************************************************/

    private static final Object USER_DATA_KEY = new Object();
    // A map containing a set of properties for this node
    private ObservableMap<Object, Object> properties;

    /**
      * Returns an observable map of properties on this node for use primarily
      * by application developers.
      *
      * @return an observable map of properties on this node for use primarily
      * by application developers
      */
     public final ObservableMap<Object, Object> getProperties() {
        if (properties == null) {
            properties = FXCollections.observableMap(new HashMap<Object, Object>());
        }
        return properties;
    }
    
    /**
     * Tests if Node has properties.
     * @return true if node has properties.
     */
     public boolean hasProperties() {
        return properties != null;
    }

    /**
     * Convenience method for setting a single Object property that can be
     * retrieved at a later date. This is functionally equivalent to calling
     * the getProperties().put(Object key, Object value) method. This can later
     * be retrieved by calling {@link Node#getUserData()}.
     *
     * @param value The value to be stored - this can later be retrieved by calling
     *          {@link Node#getUserData()}.
     */
    public void setUserData(Object value) {
        getProperties().put(USER_DATA_KEY, value);
    }

    /**
     * Returns a previously set Object property, or null if no such property
     * has been set using the {@link Node#setUserData(java.lang.Object)} method.
     *
     * @return The Object that was previously set, or null if no property
     *          has been set or if null was set.
     */
    public Object getUserData() {
        return getProperties().get(USER_DATA_KEY);
    }

    /**************************************************************************
     *                                                                        *
     *
     *                                                                        *
     *************************************************************************/

    /**
     * The parent of this {@code Node}. If this {@code Node} has not been added
     * to a scene graph, then parent will be null.
     *
     * @profile common
     * @defaultvalue null
     */
    private ReadOnlyObjectWrapper<Parent> parent;

    final void setParent(Parent value) {
        parentPropertyImpl().set(value);
    }

    public final Parent getParent() {
        return parent == null ? null : parent.get();
    }

    public final ReadOnlyObjectProperty<Parent> parentProperty() {
        return parentPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<Parent> parentPropertyImpl() {
        if (parent == null) {
            parent = new ReadOnlyObjectWrapper<Parent>() {
                private Parent oldParent;

                @Override
                protected void invalidated() {
                    if (oldParent != null) {
                        oldParent.disabledProperty().removeListener(parentDisabledChangedListener);
                        oldParent.treeVisibleProperty().removeListener(parentTreeVisibleChangedListener);
                    }
                    updateDisabled();
                    computeDerivedDepthTest();
                    final Parent newParent = get();
                    if (newParent != null) {
                    	newParent.disabledProperty().addListener(parentDisabledChangedListener);
                    	newParent.treeVisibleProperty().addListener(parentTreeVisibleChangedListener);
                        //
                        // if parent changed, then CSS needs to be reapplied so
                        // that this node will get the right styles. This used
                        // to be done from Parent.children's onChanged method.
                        // See the comments there, also.
                        //
                        impl_reapplyCSS();
                    }
                    updateTreeVisible();
                    oldParent = newParent;
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "parent";
                }
            };
        }
        return parent;
    }

    private final InvalidationListener parentDisabledChangedListener = new InvalidationListener() {
        @Override public void invalidated(Observable valueModel) {
            updateDisabled();
        }
    };

    private final InvalidationListener parentTreeVisibleChangedListener = new InvalidationListener() {
        @Override public void invalidated(Observable valueModel) {
            updateTreeVisible();
        }
    };

    /**
     * The {@link Scene} that this {@code Node} is part of. If the Node is not
     * part of a scene, then this variable will be null.
     *
     * @profile common
     * @defaultvalue null
     */
    private ReadOnlyObjectWrapper<Scene> scene;

    final void setScene(Scene value) {
        scenePropertyImpl().set(value);
    }

    public final Scene getScene() {
        return scene == null ? null : scene.get();
    }

    /**
     * Exists for Parent
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    void impl_sceneChanged(Scene old) { }

    public final ReadOnlyObjectProperty<Scene> sceneProperty() {
        return scenePropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<Scene> scenePropertyImpl() {
        if (scene == null) {
            scene = new ReadOnlyObjectWrapper<Scene>() {
                private Scene oldScene;

                @Override
                protected void invalidated() {
                    Scene _scene = get();
                    if (oldScene != _scene) {
                        if (oldScene != null) {
                            oldScene.removeFromDirtyList(Node.this);
                        }
                        impl_reapplyCSS();
                        if (_scene != null && !impl_isDirtyEmpty()) {
                            _scene.addToDirtyList(Node.this);
                        }
                    }
                    if (getClip() != null) {
                        getClip().setScene(_scene);
                    }
                    updateCanReceiveFocus();
                    if (isFocusTraversable()) {
                        if (oldScene != null) {
                            oldScene.unregisterTraversable(Node.this);
                        }
                        if (_scene != null) {
                            _scene.registerTraversable(Node.this);
                        }
                    }
                    focusSetDirty(oldScene);
                    focusSetDirty(_scene);
                    impl_sceneChanged(oldScene);
                    oldScene = _scene;
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "scene";
                }
            };
        }
        return scene;
    }

    /**
     * The id of this {@code Node}. This simple string identifier is useful for
     * finding a specific Node within the scene graph. While the id of a Node
     * should be unique within the scene graph, this uniqueness is not enforced.
     * This is analogous to the "id" attribute on an HTML element 
     * (<a href="http://www.w3.org/TR/CSS21/syndata.html#value-def-identifier">CSS ID Specification</a>).
     * <p>
     *     For example, if a Node is given the id of "myId", then the lookup method can
     *     be used to find this node as follows: <code>scene.lookup("#myId");</code>.
     * </p>
     *
     * @profile common
     * @defaultvalue null
     */
    private StringProperty id;

    public final void setId(String value) {
        idProperty().set(value);
    }

    //TODO: this is copied from the property in order to add the @return statement.
    //      We should have a better, general solution without the need to copy it.
    /**
     * The id of this {@code Node}. This simple string identifier is useful for
     * finding a specific Node within the scene graph. While the id of a Node
     * should be unique within the scene graph, this uniqueness is not enforced.
     * This is analogous to the "id" attribute on an HTML element 
     * (<a href="http://www.w3.org/TR/CSS21/syndata.html#value-def-identifier">CSS ID Specification</a>).
     *
     * @return the id assigned to this {@code Node} using the {@code setId} 
     *         method or {@code null}, if no id has been assigned.
     * @profile common
     * @defaultvalue null
     */
    public final String getId() {
        return id == null ? null : id.get();
    }

    public final StringProperty idProperty() {
        if (id == null) {
            id = new StringPropertyBase() {

                @Override
                protected void invalidated() {
                     impl_reapplyCSS();
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "id";
                }
            };
        }
        return id;
    }

    /**
     * A list of String identifiers which can be used to logically group
     * Nodes, specifically for an external style engine. This variable is
     * analogous to the "class" attribute on an HTML element and, as such,
     * each element of the list is a style class to which this Node belongs.
     *
     * @see <a href="http://www.w3.org/TR/css3-selectors/#class-html">CSS3 class selectors</a>
     * @profile common
     * @defaultvalue null
     */
    private ObservableList<String> styleClass = new TrackableObservableList<String>() {
        @Override
        protected void onChanged(Change<String> c) {
            impl_reapplyCSS();
        }

        @Override
        public String toString() {
            if (size() == 0) {
                return "";
            } else if (size() == 1) {
                return get(0);
            } else {
                StringBuilder buf = new StringBuilder();
                for (int i = 0; i < size(); i++) {
                    buf.append(get(i));
                    if (i + 1 < size()) {
                        buf.append(' ');
                    }
                }
                return buf.toString();
            }
        }
    };
    
    public final ObservableList<String> getStyleClass() { 
        return styleClass; 
    }

    /**
     * A string representation of the CSS style associated with this
     * specific {@code Node}. This is analogous to the "style" attribute of an
     * HTML element. Note that, like the HTML style attribute, this
     * variable contains style properties and values and not the
     * selector portion of a style rule.
     * @profile common
     * @defaultvalue empty string
     */
    private StringProperty style;

    /**
     * A string representation of the CSS style associated with this
     * specific {@code Node}. This is analogous to the "style" attribute of an
     * HTML element. Note that, like the HTML style attribute, this
     * variable contains style properties and values and not the
     * selector portion of a style rule.
     * @param value The inline CSS style to use for this {@code Node}.
     *         {@code null} is implicitly converted to an empty String. 
     * @profile common
     * @defaultvalue empty string
     */
    public final void setStyle(String value) {
        styleProperty().set(value);
    }

    // TODO: javadoc copied from property for the sole purpose of providing a return tag
    /**
     * A string representation of the CSS style associated with this
     * specific {@code Node}. This is analogous to the "style" attribute of an
     * HTML element. Note that, like the HTML style attribute, this
     * variable contains style properties and values and not the
     * selector portion of a style rule.
     * @profile common
     * @defaultvalue empty string
     * @return The inline CSS style associated with this {@code Node}.
     *         If this {@code Node} does not have an inline style,
     *         an empty String is returned.
     */
    public final String getStyle() {
        return style == null ? "" : style.get();
    }

    public final StringProperty styleProperty() {
        if (style == null) {
            style = new StringPropertyBase("") {

                @Override public void set(String value) {
                    // getStyle returns an empty string if the style property
                    // is null. To be consistent, getStyle should also return
                    // an empty string when the style property's value is null.
                    super.set((value != null) ? value : "");
                }

                @Override
                protected void invalidated() {
                     impl_reapplyCSS();
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "style";
                }
            };
        }
        return style;
    }

    /**
     * Specifies whether this {@code Node} and any subnodes should be rendered
     * as part of the scene graph. A node may be visible and yet not be shown
     * in the rendered scene if, for instance, it is off the screen or obscured
     * by another Node. Invisible nodes never receive mouse events or
     * keyboard focus, and never maintain keyboard focus when they become
     * invisible.
     *
     * @profile common
     * @defaultvalue true
     */
    private BooleanProperty visible;

    public final void setVisible(boolean value) {
        visibleProperty().set(value);
    }

    public final boolean isVisible() {
        return visible == null ? true : visible.get();
    }

    public final BooleanProperty visibleProperty() {
        if (visible == null) {
            visible = new StyleableBooleanProperty(true) {

                @Override
                protected void invalidated() {
                    impl_markDirty(DirtyBits.NODE_VISIBLE);
                    impl_geomChanged();
                    updateTreeVisible();
                    if (getParent() != null) {
                        // notify the parent of the potential change in visibility
                        // of this node, since visibility affects bounds of the
                        // parent node
                        getParent().childVisibilityChanged(Node.this);
                    }
                }

                @Override
                public StyleableProperty getStyleableProperty() {
                    return StyleableProperties.VISIBILITY;
                }
                
                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "visible";
                }
            };
        }
        return visible;
    }

    public final void setCursor(Cursor value) {
        cursorProperty().set(value);
    }

    public final Cursor getCursor() {
        return (miscProperties == null) ? DEFAULT_CURSOR
                                        : miscProperties.getCursor();
    }

    /**
     * Defines the mouse cursor for this {@code Node} and subnodes. If null,
     * then the cursor of the first parent node with a non-null cursor will be
     * used. If no Node in the scene graph defines a cursor, then the cursor
     * of the {@code Scene} will be used.
     *
     * @profile common
     * @defaultvalue null
     */
    public final ObjectProperty<Cursor> cursorProperty() {
        return getMiscProperties().cursorProperty();
    }

    /**
     * Specifies how opaque (that is, solid) the {@code Node} appears. A Node
     * with 0% opacity is fully translucent. That is, while it is still
     * {@link #visibleProperty visible} and rendered, you generally won't be able to see it. The
     * exception to this rule is when the Z{@code Node} is combined with a
     * blending mode and blend effect in which case a translucent Node may still
     * have an impact in rendering. An opacity of 50% will render the node as
     * being 50% transparent.
     * <p>
     * A {@link #visibleProperty visible} node with any opacity setting still receives mouse
     * events and can receive keyboard focus. For example, if you want to have
     * a large invisible rectangle overlay all Nodes in the scene graph in order
     * to intercept mouse events but not be visible to the user, you could
     * create a large Rectangle that had an opacity of 0%.
     * <p>
     * Opacity is specified as a value between 0 and 1. Values less than 0 are
     * treated as 0, values greater than 1 are treated as 1.
     * <p>
     * On some platforms ImageView might not support opacity variable.
     *
     * <p>
     * There is a known limitation of mixing opacity < 1.0 with a 3D Transform.
     * Opacity/Blending is essentially a 2D image operation. The result of
     * an opacity < 1.0 set on a {@link Group} node with 3D transformed children
     * will cause its children to be rendered in order without Z-buffering
     * applied between those children.
     *
     * @profile common
     * @defaultvalue 1.0
     */
    private DoubleProperty opacity;

    public final void setOpacity(double value) {
        opacityProperty().set(value);
    }
    public final double getOpacity() {
        return opacity == null ? 1 : opacity.get();
    }

    public final DoubleProperty opacityProperty() {
        if (opacity == null) {
            opacity = new StyleableDoubleProperty(1) {

                @Override
                public void invalidated() {
                    impl_markDirty(DirtyBits.NODE_OPACITY);
                }

                @Override
                public StyleableProperty getStyleableProperty() {
                    return StyleableProperties.OPACITY;
                }
                
                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "opacity";
                }
            };
        }
        return opacity;
    }

    /**
     * The {@link javafx.scene.effect.BlendMode} used to blend this individual node
     * into the scene behind it. If this node happens to be a Group then all of the
     * children will be composited individually into a temporary buffer using their
     * own blend modes and then that temporary buffer will be composited into the
     * scene using the specified blend mode.
     *
     * A value of {@code null} is treated as pass-though this means no effect on a
     * parent such as a Group and the equivalent of SRC_OVER for a single Node.
     *
     * @defaultvalue null
     * @profile common conditional effect
     */
    private javafx.beans.property.ObjectProperty<BlendMode> blendMode;

    public final void setBlendMode(BlendMode value) {
        blendModeProperty().set(value);
    }
    public final BlendMode getBlendMode() {
        return blendMode == null ? null : blendMode.get();
    }

    public final ObjectProperty<BlendMode> blendModeProperty() {
        if (blendMode == null) {
            blendMode = new StyleableObjectProperty<BlendMode>(null) {
                @Override public void invalidated() {
                    impl_markDirty(DirtyBits.NODE_BLENDMODE);
                }

                @Override
                public StyleableProperty getStyleableProperty() {
                    return StyleableProperties.BLEND_MODE;
                }
                
                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "blendMode";
                }
            };
        }
        return blendMode;
    }
    
    public final void setClip(Node value) {
        clipProperty().set(value);
    }

    public final Node getClip() {
        return (miscProperties == null) ? DEFAULT_CLIP
                                        : miscProperties.getClip();
    }

    /**
     * Specifies a {@code Node} to use to define the the clipping shape for this
     * Node. This clipping Node is not a child of this {@code Node} in the scene
     * graph sense. Rather, it is used to define the clip for this {@code Node}.
     * <p>
     * For example, you can use an {@link javafx.scene.image.ImageView} Node as
     * a mask to represent the Clip. Or you could use one of the geometric shape
     * Nodes such as {@link javafx.scene.shape.Rectangle} or
     * {@link javafx.scene.shape.Circle}. Or you could use a
     * {@link javafx.scene.text.Text} node to represent the Clip.
     * <p>
     * See the class documentation for {@link Node} for scene graph structure
     * restrictions on setting the clip. If these restrictions are violated by
     * a change to the clip variable, the change is ignored and the
     * previous value of the clip variable is restored.
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SHAPE_CLIP ConditionalFeature.SHAPE_CLIP}
     * for more information.
     * <p>
     * There is a known limitation of mixing Clip with a 3D Transform.
     * Clipping is essentially a 2D image operation. The result of
     * a Clip set on a {@link Group} node with 3D transformed children
     * will cause its children to be rendered in order without Z-buffering
     * applied between those children.
     *
     * @profile common conditional shape_clip
     * @defaultvalue null
     */
    public final ObjectProperty<Node> clipProperty() {
        return getMiscProperties().clipProperty();
    }

    private com.sun.javafx.sg.PGNode.CacheHint toPGCacheHint(CacheHint ch) {
        if (ch == CacheHint.DEFAULT) {
            return PGNode.CacheHint.DEFAULT;
        } else if (ch == CacheHint.SCALE) {
            return PGNode.CacheHint.SCALE;
        } else if (ch == CacheHint.ROTATE) {
            return PGNode.CacheHint.ROTATE;
        } else if (ch == CacheHint.SCALE_AND_ROTATE) {
            return PGNode.CacheHint.SCALE_AND_ROTATE;
        } else if (ch == CacheHint.SPEED) {
            return PGNode.CacheHint.SCALE_AND_ROTATE;
        } else if (ch == CacheHint.QUALITY) {
            return PGNode.CacheHint.DEFAULT;
        } else { // impossible
            return PGNode.CacheHint.DEFAULT;
        }
    }

    public final void setCache(boolean value) {
        cacheProperty().set(value);
    }

    public final boolean isCache() {
        return (miscProperties == null) ? DEFAULT_CACHE
                                        : miscProperties.isCache();
    }

    /**
     * A performance hint to the system to indicate that this {@code Node}
     * should be cached as a bitmap. Rendering a bitmap representation of a node
     * will be faster than rendering primitives in many cases, especially in the
     * case of primitives with effects applied (such as a blur). However, it
     * also increases memory usage. This hint indicates whether that trade-off
     * (increased memory usage for increased performance) is worthwhile. Also
     * note that on some platforms such as GPU accelerated platforms there is
     * little benefit to caching Nodes as bitmaps when blurs and other effects
     * are used since they are very fast to render on the GPU.
     *
     * The {@code cacheHint} variable provides additional options for enabling
     * more aggressive bitmap caching.
     *
     * <p>
     * Caching may be disabled for any node that has a 3D transform on itself,
     * any of its ancestors, or any of its descendants.
     *
     * @see #cacheHintProperty
     * @profile common
     * @defaultvalue false
     */
    public final BooleanProperty cacheProperty() {
        return getMiscProperties().cacheProperty();
    }

    public final void setCacheHint(CacheHint value) {
        cacheHintProperty().set(value);
    }

    public final CacheHint getCacheHint() {
        return (miscProperties == null) ? DEFAULT_CACHE_HINT
                                        : miscProperties.getCacheHint();
    }

    /**
     * Additional hint for controlling bitmap caching.
     * <p>
     * Under certain circumstances, such as animating nodes that are very
     * expensive to render, it is desirable to be able to perform
     * transformations on the node without having to regenerate the cached
     * bitmap.  An option in such cases is to perform the transforms on the
     * cached bitmap itself.
     * <p>
     * This technique can provide a dramatic improvement to animation
     * performance, though may also result in a reduction in visual quality.
     * The {@code cacheHint} variable provides a hint to the system about how
     * and when that trade-off (visual quality for animation performance) is
     * acceptable.
     * <p>
     * It is possible to enable the cacheHint only at times when your node is
     * animating.  In this way, expensive nodes can appear on screen with full
     * visual quality, yet still animate smoothly.
     * <p>
     * Example:
     * <pre><code>
        expensiveNode.setCache(true);
        expensiveNode.setCacheHint(CacheHint.QUALITY);
        ...
        // Do an animation
        expensiveNode.setCacheHint(CacheHint.SPEED);
        new Timeline(
            new KeyFrame(Duration.seconds(2),
                new KeyValue(expensiveNode.scaleXProperty(), 2.0),
                new KeyValue(expensiveNode.scaleYProperty(), 2.0),
                new KeyValue(expensiveNode.rotateProperty(), 360),
                new KeyValue(expensiveNode.cacheHintProperty(), CacheHint.QUALITY)
            )
        ).play();
     </code></pre>
     *
     * Note that {@code cacheHint} is only a hint to the system.  Depending on
     * the details of the node or the transform, this hint may be ignored.
     *
     * <p>
     * If {@code Node.cache} is false, cacheHint is ignored.
     * Caching may be disabled for any node that has a 3D transform on itself,
     * any of its ancestors, or any of its descendants.
     *
     * @see #cacheProperty
     * @profile common
     * @since JavaFX 1.3
     * @defaultvalue CacheHint.DEFAULT
     */
    public final ObjectProperty<CacheHint> cacheHintProperty() {
        return getMiscProperties().cacheHintProperty();
    }

    public final void setEffect(Effect value) {
        effectProperty().set(value);
    }

    public final Effect getEffect() {
        return (miscProperties == null) ? DEFAULT_EFFECT
                                        : miscProperties.getEffect();
    }

    /**
     * Specifies an effect to apply to this {@code Node}.
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#EFFECT ConditionalFeature.EFFECT}
     * for more information.
     *
     * <p>
     * There is a known limitation of mixing Effect with a 3D Transform. Effect is
     * essentially a 2D image operation. The result of an Effect set on
     * a {@link Group} node with 3D transformed children will cause its children
     * to be rendered in order without Z-buffering applied between those
     * children.
     *
     * @profile common conditional effect
     * @defaultvalue null
     */
    public final ObjectProperty<Effect> effectProperty() {
        return getMiscProperties().effectProperty();
    }

    public final void setDepthTest(DepthTest value) {
        depthTestProperty().set(value);
    }

    public final DepthTest getDepthTest() {
        return (miscProperties == null) ? DEFAULT_DEPTH_TEST
                                        : miscProperties.getDepthTest();
    }

    /**
     * Indicates whether depth testing is used when rendering this node.
     * If the depthTest flag is DepthTest.DISABLE, then depth testing
     * is disabled for this node.
     * If the depthTest flag is DepthTest.ENABLE, then depth testing
     * is enabled for this node.
     * If the depthTest flag is DepthTest.INHERIT, then depth testing
     * is enabled for this node if it is enabled for the parent node or the
     * parent node is null.
     * <p>
     * The depthTest flag is only used when the depthBuffer flag for
     * the {@link Scene} is true (meaning that the
     * {@link Scene} has an associated depth buffer)
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *<p>
     * See the constructor in Scene with depthBuffer as one of its input
     * arguments.
     *
     * @see javafx.scene.Scene
     * @profile common conditional scene3d
     * @defaultvalue INHERIT
     */
    public final ObjectProperty<DepthTest> depthTestProperty() {
        return getMiscProperties().depthTestProperty();
    }

    /**
     * Recompute the derived depth test flag. This flag is true
     * if the depthTest flag for this node is true and the
     * depth test flag for each ancestor node is true. It is false
     * otherwise. Equivalently, the derived depth flag is true
     * if the depthTest flag for this node is true and the derivedDepthTest
     * flag for its parent is true.
     */
    void computeDerivedDepthTest() {
        boolean newDDT;
        if (getDepthTest() == DepthTest.INHERIT) {
            if (getParent() != null) {
                newDDT = getParent().isDerivedDepthTest();
            } else {
                newDDT = true;
            }
        } else if (getDepthTest() == DepthTest.ENABLE) {
            newDDT = true;
        } else {
            newDDT = false;
        }

        if (isDerivedDepthTest() != newDDT) {
            impl_markDirty(DirtyBits.NODE_DEPTH_TEST);
            setDerivedDepthTest(newDDT);
        }
    }

    // This is the derived depthTest value to pass to PG level
    private boolean derivedDepthTest = true;

    void setDerivedDepthTest(boolean value) {
        derivedDepthTest = value;
    }

    boolean isDerivedDepthTest() {
        return derivedDepthTest;
    }

    public final void setDisable(boolean value) {
        disableProperty().set(value);
    }

    public final boolean isDisable() {
        return (miscProperties == null) ? DEFAULT_DISABLE
                                        : miscProperties.isDisable();
    }

    /**
     * Sets the individual disabled state of this {@code Node}. Setting
     * {@code disable} to true will cause this {@code Node} and any subnodes to
     * become disabled. This variable should be used only to set the disabled
     * state of a {@code Node}.  For querying the disabled state of a
     * {@code Node}, the {@link #disabledProperty disabled} variable should instead be used,
     * since it is possible that a {@code Node} was disabled as a result of an
     * ancestor being disabled even if the individual {@code disable} state on
     * this {@code Node} is {@code false}.
     *
     * @profile common
     * @defaultvalue false
     */
    public final BooleanProperty disableProperty() {
        return getMiscProperties().disableProperty();
    }

    /**************************************************************************
     *                                                                        *
     *
     *                                                                        *
     *************************************************************************/
    /**
     * Defines how the picking computation is done for this node when
     * triggered by a {@code MouseEvent} or a {@code contains} function call.
     *
     * If {@code pickOnBounds} is true, then picking is computed by
     * intersecting with the bounds of this node, else picking is computed
     * by intersecting with the geometric shape of this node.
     *
     * @defaultvalue false
     * @profile common
     * @since JavaFX 1.3
     */
    private BooleanProperty pickOnBounds;

    public final void setPickOnBounds(boolean value) {
        pickOnBoundsProperty().set(value);
    }

    public final boolean isPickOnBounds() {
        return pickOnBounds == null ? false : pickOnBounds.get();
    }

    public final BooleanProperty pickOnBoundsProperty() {
        if (pickOnBounds == null) {
            pickOnBounds = new SimpleBooleanProperty(this, "pickOnBounds");
        }
        return pickOnBounds;
    }

    /**
     * Indicates whether or not this {@code Node} is disabled.  A {@code Node}
     * will become disabled if {@link #disableProperty disable} is set to {@code true} on either
     * itself or one of its ancestors in the scene graph.
     * <p>
     * A disabled {@code Node} should render itself differently to indicate its
     * disabled state to the user.
     * Such disabled rendering is dependent on the implementation of the
     * {@code Node}. The shape classes contained in {@code javafx.scene.shape}
     * do not implement such rendering by default, therefore applications using
     * shapes for handling input must implement appropriate disabled rendering
     * themselves. The user-interface controls defined in
     * {@code javafx.scene.control} will implement disabled-sensitive rendering,
     * however.
     * <p>
     * A disabled {@code Node} does not receive mouse or key events.
     *
     * @profile common
     * @defaultvalue false
     */
    private ReadOnlyBooleanWrapper disabled;

    protected final void setDisabled(boolean value) {
        disabledPropertyImpl().set(value);
    }

    public final boolean isDisabled() {
        return disabled == null ? false : disabled.get();
    }

    public final ReadOnlyBooleanProperty disabledProperty() {
        return disabledPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyBooleanWrapper disabledPropertyImpl() {
        if (disabled == null) {
            disabled = new ReadOnlyBooleanWrapper() {

                @Override
                protected void invalidated() {
                    impl_pseudoClassStateChanged("disabled");
                    updateCanReceiveFocus();
                    focusSetDirty(getScene());
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "disabled";
                }
            };
        }
        return disabled;
    }

    private void updateDisabled() {
        setDisabled(isDisable() || (getParent() != null && getParent().isDisabled()));
    }

    /**
     * Finds this {@code Node}, or the first sub-node, based on the given CSS selector.
     * If this node is a {@code Parent}, then this function will traverse down
     * into the branch until it finds a match. If more than one sub-node matches the
     * specified selector, this function returns the first of them.
     * <p>
     *     For example, if a Node is given the id of "myId", then the lookup method can
     *     be used to find this node as follows: <code>scene.lookup("#myId");</code>.
     * </p>
     *
     * @profile common
     * @param selector The css selector of the node to find
     * @return The first node, starting from this {@code Node}, which matches
     *         the CSS {@code selector}, null if none is found.
     */
    public Node lookup(String selector) {
        if (selector == null) return null;
        Selector s = Selector.createSelector(selector);
        return s != null && s.applies(this) ? this : null;
    }

    /**
     * Finds all {@code Node}s, including this one and any children, which match
     * the given CSS selector. If no matches are found, an empty unmodifiable set is
     * returned. The set is explicitly unordered.
     *
     * @profile common
     * @param selector The css selector of the nodes to find
     * @return All nodes, starting from and including this {@code Node}, which match
     *         the CSS {@code selector}. The returned set is always unordered and
     *         unmodifiable, and never null.
     */
    public Set<Node> lookupAll(String selector) {
        final Selector s = Selector.createSelector(selector);
        final Set<Node> empty = Collections.emptySet();
        if (s == null) return empty;
        List<Node> results = lookupAll(s, null);
        return results == null ? empty : new UnmodifiableListSet<Node>(results);
    }

    /**
     * Used by Node and Parent for traversing the tree and adding all nodes which
     * match the given selector.
     *
     * @param selector The Selector. This will never be null.
     * @param results The results. This will never be null.
     */
    List<Node> lookupAll(Selector selector, List<Node> results) {
        if (selector.applies(this)) {
            // Lazily create the set to reduce some trash.
            if (results == null) {
                results = new LinkedList<Node>();
            }
            results.add(this);
        }
        return results;
    }

    /**
     * Moves this {@code Node} to the back of its sibling nodes in terms of
     * z-order.  This is accomplished by moving this {@code Node} to the
     * first position in its parent's {@code content} ObservableList.
     * This function has no effect if this {@code Node} is not part of a group.
     *
     * @profile common
     */
    public void toBack() {
        if (getParent() != null) {
            getParent().impl_toBack(this);
        }
    }

    /**
     * Moves this {@code Node} to the front of its sibling nodes in terms of
     * z-order.  This is accomplished by moving this {@code Node} to the
     * last position in its parent's {@code content} ObservableList.
     * This function has no effect if this {@code Node} is not part of a group.
     *
     * @profile common
     */
    public void toFront() {
        if (getParent() != null) {
            getParent().impl_toFront(this);
        }
    }

    /* ************************************************************************
     *                                                                        *
     *
     *                                                                        *
     *************************************************************************/

    public final void setOnDragEntered(
            EventHandler<? super DragEvent> value) {
        onDragEnteredProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragEntered() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnDragEntered();
    }

    /**
     * Defines a function to be called when drag gesture
     * enters this {@code Node}.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>>
            onDragEnteredProperty() {
        return getEventHandlerProperties().onDragEnteredProperty();
    }

    public final void setOnDragExited(
            EventHandler<? super DragEvent> value) {
        onDragExitedProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragExited() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnDragExited();
    }

    /**
     * Defines a function to be called when drag gesture
     * exits this {@code Node}.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>>
            onDragExitedProperty() {
        return getEventHandlerProperties().onDragExitedProperty();
    }

    public final void setOnDragOver(
            EventHandler<? super DragEvent> value) {
        onDragOverProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragOver() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnDragOver();
    }

    /**
     * Defines a function to be called when drag gesture progresses within
     * this {@code Node}.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>>
            onDragOverProperty() {
        return getEventHandlerProperties().onDragOverProperty();
    }

    // Do we want DRAG_TRANSFER_MODE_CHANGED event?
//    public final void setOnDragTransferModeChanged(
//            EventHandler<? super DragEvent> value) {
//        onDragTransferModeChangedProperty().set(value);
//    }
//
//    public final EventHandler<? super DragEvent> getOnDragTransferModeChanged() {
//        return (eventHandlerProperties == null)
//                ? null : eventHandlerProperties.getOnDragTransferModeChanged();
//    }
//
//    /**
//     * Defines a function to be called this {@code Node} if it is a potential
//     * drag-and-drop target when the user takes action to change the intended
//     * {@code TransferMode}.
//     * The user can change the intended {@link TransferMode} by holding down
//     * or releasing key modifiers.
//     */
//    public final ObjectProperty<EventHandler<? super DragEvent>>
//            onDragTransferModeChangedProperty() {
//        return getEventHandlerProperties().onDragTransferModeChangedProperty();
//    }

    public final void setOnDragDropped(
            EventHandler<? super DragEvent> value) {
        onDragDroppedProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragDropped() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnDragDropped();
    }

    /**
     * Defines a function to be called when the mouse button is released
     * on this {@code Node} during drag and drop gesture. Transfer of data from
     * the {@link DragEvent}'s {@link DragEvent#dragboard dragboard} should
     * happen in this function.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>>
            onDragDroppedProperty() {
        return getEventHandlerProperties().onDragDroppedProperty();
    }

    public final void setOnDragDone(
            EventHandler<? super DragEvent> value) {
        onDragDoneProperty().set(value);
    }

    public final EventHandler<? super DragEvent> getOnDragDone() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnDragDone();
    }

    /**
     * Defines a function to be called when this {@code Node} is a
     * drag and drop gesture source after its data has
     * been dropped on a drop target. The {@code transferMode} of the
     * event shows what just happened at the drop target.
     * If {@code transferMode} has the value {@code MOVE}, then the source can
     * clear out its data. Clearing the source's data gives the appropriate
     * appearance to a user that the data has been moved by the drag and drop
     * gesture. A {@code transferMode} that has the value {@code NONE}
     * indicates that no data was transferred during the drag and drop gesture.
     */
    public final ObjectProperty<EventHandler<? super DragEvent>>
            onDragDoneProperty() {
        return getEventHandlerProperties().onDragDoneProperty();
    }

    /**
     * Confirms a potential drag and drop gesture that is recognized over this
     * {@code Node}.
     * Can be called only from a DRAG_DETECTED event handler. The returned
     * {@link Dragboard} is used to transfer data during
     * the drag and drop gesture. Placing this {@code Node}'s data on the
     * {@link Dragboard} also identifies this {@code Node} as the source of
     * the drag and drop gesture.
     * More detail about drag and drop gestures is described in the overivew
     * of {@link DragEvent}.
     *
     * @see DragEvent
     * @param transferModes The supported {@code TransferMode}(s) of this {@code Node}
     * @return A {@code Dragboard} to place this {@code Node}'s data on
     * @throws IllegalStateException if drag and drop cannot be started at this
     * moment (it's called outside of {@code DRAG_DETECTED} event handling or
     * this node is not in scene).
     */
    public Dragboard startDragAndDrop(TransferMode... transferModes) {
        if (getScene() != null) {
            return getScene().impl_startDragAndDrop(this, transferModes);
        }
        
        throw new IllegalStateException("Cannot start drag and drop on node "
                + "that is not in scene");
    }

    /**
     * Starts a full press-drag-release gesture with this node as gesture
     * source. This method can be called only from a {@code DRAG_DETECTED} mouse
     * event handler. More detail about dragging gestures can be found
     * in the overview of {@link MouseEvent} and {@link MouseDragEvent}.
     *
     * @see MosueEvent
     * @see MouseDragEvent
     * @throws IllegalStateException if the full press-drag-release gesture
     * cannot be started at this moment (it's called outside of
     * {@code DRAG_DETECTED} event handling or this node is not in scene).
     */
    public void startFullDrag() {
        if (getScene() != null) {
            getScene().impl_startFullDrag(this);
            return;
        }

        throw new IllegalStateException("Cannot start full drag on node "
                + "that is not in scene");
    }

    ////////////////////////////
    //  Private Implementation
    ////////////////////////////

    /**
     * If this Node is being used as the clip of another Node, that other node
     * is referred to as the clipParent. If the boundsInParent of this Node
     * changes, it must update the clipParent's bounds as well.
     */
    private Node clipParent;
    // Use a getter function instead of giving clipParent package access,
    // so that clipParent doesn't get turned into a Location.
    Node impl_getClipParent() {
        return clipParent;
    }

    /**
     * Determines whether this node is connected anywhere in the scene graph.
     */
    boolean isConnected() {
        // don't need to check scene, because if scene is non-null
        // parent must also be non-null
        return getParent() != null || clipParent != null;
    }

    /**
     * Tests whether creating a parent-child relationship between these
     * nodes would cause a cycle. The parent relationship includes not only
     * the "real" parent (child of Group) but also the clipParent.
     */
    boolean wouldCreateCycle(Node parent, Node child) {
        if (child != null && child.getClip() == null && (!(child instanceof Parent))) {
            return false;
    }

        Node n = parent;
        while (n != child) {
            if (n.getParent() != null) {
                n = n.getParent();
            } else if (n.clipParent != null) {
                n = n.clipParent;
            } else {
                return false;
    }
        }
        return true;
    }

    /**
     * The peer node created by the graphics Toolkit/Pipeline implementation
     */
    private PGNode peer;

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @SuppressWarnings("CallToPrintStackTrace")
    public PGNode impl_getPGNode() {
        if (Utils.assertionEnabled()) {
            // Assertion checking code
            if (!Scene.impl_isPGAccessAllowed()) {
                java.lang.System.err.println();
                java.lang.System.err.println("*** unexpected PG access");
                java.lang.Thread.dumpStack();
            }
        }

        if (peer == null) {
            //if (PerformanceTracker.isLoggingEnabled()) {
            //    PerformanceTracker.logEvent("Creating PGNode for [{this}, id=\"{id}\"]");
            //}
            peer = impl_createPGNode();
            //if (PerformanceTracker.isLoggingEnabled()) {
            //    PerformanceTracker.logEvent("PGNode created");
            //}
        }
        return peer;
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected abstract PGNode impl_createPGNode();

    /***************************************************************************
     *                                                                         *
     *                              Initialization                             *
     *                                                                         *
     *  To Note limit the number of bounds computations and improve startup    *
     *  performance.                                                           *
     *                                                                         *
     **************************************************************************/

    /**
     * Creates a new instance of Node.
     */
    protected Node() {
        //if (PerformanceTracker.isLoggingEnabled()) {
        //    PerformanceTracker.logEvent("Node.init for [{this}, id=\"{id}\"]");
        //}

        setDirty();
        updateTreeVisible();
        //if (PerformanceTracker.isLoggingEnabled()) {
        //    PerformanceTracker.logEvent("Node.postinit " +
        //                                "for [{this}, id=\"{id}\"] finished");
        //}
    }

    /***************************************************************************
     *                                                                         *
     * Layout related APIs.                                                    *
     *                                                                         *
     **************************************************************************/
    /**
     * Defines whether or not this node's layout will be managed by it's parent.
     * If the node is managed, it's parent will factor the node's geometry
     * into its own preferred size and layoutBounds calculations and will lay it
     * out during the scene's layout pass.  If a managed node's layoutBounds
     * changes, it will automatically trigger relayout up the scene-graph
     * to the nearest layout root (which is typically the scene's root node).
     * <p>
     * If the node is unmanaged, its parent will ignore the child in both preferred
     * size computations and layout.   Changes in layoutBounds will not trigger
     * relayout above it.   If an unmanaged node is of type {@link javafx.scene.Parent Parent},
     * it will act as a "layout root", meaning that calls to requestLayout()
     * beneath it will cause only the branch rooted by the node to be relayed out,
     * thereby isolating layout changes to that root and below.  It's the application's
     * responsibility to set the size and position of an unmanaged node.
     * <p>
     * By default all nodes are managed.
     * <p>
     * @see #isResizable()
     * @see #getLayoutBounds()
     *
     */
    private BooleanProperty managed;

    public final void setManaged(boolean value) {
        managedProperty().set(value);
    }

    public final boolean isManaged() {
        return managed == null ? true : managed.get();
    }

    public final BooleanProperty managedProperty() {
        if (managed == null) {
            managed = new BooleanPropertyBase(true) {

                @Override
                protected void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "managed";
                }

            };
        }
        return managed;
    }

    /**
     * Defines the x coordinate of the translation that is added to this {@code Node}'s
     * transform for the purpose of layout.  The value should be computed as the
     * offset required to adjust the position of the node from its current
     * {@code layoutBounds minX} position (which might not be 0) to the desired location.
     *
     * <p>For example, if {@code textnode} should be positioned at {@code finalX}
     * <code><pre>
     *     textnode.setLayoutX(finalX - textnode.getLayoutBounds().getMinX();
     * </pre></code>
     * <p>
     * Failure to subtract {@code layoutBounds minX} may result in misplacement
     * of the node.  The {@code relocate(x,y)} method will automatically do the
     * correct computation and should generally be used over setting layoutX directly.
     * <p>
     * The node's final translation will be computed as {@code layoutX} + {@link #translateXProperty translateX},
     * where {@code layoutX} establishes the node's stable position
     * and {@code translateX} optionally makes dynamic adjustments to that
     * position.
     * <p>
     * If the node is managed and has a {@link javafx.scene.layout.Region}
     * as its parent, then the layout region will set {@code layoutX} according to its
     * own layout policy.   If the node is unmanaged or parented by a {@link Group},
     * then the application may set {@code layoutX} directly to position it.
     * <p>
     * @see #relocate(double, double) 
     *
     */
    private DoubleProperty layoutX;

    public final void setLayoutX(double value) {
        layoutXProperty().set(value);
    }

    public final double getLayoutX() {
        return layoutX == null ? 0.0 : layoutX.get();
    }

    public final DoubleProperty layoutXProperty() {
        if (layoutX == null) {
            layoutX = new DoublePropertyBase(0.0) {

                @Override
                protected void invalidated() {
                    impl_transformsChanged();
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "layoutX";
                }
            };
        }
        return layoutX;
    }

    /**
     * Defines the y coordinate of the translation that is added to this {@code Node}'s
     * transform for the purpose of layout.  The value should be computed as the
     * offset required to adjust the position of the node from its current
     * {@code layoutBounds minY} position (which might not be 0) to the desired location.
     *
     * <p>For example, if {@code textnode} should be positioned at {@code finalY}
     * <code><pre>
     *     textnode.setLayoutY(finalY - textnode.getLayoutBounds().getMinY());
     * </pre></code>
     * <p>
     * Failure to subtract {@code layoutBounds minY} may result in misplacement
     * of the node.  The {@code relocate(x,y)} method will automatically do the
     * correct computation and should generally be used over setting layoutY directly.
     * <p>
     * The node's final translation will be computed as {@code layoutY} + {@link #translateYProperty translateY},
     * where {@code layoutY} establishes the node's stable position
     * and {@code translateY} optionally makes dynamic adjustments to that
     * position.
     * <p>
     * If the node is managed and has a {@link javafx.scene.layout.Region}
     * as its parent, then the region will set {@code layoutY} according to its
     * own layout policy.   If the node is unmanaged or parented by a {@link Group},
     * then the application may set {@code layoutY} directly to position it.
     *
     * @see #relocate(double, double) 
     */
    private DoubleProperty layoutY;

    public final void setLayoutY(double value) {
        layoutYProperty().set(value);
    }

    public final double getLayoutY() {
        return layoutY == null ? 0.0 : layoutY.get();
    }

    public final DoubleProperty layoutYProperty() {
        if (layoutY == null) {
            layoutY = new DoublePropertyBase(0.0) {

                @Override
                protected void invalidated() {
                    impl_transformsChanged();
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "layoutY";
                }

            };
        }
        return layoutY;
    }

    /**
     * Sets the node's layoutX and layoutY translation properties in order to
     * relocate this node to the x,y location in the parent.
     * <p>
     * This method does not alter translateX or translateY, which if also set
     * will be added to layoutX and layoutY, adjusting the final location by
     * corresponding amounts.
     *
     * @param x the target x coordinate location
     * @param y the target y coordinate location
     */
    public void relocate(double x, double y) {
        setLayoutX(x - getLayoutBounds().getMinX());
        setLayoutY(y - getLayoutBounds().getMinY());

        PlatformLogger logger = Logging.getLayoutLogger();
        if (logger.isLoggable(PlatformLogger.FINER)) {
            logger.finer(this.toString()+" moved to ("+x+","+y+")");
        }
    }

    /**
     * Indicates whether this node is a type which can be resized by its parent.
     * If this method returns true, then the parent will resize the node (ideally
     * within its size range) by calling node.resize(width,height) during the
     * layout pass.  All Regions, Controls, and WebView are resizable classes
     * which depend on their parents resizing them during layout once all sizing
     * and CSS styling information has been applied.
     * <p>
     * If this method returns false, then the parent cannot resize it during
     * layout (resize() is a no-op) and it should return its layoutBounds for
     * minimum, preferred, and maximum sizes.  Group, Text, and all Shapes are not
     * resizable and hence depend on the application to establish their sizing
     * by setting appropriate properties (e.g.  width/height for Rectangle,
     * text on Text, and so on).  Non-resizable nodes may still be relocated
     * during layout.
     *
     * @see #getContentBias() 
     * @see #minWidth(double)
     * @see #minHeight(double)
     * @see #prefWidth(double)
     * @see #prefHeight(double)
     * @see #maxWidth(double)
     * @see #maxHeight(double)
     * @see #resize(double, double)
     * @see #getLayoutBounds()
     *
     * @return whether or not this node type can be resized by its parent during layout
     */
    public boolean isResizable() {
        return false;
    }

    /**
     * Returns the orientation of a node's resizing bias for layout purposes.
     * If the node type has no bias, returns null.  If the node is resizable and
     * it's height depends on its width, returns HORIZONTAL, else if its width
     * depends on its height, returns VERTICAL.
     * <p>
     * Resizable subclasses should override this method to return an 
     * appropriate value.
     *
     * @see #isResizable()
     * @see #minWidth(double)
     * @see #minHeight(double)
     * @see #prefWidth(double)
     * @see #prefHeight(double)
     * @see #maxWidth(double)
     * @see #maxHeight(double)
     *
     * @return orientation of width/height dependency or null if there is none
     */
    public Orientation getContentBias() {
        return null;
    }

    /**
     * Returns the node's minimum width for use in layout calculations.
     * If the node is resizable, its parent should not resize its width any
     * smaller than this value.  If the node is not resizable, returns its
     * layoutBounds width.
     * <p>
     * Layout code which calls this method should first check the content-bias
     * of the node.  If the node has a vertical content-bias, then callers
     * should pass in a height value that the minimum width should be based on.
     * If the node has either a horizontal or null content-bias, then the caller
     * should pass in -1.
     * <p>
     * Node subclasses with a vertical content-bias should honor the height
     * parameter whether -1 or a positive value.   All other subclasses may ignore
     * the height parameter (which will likely be -1).
     * <p>
     * @see #isResizable()
     * @see #getContentBias()
     * 
     * @param height the height that should be used if minimum width depends on it
     * @return the minimum width that the node should be resized to during layout
     *
     */
    public double minWidth(double height) {
        return prefWidth(height);
    }

    /**
     * Returns the node's minimum height for use in layout calculations.
     * If the node is resizable, its parent should not resize its height any
     * smaller than this value.  If the node is not resizable, returns its
     * layoutBounds height.
     * <p>
     * Layout code which calls this method should first check the content-bias
     * of the node.  If the node has a horizontal content-bias, then callers
     * should pass in a width value that the minimum height should be based on.
     * If the node has either a vertical or null content-bias, then the caller
     * should pass in -1.
     * <p>
     * Node subclasses with a horizontal content-bias should honor the width
     * parameter whether -1 or a positive value.   All other subclasses may ignore
     * the width parameter (which will likely be -1).
     * <p>
     * @see #isResizable()
     * @see #getContentBias()
     *
     * @param width the width that should be used if minimum height depends on it
     * @return the minimum height that the node should be resized to during layout
     *
     */
    public double minHeight(double width) {
        return prefHeight(width);
    }


    /**
     * Returns the node's preferred width for use in layout calculations.
     * If the node is resizable, its parent should treat this value as the
     * node's ideal width within its range.  If the node is not resizable,
     * just returns its layoutBounds width, which should be treated as the rigid
     * width of the node.
     * <p>
     * Layout code which calls this method should first check the content-bias
     * of the node.  If the node has a vertical content-bias, then callers
     * should pass in a height value that the preferred width should be based on.
     * If the node has either a horizontal or null content-bias, then the caller
     * should pass in -1.
     * <p>
     * Node subclasses with a vertical content-bias should honor the height
     * parameter whether -1 or a positive value.   All other subclasses may ignore
     * the height parameter (which will likely be -1).
     * <p>
     * @see #isResizable() 
     * @see #getContentBias()
     * @see #autosize()
     *
     * @param height the height that should be used if preferred width depends on it
     * @return the preferred width that the node should be resized to during layout
     */
    public double prefWidth(double height) {
        return getLayoutBounds().getWidth();
    }

    /**
     * Returns the node's preferred height for use in layout calculations.
     * If the node is resizable, its parent should treat this value as the
     * node's ideal height within its range.  If the node is not resizable,
     * just returns its layoutBounds height, which should be treated as the rigid
     * height of the node.
     * <p>
     * Layout code which calls this method should first check the content-bias
     * of the node.  If the node has a horizontal content-bias, then callers
     * should pass in a width value that the preferred height should be based on.
     * If the node has either a vertical or null content-bias, then the caller
     * should pass in -1.
     * <p>
     * Node subclasses with a horizontal content-bias should honor the height
     * parameter whether -1 or a positive value.   All other subclasses may ignore
     * the height parameter (which will likely be -1).
     * <p>
     * @see #getContentBias()
     * @see #autosize()
     *
     * @param width the width that should be used if preferred height depends on it
     * @return the preferred height that the node should be resized to during layout
     */
    public double prefHeight(double width) {
        return getLayoutBounds().getHeight();
    }

    /**
     * Returns the node's maximum width for use in layout calculations.
     * If the node is resizable, its parent should not resize its width any
     * larger than this value.  A value of Double.MAX_VALUE indicates the
     * parent may expand the node's width beyond its preferred without limits.
     * <p>
     * If the node is not resizable, returns its layoutBounds width.
     * <p>
     * Layout code which calls this method should first check the content-bias
     * of the node.  If the node has a vertical content-bias, then callers
     * should pass in a height value that the maximum width should be based on.
     * If the node has either a horizontal or null content-bias, then the caller
     * should pass in -1.
     * <p>
     * Node subclasses with a vertical content-bias should honor the height
     * parameter whether -1 or a positive value.   All other subclasses may ignore
     * the height parameter (which will likely be -1).
     * <p>
     * @see #isResizable()
     * @see #getContentBias()
     *
     * @param height the height that should be used if maximum width depends on it
     * @return the maximum width that the node should be resized to during layout
     *
     */
    public double maxWidth(double height) {
        return prefWidth(height);
    }

    /**
     * Returns the node's maximum height for use in layout calculations.
     * If the node is resizable, its parent should not resize its height any
     * larger than this value.  A value of Double.MAX_VALUE indicates the
     * parent may expand the node's height beyond its preferred without limits.
     * <p>
     * If the node is not resizable, returns its layoutBounds height.
     * <p>
     * Layout code which calls this method should first check the content-bias
     * of the node.  If the node has a horizontal content-bias, then callers
     * should pass in a width value that the maximum height should be based on.
     * If the node has either a vertical or null content-bias, then the caller
     * should pass in -1.
     * <p>
     * Node subclasses with a horizontal content-bias should honor the width
     * parameter whether -1 or a positive value.   All other subclasses may ignore
     * the width parameter (which will likely be -1).
     * <p>
     * @see #isResizable()
     * @see #getContentBias()
     *
     * @param width the width that should be used if maximum height depends on it
     * @return the maximum height that the node should be resized to during layout
     *
     */
    public double maxHeight(double width) {
        return prefHeight(width);
    }

    /**
     * If the node is resizable, will set its layout bounds to the specified
     * width and height.   If the node is not resizable, this method is a no-op.
     * <p>
     * This method should generally only be called by parent nodes from their
     * layoutChildren() methods.   All Parent classes will automatically resize
     * resizable children, so resizing done directly by the application will be
     * overridden by the node's parent, unless the child is unmanaged.
     * <p>
     * Parents are responsible for ensuring the width and height values fall
     * within the resizable node's preferred range.  The autosize() method may
     * be used if the parent just needs to resize the node to its preferred size.
     * 
     * <p>
     * @see #isResizable()
     * @see #getContentBias()
     * @see #autosize()
     * @see #minWidth(double)
     * @see #minHeight(double)
     * @see #prefWidth(double)
     * @see #prefHeight(double)
     * @see #maxWidth(double)
     * @see #maxHeight(double)
     * @see #getLayoutBounds()
     *
     * @param width the target layout bounds width
     * @param height the target layout bounds height
     */
    public void resize(double width, double height) {
    }

    /**
     * If the node is resizable, will set its layout bounds to its current preferred
     * width and height. If the node is not resizable, this method is a no-op.
     * <p>
     * This method automatically queries the node's content-bias and if it's
     * horizontal, will pass in the node's preferred width to get the preferred
     * height; if vertical, will pass in the node's preferred height to get the width,
     * and if null, will compute the preferred width/height independently.
     * <p>
     *
     * @see #isResizable()
     * @see #getContentBias()
     *
     */
    public final void autosize() {
        if (isResizable()) {
            Orientation contentBias = getContentBias();
            double w, h;
            if (contentBias == null) {
                w = boundedSize(prefWidth(-1), minWidth(-1), maxWidth(-1));
                h = boundedSize(prefHeight(-1), minHeight(-1), maxHeight(-1));

            } else if (contentBias == Orientation.HORIZONTAL) {
                w = boundedSize(prefWidth(-1), minWidth(-1), maxWidth(-1));
                h = prefHeight(w);

            } else { // bias == VERTICAL
                h = boundedSize(prefHeight(-1), minHeight(-1), maxHeight(-1));
                w = prefWidth(h);
            }
            resize(w,h);
        }
    }

    double boundedSize(double value, double min, double max) {
        // if max < value, return max
        // if min > value, return min
        // if min > max, return min
        return Math.min(Math.max(value, min), Math.max(min,max));
    }

    /**
     * If the node is resizable, will set its layout bounds to the specified
     * width and height.   If the node is not resizable, the resize step is skipped.
     * <p>
     * Once the node has been resized (if resizable) then sets the node's layoutX
     * and layoutY translation properties in order to relocate it to x,y in the
     * parent's coordinate space.
     * <p>
     * This method should generally only be called by parent nodes from their
     * layoutChildren() methods.   All Parent classes will automatically resize
     * resizable children, so resizing done directly by the application will be
     * overridden by the node's parent, unless the child is unmanaged.
     * <p>
     * Parents are responsible for ensuring the width and height values fall
     * within the resizable node's preferred range.  The autosize() and relocate()
     * methods may be used if the parent just needs to resize the node to its
     * preferred size and reposition it.
     * <p>
     * @see #isResizable()
     * @see #getContentBias()
     * @see #autosize()
     * @see #minWidth(double)
     * @see #minHeight(double)
     * @see #prefWidth(double)
     * @see #prefHeight(double)
     * @see #maxWidth(double)
     * @see #maxHeight(double)
     *
     * @param x the target x coordinate location
     * @param y the target y coordinate location
     * @param width the target layout bounds width
     * @param height the target layout bounds height
     *
     */
    public void resizeRelocate(double x, double y, double width, double height) {
        resize(width, height);
        relocate(x,y);
    }

    /**
     * The 'alphabetic' (or 'roman') baseline offset from the node's layoutBounds.minY location
     * that should be used when this node is being vertically aligned by baseline with
     * other nodes.  By default this returns the layoutBounds height of the node.  Subclasses
     * which contain text should override this method to return their actual text baseline offset.
     *
     * @return offset of text baseline from layoutBounds.minY
     */
    public double getBaselineOffset() {
        return getLayoutBounds().getHeight();
    }

    /* *************************************************************************
     *                                                                         *
     * Bounds related APIs                                                     *
     *                                                                         *
     **************************************************************************/

    public final Bounds getBoundsInParent() {
        return boundsInParentProperty().get();
    }

    /**
     * The rectangular bounds of this {@code Node} which include its transforms.
     * {@code boundsInParent} is calculated by
     * taking the local bounds (defined by {@link #boundsInLocalProperty boundsInLocal}) and applying
     * the transform created by setting the following additional variables
     * <ol>
     * <li>{@link #getTransforms transforms} ObservableList</li>
     * <li>{@link #scaleXProperty scaleX}, {@link #scaleYProperty scaleY}</li>
     * <li>{@link #rotateProperty rotate}</li>
     * <li>{@link #layoutXProperty layoutX}, {@link #layoutYProperty layoutY}</li>
     * <li>{@link #translateXProperty translateX}, {@link #translateYProperty translateY}</li>
     * </ol>
     * <p>
     * The resulting bounds will be conceptually in the coordinate space of the
     * {@code Node}'s parent, however the node need not have a parent to calculate
     * these bounds.
     * <p>
     * Note that this method does not take the node's visibility into account;
     * the computation is based on the geometry of this {@code Node} only.
     * <p>
     * This property will always have a non-null value.
     * <p>
     * Note that boundsInParent is automatically recomputed whenever the
     * geometry of a node changes, or when any of the following the change:
     * transforms ObservableList, translateX, translateY, layoutX, layoutY,
     * scaleX, scaleY, or the rotate variable. For this reason, it is an error
     * to bind any of these values in a node to an expression that depends upon
     * this variable. For example, the x or y variables of a shape, or
     * translateX, translateY should never be bound to boundsInParent
     * for the purpose of positioning the node.
     *
     * @profile common
     */
    public final ReadOnlyObjectProperty<Bounds> boundsInParentProperty() {
        return getMiscProperties().boundsInParentProperty();
    }

    private void invalidateBoundsInParent() {
        if (miscProperties != null) {
            miscProperties.invalidateBoundsInParent();
        }
    }

    public final Bounds getBoundsInLocal() {
        return boundsInLocalProperty().get();
    }

    /**
     * The rectangular bounds of this {@code Node} in the node's
     * untransformed local coordinate space.  For nodes that extend
     * {@link javafx.scene.shape.Shape}, the local bounds will also include
     * space required for a non-zero stroke that may fall outside the shape's
     * geometry that is defined by position and size attributes.
     * The local bounds will also include any clipping set with {@link #clipProperty clip}
     * as well as effects set with {@link #effectProperty effect}.
     *
     * <p>
     * Note that this method does not take the node's visibility into account;
     * the computation is based on the geometry of this {@code Node} only.
     * <p>
     * This property will always have a non-null value.
     * <p>
     * Note that boundsInLocal is automatically recomputed whenever the
     * geometry of a node changes. For this reason, it is an error to bind any
     * of these values in a node to an expression that depends upon this variable.
     * For example, the x or y variables of a shape should never be bound
     * to boundsInLocal for the purpose of positioning the node.
     *
     * @profile common
     */
    public final ReadOnlyObjectProperty<Bounds> boundsInLocalProperty() {
        return getMiscProperties().boundsInLocalProperty();
    }

    private void invalidateBoundsInLocal() {
        if (miscProperties != null) {
            miscProperties.invalidateBoundsInLocal();
        }
    }

    /**
     * The rectangular bounds that should be used for layout calculations for
     * this node. {@code layoutBounds} may differ from the visual bounds
     * of the node and is computed differently depending on the node type.
     * <p>
     * If the node type is resizable ({@link javafx.scene.layout.Region Region},
     * {@link javafx.scene.control.Control Control}, or {@link javafx.scene.web.WebView WebView})
     * then the layoutBounds will always be {@code 0,0 width x height}.
     * If the node type is not resizable ({@link javafx.scene.shape.Shape Shape},
     * {@link javafx.scene.text.Text Text}, or {@link Group}), then the layoutBounds
     * are computed based on the node's geometric properties and does not include the
     * node's clip, effect, or transforms.  See individual class documentation
     * for details.
     * <p>
     * Note that the {@link #layoutXProperty layoutX}, {@link #layoutYProperty layoutY}, {@link #translateXProperty translateX}, and
     * {@link #translateYProperty translateY} variables are not included in the layoutBounds.
     * This is important because layout code must first determine the current
     * size and location of the node (using layoutBounds) and then set
     * {@code layoutX} and {@code layoutY} to adjust the translation of the
     * node so that it will have the desired layout position.
     * <p>
     * Because the computation of layoutBounds is often tied to a node's
     * geometric variables, it is an error to bind any such variables to an
     * expression that depends upon {@code layoutBounds}. For example, the
     * x or y variables of a shape should never be bound to layoutBounds
     * for the purpose of positioning the node.
     * <p>
     * The layoutBounds will never be null.
     *
     */
    private LazyBoundsProperty layoutBounds = new LazyBoundsProperty() {
        @Override
        protected Bounds computeBounds() {
            return impl_computeLayoutBounds();
        }

        @Override
        public Object getBean() {
            return Node.this;
        }

        @Override
        public String getName() {
            return "layoutBounds";
        }
    };

    public final Bounds getLayoutBounds() {
        return layoutBoundsProperty().get();
    }

    public final ReadOnlyObjectProperty<Bounds> layoutBoundsProperty() {
        return layoutBounds;
    }

    /*
     *                  Bounds And Transforms Computation
     *
     *  This section of the code is responsible for computing and caching
     *  various bounds and transforms. For optimal performance and minimal
     *  recomputation of bounds (which can be quite expensive), we cache
     *  values on two different levels. We expose two public immutable
     *  Bounds boundsInParent objects and boundsInLocal. Because they are
     *  immutable and because they may change quite frequently (especially
     *  in the case of a Parent who's children are animated), it is
     *  important that the system does not rely on these variables, because
     *  doing so would produce a large amount of garbage. Rather, these
     *  variables are provided solely for the convenience of application
     *  developers and, being lazily bound, should generally be created at
     *  most once per frame.
     *
     *  The second level of caching are within local Bounds2D variables.
     *  These variables, txBounds and geomBounds, are mutable and as such
     *  can be cached and updated as frequently as necessary without creating
     *  excessive garbage. However, since the computation of bounds is still
     *  expensive, it is desirable to cache both the geometric bounds and
     *  the "complete" transformed bounds (essentially, boundsInParent).
     *  Cached txBounds is particularly useful when computing the geometric
     *  bounds of a Parent since it would not require complete or partial
     *  recomputation of each child.
     *
     *  Finally, we cache the complete transform for this node which converts
     *  its coord system from local to parent coords. This is useful both for
     *  minimizing bounds recomputations in the case of the geometry having
     *  changed but the transform not having changed, and also because the tx
     *  is required for several different computations (for example, it must
     *  be computed once during state synchronization with the PG peer, and
     *  must also be computed when the pivot point changes, and also when
     *  deriving the txBounds of the Node).
     *
     *  As with any caching system, a subtle and non-trivial amount of code
     *  is devoted to invalidating the bounds / transforms at appropriate
     *  times and in appropriate places to make sure bounds / transforms
     *  are recomputed at all necessary times.
     *
     *  There are three computeXXX functions. One is for computing the
     *  boundsInParent, the second for computing boundsInLocal, and the
     *  third for computing the default layout bounds (which, by default,
     *  is based on the geometric bounds). These functions are all prefixed
     *  with "compute" because they create and return new immutable
     *  Bounds objects.
     *
     *  There are three getXXXBounds functions. One is for returning the
     *  complete transformed bounds. The second is for returning the
     *  local bounds. The last is for returning the geometric bounds. These
     *  functions are all prefixed with "get" because they may well return
     *  a cached value, or may actually compute the bounds if necessary. These
     *  functions all have the same signature. They take a Bounds2D and
     *  BaseTransform, and return a Bounds2D (the same as they took). These
     *  functions essentially populate the supplied bounds2D with the
     *  appropriate bounds information, leveraging cached bounds if possible.
     *
     *  There is a single impl_computeGeomBounds function which is abstract.
     *  This must be implemented in each subclass, and is responsible for
     *  computing the actual geometric bounds for the Node. For example, Parent
     *  is written such that this function is the union of the transformed
     *  bounds of each child. Rectangle is written such that this takes into
     *  account the size and stroke. Text is written such that it is computed
     *  based on the actual glyphs.
     *
     *  There are two updateXXX functions, updateGeomBounds and updateTxBounds.
     *  These functions are for ensuring that geomBounds and txBounds are
     *  valid. They only execute in the case of the cached value being invalid,
     *  so the function call is very cheap in cases where the cached bounds
     *  values are still valid.
     */

    /**
     * An affine transform that holds the computed local-to-parent transform.
     * This is the concatenation of all transforms in this node, including all
     * of the convenience transforms.
     */
    private final Affine3D localToParentTx = new Affine3D();

    /**
     * This flag is used to indicate that localToParentTx is dirty and needs
     * to be recomputed.
     */
    private boolean transformDirty = true;

    /**
     * The cached transformed bounds. This is never null, but is frequently set
     * to be invalid whenever the bounds for the node have changed. These are
     * "complete" bounds, that is, with transforms and effect and clip applied.
     * Note that this is equivalent to boundsInParent
     */
    private BaseBounds txBounds = new RectBounds();

    /**
     * The cached bounds. This is never null, but is frequently set to be
     * invalid whenever the bounds for the node have changed. These are the
     * "content" bounds, that is, without transforms or effects applied.
     */
    private BaseBounds geomBounds = new RectBounds();

    /**
     * This special flag is used only by Parent to flag whether or not
     * the *parent* has processed the fact that bounds have changed for this
     * child Node. We need some way of flagging this on a per-node basis to
     * enable the significant performance optimizations and fast paths that
     * are in the Parent code.
     * <p>
     * To reduce confusion, although this variable is defined on Node, it
     * really belongs to the Parent of the node and should *only* be modified
     * by the parent.
     */
    boolean boundsChanged = true;

    /**
     * Returns geometric bounds, but may be over-ridden by a subclass.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected Bounds impl_computeLayoutBounds() {
        BaseBounds tempBounds = TempState.getInstance().bounds;
        tempBounds = getGeomBounds(tempBounds,
                                   BaseTransform.IDENTITY_TRANSFORM);
        return new BoundingBox(tempBounds.getMinX(),
                               tempBounds.getMinY(),
                               tempBounds.getMinZ(),
                               tempBounds.getWidth(),
                               tempBounds.getHeight(),
                               tempBounds.getDepth());
    }

    /**
     * Subclasses may customize the layoutBounds by means of overriding the
     * impl_computeLayoutBounds method. If the layout bounds need to be
     * recomputed, the subclass must notify the Node implementation of this
     * fact so that appropriate notifications and internal state can be
     * kept in sync. Subclasses must call impl_layoutBoundsChanged to
     * let Node know that the layout bounds are invalid and need to be
     * recomputed.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected void impl_layoutBoundsChanged() {
        layoutBounds.invalidate();
        if (getScaleX() != 1 || getScaleY() != 1 || getScaleZ() != 1 || getRotate() != 0) {
            // if either the scale or rotate convenience variables are used,
            // then we need a valid pivot point. Since the layoutBounds
            // affects the pivot we need to invalidate the transform
            impl_transformsChanged();
        }
        // notify the parent
        // Group instanceof check a little hoaky, but it allows us to disable
        // unnecessary layout for the case of a non-resizable within a group
        if (isManaged() && (getParent() != null) && !(getParent() instanceof Group && !isResizable())) {
            getParent().requestLayout();
        }
    }

    /**
     * Loads the given bounds object with the transformed bounds relative to,
     * and based on, the given transform. That is, this is the local bounds
     * with the local-to-parent transform applied.
     *
     * We *never* pass null in as a bounds. This method will
     * NOT take a null bounds object. The returned value may be
     * the same bounds object passed in, or it may be a new object.
     * The reason for this object promotion is in the case of needing
     * to promote from a RectBounds to a BoxBounds (3D).
     */
    BaseBounds getTransformedBounds(BaseBounds bounds, BaseTransform tx) {
        // TODO couldn't we also take a fastpath for cases where tx is
        // simply a translation?
        updateLocalToParentTransform();
        if (tx.isIdentity()) {
            updateTxBounds();
            bounds = bounds.deriveWithNewBounds(txBounds);
            return bounds;
        } else if (localToParentTx.isIdentity()) {
            return getLocalBounds(bounds, tx);
        } else {
            double mxx = tx.getMxx();
            double mxy = tx.getMxy();
            double mxz = tx.getMxz();
            double mxt = tx.getMxt();
            double myx = tx.getMyx();
            double myy = tx.getMyy();
            double myz = tx.getMyz();
            double myt = tx.getMyt();
            double mzx = tx.getMzx();
            double mzy = tx.getMzy();
            double mzz = tx.getMzz();
            double mzt = tx.getMzt();
            BaseTransform boundsTx = tx.deriveWithConcatenation(localToParentTx);
            bounds = getLocalBounds(bounds, boundsTx);
            if (boundsTx == tx) {
                tx.restoreTransform(mxx, mxy, mxz, mxt,
                                    myx, myy, myz, myt,
                                    mzx, mzy, mzz, mzt);
            }
            return bounds;
        }
    }

    /**
     * Loads the given bounds object with the local bounds relative to,
     * and based on, the given transform. That is, these are the geometric
     * bounds + clip and effect.
     *
     * We *never* pass null in as a bounds. This method will
     * NOT take a null bounds object. The returned value may be
     * the same bounds object passed in, or it may be a new object.
     * The reason for this object promotion is in the case of needing
     * to promote from a RectBounds to a BoxBounds (3D).
     */
    BaseBounds getLocalBounds(BaseBounds bounds, BaseTransform tx) {
        if (getEffect() != null || getClip() != null) {
            // We either get the bounds of the effect (if it isn't null)
            // or we get the geom bounds (if effect is null). We will then
            // intersect this with the clip.
            if (getEffect() != null) {
                BaseBounds b = getEffect().impl_getBounds(bounds, tx, this, boundsAccessor);
                bounds = bounds.deriveWithNewBounds(b);
            } else {
                bounds = getGeomBounds(bounds, tx);
            }
            // intersect with the clip. Take care with "bounds" as it may
            // actually be TEMP_BOUNDS, so we save off state
            if (getClip() != null) {
                //TODO: For 3D case the intersecting transformed bounds and
                //      transformed clip will not produce the correct bounds.
                double x1 = bounds.getMinX();
                double y1 = bounds.getMinY();
                double x2 = bounds.getMaxX();
                double y2 = bounds.getMaxY();
                double z1 = bounds.getMinZ();
                double z2 = bounds.getMaxZ();
                bounds = getClip().getTransformedBounds(bounds, tx);
                bounds.intersectWith((float)x1, (float)y1, (float)z1,
                        (float)x2, (float)y2, (float)z2);
            }
            // return the bounds
            return bounds;
        } else {
            // fast path, simply return geom bounds
            return getGeomBounds(bounds, tx);
        }
    }

    /**
     * Loads the given bounds object with the geometric bounds relative to,
     * and based on, the given transform.
     *
     * We *never* pass null in as a bounds. This method will
     * NOT take a null bounds object. The returned value may be
     * the same bounds object passed in, or it may be a new object.
     * The reason for this object promotion is in the case of needing
     * to promote from a RectBounds to a BoxBounds (3D).
     */
    BaseBounds getGeomBounds(BaseBounds bounds, BaseTransform tx) {
        if (tx.isTranslateOrIdentity()) {
            // we can take a fast path since we know tx is either a simple
            // translation or is identity
            updateGeomBounds();
            bounds = bounds.deriveWithNewBounds(geomBounds);
            if (!tx.isIdentity()) {
                double translateX = tx.getMxt();
                double translateY = tx.getMyt();
                double translateZ = tx.getMzt();
                bounds = bounds.deriveWithNewBounds((float) (bounds.getMinX() + translateX),
                        (float) (bounds.getMinY() + translateY),
                        (float) (bounds.getMinZ() + translateZ),
                        (float) (bounds.getMaxX() + translateX),
                        (float) (bounds.getMaxY() + translateY),
                        (float) (bounds.getMaxZ() + translateZ));
            }
            return bounds;
        } else if (tx.is2D()) {
            // this is a scale / rotate / skew transform
            return impl_computeGeomBounds(bounds, tx);
        } else {
            // Note: Transforming the local geomBounds into a 3D space will yield a bounds
            // that isn't as tight as transforming its geometry and compute it bounds.
            updateGeomBounds();
            return tx.transform(geomBounds, bounds);
        }
    }

    /**
     * Computes the geometric bounds for this Node. This method is abstract
     * and must be implemented by each Node subclass.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public abstract BaseBounds impl_computeGeomBounds(BaseBounds bounds, BaseTransform tx);

    /**
     * If necessary, recomputes the cached local bounds. If the bounds are not
     * invalid, then this method is a no-op.
     */
    void updateGeomBounds() {
        if (geomBoundsInvalid) {
            geomBounds = impl_computeGeomBounds(geomBounds, BaseTransform.IDENTITY_TRANSFORM);
            geomBoundsInvalid = false;
        }
    }

    /**
     * If necessary, recomputes the cached transformed bounds.
     * If the cached transformed bounds are not invalid, then
     * this method is a no-op.
     */
    void updateTxBounds() {
        if (txBoundsInvalid) {
            updateLocalToParentTransform();
            txBounds = getLocalBounds(txBounds, localToParentTx);
            txBoundsInvalid = false;
        }
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected abstract boolean impl_computeContains(double localX, double localY);

    private double min4(double v1, double v2, double v3, double v4) {
        return Math.min(Math.min(v1, v2), Math.min(v3, v4));
    }

    private double max4(double v1, double v2, double v3, double v4) {
        return Math.max(Math.max(v1, v2), Math.max(v3, v4));
    }

    private double min8(double v1, double v2, double v3, double v4,
            double v5, double v6, double v7, double v8) {
        return Math.min(min4(v1, v2, v3, v4), min4(v5, v6, v7, v8));
    }

    private double max8(double v1, double v2, double v3, double v4,
            double v5, double v6, double v7, double v8) {
        return Math.max(max4(v1, v2, v3, v4), max4(v5, v6, v7, v8));
    }

    Bounds createBoundingBox(Point3D p1, Point3D p2, Point3D p3, Point3D p4,
            Point3D p5, Point3D p6, Point3D p7, Point3D p8) {
        double minX = min8(p1.getX(), p2.getX(), p3.getX(), p4.getX(),
                p5.getX(), p6.getX(), p7.getX(), p8.getX());
        double maxX = max8(p1.getX(), p2.getX(), p3.getX(), p4.getX(),
                p5.getX(), p6.getX(), p7.getX(), p8.getX());
        double minY = min8(p1.getY(), p2.getY(), p3.getY(), p4.getY(),
                p5.getY(), p6.getY(), p7.getY(), p8.getY());
        double maxY = max8(p1.getY(), p2.getY(), p3.getY(), p4.getY(),
                p5.getY(), p6.getY(), p7.getY(), p8.getY());
        double minZ = min8(p1.getZ(), p2.getZ(), p3.getZ(), p4.getZ(),
                p5.getZ(), p6.getZ(), p7.getZ(), p8.getZ());
        double maxZ = max8(p1.getZ(), p2.getZ(), p3.getZ(), p4.getZ(),
                p5.getZ(), p6.getZ(), p7.getZ(), p8.getZ());

        return new BoundingBox(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ);
    }

    Bounds createBoundingBox(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        double minX = min4(p1.getX(), p2.getX(), p3.getX(), p4.getX());
        double maxX = max4(p1.getX(), p2.getX(), p3.getX(), p4.getX());
        double minY = min4(p1.getY(), p2.getY(), p3.getY(), p4.getY());
        double maxY = max4(p1.getY(), p2.getY(), p3.getY(), p4.getY());

        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    /*
     *                   Bounds Invalidation And Notification
     *
     *  The goal of this section is to efficiently propagate bounds
     *  invalidation through the scenegraph while also being semantically
     *  correct.
     *
     *  The code path for invalidation of layout bounds is somewhat confusing
     *  primarily due to performance enhancements and the desire to reduce the
     *  number of requestLayout() calls that are performed when layout bounds
     *  change. Before diving into layout bounds, I will first describe how
     *  normal bounds invalidation occurs.
     *
     *  When a node's geometry changes (for example, if the width of a
     *  Rectangle is changed) then the Node must call impl_geomChanged().
     *  Invoking this function will eventually clear all cached bounds and
     *  notify to each parent up the tree that their bounds may have changed.
     *
     *  After invalidating geomBounds (and after kicking off layout bounds
     *  notification), impl_geomChanged calls localBoundsChanged(). It should
     *  be noted that impl_geomChanged should only be called when the geometry
     *  of the node has changed such that it may result in the geom bounds
     *  actually changing.
     *
     *  localBoundsChanged() simply invalidates boundsInLocal and then calls
     *  transformedBoundsChanged().
     *
     *  transformedBoundsChanged() is responsible for invalidating
     *  boundsInParent and txBounds. If the Node is not visible, then there is
     *  no need to notify the parent of the bounds change because the parent's
     *  bounds do not include invisible nodes. If the node is visible, then
     *  it must tell the parent that this child node's bounds have changed.
     *  It is up to the parent to eventually invoke its own impl_geomChanged
     *  function. If instead of a parent this node has a clipParent, then the
     *  clipParent's localBoundsChanged() is called instead.
     *
     *  There are a few other ways in which we enter the invalidate steps
     *  beyond just the geometry changes. If the visibility of a Node changes,
     *  its own bounds are not affected but its parent's bounds are. So a
     *  special call to parent.childVisibilityChanged is made so the parent
     *  can react accordingly.
     *
     *  If a transform is changed (layoutX, layoutY, rotate, transforms, etc)
     *  then the transform must be invalidated. When a transform is invalidated,
     *  it must also invalidate the txBounds by invoking
     *  transformedBoundsChanged, which will in turn notify the parent as
     *  before.
     *
     *  If an effect is changed or replaced then the local bounds must be
     *  invalidated, as well as the transformedBounds and the parent notified
     *  of the change in bounds.
     *
     *  layoutBound is somewhat unique in that it can be redefined in
     *  subclasses. By default, the layoutBounds is the geomBounds, and so
     *  whenever the impl_geomBounds() function is called the layoutBounds
     *  must be invalidated. However in subclasses, especially Resizables,
     *  the layout bounds may not be defined to be the same as the geometric
     *  bounds. This is both useful and provides a very nice performance
     *  optimization for regions and controls. In this case, subclasses
     *  need some way to interpose themselves such that a call to
     *  impl_geomChanged() *does not* invalidate the layout bounds.
     *
     *  This interposition happens by providing the
     *  impl_notifyLayoutBoundsChanged function. The default implementation
     *  simply invalidates boundsInLocal. Subclasses (such as Region and
     *  Control) can override this function so that it does not invalidate
     *  the layout bounds.
     *
     *  An on invalidate trigger on layoutBounds handles kicking off the rest
     *  of the invalidate process for layoutBounds. Because the layout bounds
     *  define the pivot point, if scaleX, scaleY, or rotate contain
     *  non-identity values then whenever the layoutBounds change the
     *  transformed bounds also change. Finally, if this node's parent is
     *  a Region and if the Node is being managed by the Region, then
     *  we must call requestLayout on the Region whenever the layout bounds
     *  have changed.
     */

    /**
     * Invoked by subclasses whenever their geometric bounds have changed.
     * Because the default layout bounds is based on the node geometry, this
     * function will invoke impl_notifyLayoutBoundsChanged. The default
     * implementation of impl_notifyLayoutBoundsChanged() will simply invalidate
     * layoutBounds. Resizable subclasses will want to override this function
     * in most cases to be a no-op.
     * <p>
     * This function will also invalidate the cached geom bounds, and then
     * invoke localBoundsChanged() which will eventually end up invoking a
     * chain of functions up the tree to ensure that each parent of this
     * Node is notified that its bounds may have also changed.
     * <p>
     * This function should be treated as though it were final. It is not
     * intended to be overridden by subclasses.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected void impl_geomChanged() {
        geomBounds.makeEmpty();
        geomBoundsInvalid = true;
        impl_markDirty(DirtyBits.NODE_BOUNDS);
        impl_notifyLayoutBoundsChanged();
        localBoundsChanged();
    }

    private boolean geomBoundsInvalid = true;
    private boolean txBoundsInvalid = true;

    /**
     * Responds to changes in the local bounds by invalidating boundsInLocal
     * and notifying this node that its transformed bounds have changed.
     */
    void localBoundsChanged() {
        invalidateBoundsInLocal();
        transformedBoundsChanged();
    }

    /**
     * Responds to changes in the transformed bounds by invalidating txBounds
     * and boundsInParent. If this Node is not visible, then we have no need
     * to walk further up the tree but can instead simply invalidate state.
     * Otherwise, this function will notify parents (either the parent or the
     * clipParent) that this child Node's bounds have changed.
     */
    void transformedBoundsChanged() {
        boolean wasValid = !txBoundsInvalid;
        txBounds.makeEmpty();
        txBoundsInvalid = true;
        invalidateBoundsInParent();
        if (isVisible()) {
            // TODO if we do dirty regions, insert that code here
            if (wasValid) {
                notifyParentOfBoundsChange();
            }
        }
        impl_markDirty(DirtyBits.NODE_BOUNDS);
    }

    /**
     * Invoked by impl_geomChanged(). Since layoutBounds is by default based
     * on the geometric bounds, the default implementation of this function will
     * invalidate the layoutBounds. Resizable Node subclasses generally base
     * layoutBounds on the width/height instead of the geometric bounds, and so
     * will generally want to override this function to be a no-op.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected void impl_notifyLayoutBoundsChanged() {
        impl_layoutBoundsChanged();
    }

    /**
     * Notifies both the real parent and the clip parent (if they exist) that
     * the bounds of the child has changed. Note that since FX doesn't throw
     * NPE's, things actually are faster if we don't check twice for Null
     * (we check once, the compiler checks again)
     */
    void notifyParentOfBoundsChange() {
        // let the parent know which node has changed and the parent will
        // deal with marking itself invalid correctly
        if (getParent() != null) {
            getParent().childBoundsChanged(this);
        }
        // since the clip is used to compute the local bounds (and not the
        // geom bounds), we just need to notify that local bounds on the
        // clip parent have changed
        if (clipParent != null) {
            clipParent.localBoundsChanged();
        }
    }

    /***************************************************************************
     *                                                                         *
     * Geometry and coordinate system related APIs. For example, methods       *
     * related to containment, intersection, coordinate space conversion, etc. *
     *                                                                         *
     **************************************************************************/

    /**
     * Returns {@code true} if the given point (specified in the local
     * coordinate space of this {@code Node}) is contained within the shape of
     * this {@code Node}. Note that this method does not take visibility into
     * account; the test is based on the geometry of this {@code Node} only.
     *
     * @profile common
     */
    public boolean contains(double localX, double localY) {
        if (containsBounds(localX, localY)) {
            return (isPickOnBounds() || impl_computeContains(localX, localY));
        }
        return false;
    }

    /**
     * This method only does the contains check based on the bounds, clip and
     * effect of this node, excluding its shape (or geometry).
     *
     * Returns true if the given point (specified in the local
     * coordinate space of this {@code Node}) is contained within the bounds,
     * clip and effect of this node.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected boolean containsBounds(double localX, double localY) {
          final TempState tempState = TempState.getInstance();
        BaseBounds tempBounds = tempState.bounds;

        // first, we do a quick test to see if the point is contained in
        // our local bounds. If so, then we will go the next step and check
        // the clip, effect, and geometry for containment.
        tempBounds = getLocalBounds(tempBounds,
                                    BaseTransform.IDENTITY_TRANSFORM);
        if (tempBounds.contains((float)localX, (float)localY)) {
            // if the clip is defined, then check it for containment, being
            // sure to convert from this node's local coordinate system
            // to the local coordinate system of the clip node
            if (getClip() != null) {
                tempState.point.x = (float)localX;
                tempState.point.y = (float)localY;
                try {
                    getClip().parentToLocal(tempState.point);
                } catch (NoninvertibleTransformException e) {
                    return false;
                }
                if (!getClip().contains(tempState.point.x, tempState.point.y)) {
                    return false;
                }
            }
            // if there is an effect, then we need to check the effect
            // for containment as well.
            if (getEffect() != null) {
                tempBounds = getEffect().impl_getBounds(
                                    tempBounds,
                                    BaseTransform.IDENTITY_TRANSFORM,
                                    this, boundsAccessor);

                if (!tempBounds.contains((float)localX, (float)localY)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the given point (specified in the local
     * coordinate space of this {@code Node}) is contained within the shape of
     * this {@code Node}. Note that this method does not take visibility into
     * account; the test is based on the geometry of this {@code Node} only.
     *
     * @profile common
     */
    public boolean contains(Point2D localPoint) {
        return contains(localPoint.getX(), localPoint.getY());
    }

    /**
     * Returns {@code true} if the given rectangle (specified in the local
     * coordinate space of this {@code Node}) intersects the shape of this
     * {@code Node}. Note that this method does not take visibility into
     * account; the test is based on the geometry of this {@code Node} only.
     * The default behavior of this function is simply to check if the
     * given coordinates intersect with the local bounds.
     *
     * @profile common
     */
    public boolean intersects(double localX, double localY, double localWidth, double localHeight) {
        BaseBounds tempBounds = TempState.getInstance().bounds;
        tempBounds = getLocalBounds(tempBounds,
                                    BaseTransform.IDENTITY_TRANSFORM);
        return tempBounds.intersects((float)localX,
                                     (float)localY,
                                     (float)localWidth,
                                     (float)localHeight);
    }

    /**
     * Returns {@code true} if the given bounds (specified in the local
     * coordinate space of this {@code Node}) intersects the shape of this
     * {@code Node}. Note that this method does not take visibility into
     * account; the test is based on the geometry of this {@code Node} only.
     * The default behavior of this function is simply to check if the
     * given coordinates intersect with the local bounds.
     *
     * @profile common
     */
    public boolean intersects(Bounds localBounds) {
        return intersects(localBounds.getMinX(), localBounds.getMinY(), localBounds.getWidth(), localBounds.getHeight());
    }

    /**
     * Transforms a point from the coordinate space of the {@link Scene}
     * into the local coordinate space of this {@code Node}.
     *
     * @profile common
     */
    public Point2D sceneToLocal(double sceneX, double sceneY) {
        final com.sun.javafx.geom.Point2D tempPt =
                TempState.getInstance().point;
        tempPt.setLocation((float)sceneX, (float)sceneY);
        try {
            sceneToLocal(tempPt);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
        return new Point2D(tempPt.x, tempPt.y);
    }

    /**
     * Transforms a point from the coordinate space of the {@link javafx.scene.Scene}
     * into the local coordinate space of this {@code Node}.
     *
     * @profile common
     */
    public Point2D sceneToLocal(Point2D scenePoint) {
        return sceneToLocal(scenePoint.getX(), scenePoint.getY());
    }

    // Why is this method private?
    private Point3D sceneToLocal(double x, double y, double z) {
        final com.sun.javafx.geom.Vec3d tempV3D =
                TempState.getInstance().vec3d;
        tempV3D.set(x, y, z);
        try {
            sceneToLocal(tempV3D);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
        return new Point3D(tempV3D.x, tempV3D.y, tempV3D.z);
    }

    /**
     * Transforms a rectangle from the coordinate space of the
     * {@link javafx.scene.Scene} into the local coordinate space of this
     * {@code Node}.
     *
     * @profile common
     */
    public Bounds sceneToLocal(Bounds sceneBounds) {
        // Do a quick update of localToParentTransform so that we can determine
        // if this tx is 2D transform
        updateLocalToParentTransform();
        if (localToParentTx.is2D() && (sceneBounds.getMinZ() == 0) && (sceneBounds.getMaxZ() == 0)) {
            Point2D p1 = sceneToLocal(sceneBounds.getMinX(), sceneBounds.getMinY());
            Point2D p2 = sceneToLocal(sceneBounds.getMaxX(), sceneBounds.getMinY());
            Point2D p3 = sceneToLocal(sceneBounds.getMaxX(), sceneBounds.getMaxY());
            Point2D p4 = sceneToLocal(sceneBounds.getMinX(), sceneBounds.getMaxY());

            return createBoundingBox(p1, p2, p3, p4);
        }
        Point3D p1 = sceneToLocal(sceneBounds.getMinX(), sceneBounds.getMinY(), sceneBounds.getMinZ());
        Point3D p2 = sceneToLocal(sceneBounds.getMinX(), sceneBounds.getMinY(), sceneBounds.getMaxZ());
        Point3D p3 = sceneToLocal(sceneBounds.getMinX(), sceneBounds.getMaxY(), sceneBounds.getMinZ());
        Point3D p4 = sceneToLocal(sceneBounds.getMinX(), sceneBounds.getMaxY(), sceneBounds.getMaxZ());
        Point3D p5 = sceneToLocal(sceneBounds.getMaxX(), sceneBounds.getMaxY(), sceneBounds.getMinZ());
        Point3D p6 = sceneToLocal(sceneBounds.getMaxX(), sceneBounds.getMaxY(), sceneBounds.getMaxZ());
        Point3D p7 = sceneToLocal(sceneBounds.getMaxX(), sceneBounds.getMinY(), sceneBounds.getMinZ());
        Point3D p8 = sceneToLocal(sceneBounds.getMaxX(), sceneBounds.getMinY(), sceneBounds.getMaxZ());
        return createBoundingBox(p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Transforms a point from the local coordinate space of this {@code Node}
     * into the coordinate space of its {@link javafx.scene.Scene}.
     *
     * @profile common
     */
    public Point2D localToScene(double localX, double localY) {
        final com.sun.javafx.geom.Point2D tempPt =
                TempState.getInstance().point;
        tempPt.setLocation((float)localX, (float)localY);
        localToScene(tempPt);
        return new Point2D(tempPt.x, tempPt.y);
    }

    /**
     * Transforms a point from the local coordinate space of this {@code Node}
     * into the coordinate space of its {@link javafx.scene.Scene}.
     *
     * @profile common
     */
    public Point2D localToScene(Point2D localPoint) {
        return localToScene(localPoint.getX(), localPoint.getY());
    }

    private Point3D localToScene(double x, double y, double z) {
        final com.sun.javafx.geom.Vec3d tempV3D =
                TempState.getInstance().vec3d;
        tempV3D.set(x, y, z);
        localToScene(tempV3D);
        return new Point3D(tempV3D.x, tempV3D.y, tempV3D.z);
    }

    /**
     * Transforms a bounds from the local coordinate space of this
     * {@code Node} into the coordinate space of its {@link javafx.scene.Scene}.
     *
     * @profile common
     */
    public Bounds localToScene(Bounds localBounds) {
        // Do a quick update of localToParentTransform so that we can determine
        // if this tx is 2D transform
        updateLocalToParentTransform();
        if (localToParentTx.is2D() && (localBounds.getMinZ() == 0) && (localBounds.getMaxZ() == 0)) {
            Point2D p1 = localToScene(localBounds.getMinX(), localBounds.getMinY());
            Point2D p2 = localToScene(localBounds.getMaxX(), localBounds.getMinY());
            Point2D p3 = localToScene(localBounds.getMaxX(), localBounds.getMaxY());
            Point2D p4 = localToScene(localBounds.getMinX(), localBounds.getMaxY());

            return createBoundingBox(p1, p2, p3, p4);
        }
        Point3D p1 = localToScene(localBounds.getMinX(), localBounds.getMinY(), localBounds.getMinZ());
        Point3D p2 = localToScene(localBounds.getMinX(), localBounds.getMinY(), localBounds.getMaxZ());
        Point3D p3 = localToScene(localBounds.getMinX(), localBounds.getMaxY(), localBounds.getMinZ());
        Point3D p4 = localToScene(localBounds.getMinX(), localBounds.getMaxY(), localBounds.getMaxZ());
        Point3D p5 = localToScene(localBounds.getMaxX(), localBounds.getMaxY(), localBounds.getMinZ());
        Point3D p6 = localToScene(localBounds.getMaxX(), localBounds.getMaxY(), localBounds.getMaxZ());
        Point3D p7 = localToScene(localBounds.getMaxX(), localBounds.getMinY(), localBounds.getMinZ());
        Point3D p8 = localToScene(localBounds.getMaxX(), localBounds.getMinY(), localBounds.getMaxZ());
        return createBoundingBox(p1, p2, p3, p4, p5, p6, p7, p8);

    }

    /**
     * Transforms a point from the coordinate space of the parent into the
     * local coordinate space of this {@code Node}.
     *
     * @profile common
     */
    public Point2D parentToLocal(double parentX, double parentY) {
        final com.sun.javafx.geom.Point2D tempPt =
                TempState.getInstance().point;
        tempPt.setLocation((float)parentX, (float)parentY);
        try {
            parentToLocal(tempPt);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
        return new Point2D(tempPt.x, tempPt.y);
    }

    /**
     * Transforms a point from the coordinate space of the parent into the
     * local coordinate space of this {@code Node}.
     *
     * @profile common
     */
    public Point2D parentToLocal(Point2D parentPoint) {
        return parentToLocal(parentPoint.getX(), parentPoint.getY());
    }

    private Point3D parentToLocal(double x, double y, double z) {
        final com.sun.javafx.geom.Vec3d tempV3D =
                TempState.getInstance().vec3d;
        tempV3D.set(x, y, z);
        try {
            parentToLocal(tempV3D);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
        return new Point3D(tempV3D.x, tempV3D.y, tempV3D.z);
    }

    /**
     * Transforms a rectangle from the coordinate space of the parent into the
     * local coordinate space of this {@code Node}.
     *
     * @profile common
     */
    public Bounds parentToLocal(Bounds parentBounds) {
        // Do a quick update of localToParentTransform so that we can determine
        // if this tx is 2D transform
        updateLocalToParentTransform();
        if (localToParentTx.is2D() && (parentBounds.getMinZ() == 0) && (parentBounds.getMaxZ() == 0)) {
            Point2D p1 = parentToLocal(parentBounds.getMinX(), parentBounds.getMinY());
            Point2D p2 = parentToLocal(parentBounds.getMaxX(), parentBounds.getMinY());
            Point2D p3 = parentToLocal(parentBounds.getMaxX(), parentBounds.getMaxY());
            Point2D p4 = parentToLocal(parentBounds.getMinX(), parentBounds.getMaxY());

            return createBoundingBox(p1, p2, p3, p4);
        }
        Point3D p1 = parentToLocal(parentBounds.getMinX(), parentBounds.getMinY(), parentBounds.getMinZ());
        Point3D p2 = parentToLocal(parentBounds.getMinX(), parentBounds.getMinY(), parentBounds.getMaxZ());
        Point3D p3 = parentToLocal(parentBounds.getMinX(), parentBounds.getMaxY(), parentBounds.getMinZ());
        Point3D p4 = parentToLocal(parentBounds.getMinX(), parentBounds.getMaxY(), parentBounds.getMaxZ());
        Point3D p5 = parentToLocal(parentBounds.getMaxX(), parentBounds.getMaxY(), parentBounds.getMinZ());
        Point3D p6 = parentToLocal(parentBounds.getMaxX(), parentBounds.getMaxY(), parentBounds.getMaxZ());
        Point3D p7 = parentToLocal(parentBounds.getMaxX(), parentBounds.getMinY(), parentBounds.getMinZ());
        Point3D p8 = parentToLocal(parentBounds.getMaxX(), parentBounds.getMinY(), parentBounds.getMaxZ());
        return createBoundingBox(p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Transforms a point from the local coordinate space of this {@code Node}
     * into the coordinate space of its parent.
     *
     * @profile common
     */
    public Point2D localToParent(double localX, double localY) {
        final com.sun.javafx.geom.Point2D tempPt =
                TempState.getInstance().point;
        tempPt.setLocation((float)localX, (float)localY);
        localToParent(tempPt);
        return new Point2D(tempPt.x, tempPt.y);
    }

    /**
     * Transforms a point from the local coordinate space of this {@code Node}
     * into the coordinate space of its parent.
     *
     * @profile common
     */
    public Point2D localToParent(Point2D localPoint) {
        return localToParent(localPoint.getX(), localPoint.getY());
    }

    private Point3D localToParent(double x, double y, double z) {
        final com.sun.javafx.geom.Vec3d tempV3D =
                TempState.getInstance().vec3d;
        tempV3D.set(x, y, z);
        localToParent(tempV3D);
        return new Point3D(tempV3D.x, tempV3D.y, tempV3D.z);
    }

    /**
     * Transforms a bounds from the local coordinate space of this
     * {@code Node} into the coordinate space of its parent.
     *
     * @profile common
     */
    public Bounds localToParent(Bounds localBounds) {
        // Do a quick update of localToParentTransform so that we can determine
        // if this tx is 2D transform
        updateLocalToParentTransform();
        if (localToParentTx.is2D() && (localBounds.getMinZ() == 0) && (localBounds.getMaxZ() == 0)) {
            Point2D p1 = localToParent(localBounds.getMinX(), localBounds.getMinY());
            Point2D p2 = localToParent(localBounds.getMaxX(), localBounds.getMinY());
            Point2D p3 = localToParent(localBounds.getMaxX(), localBounds.getMaxY());
            Point2D p4 = localToParent(localBounds.getMinX(), localBounds.getMaxY());

            return createBoundingBox(p1, p2, p3, p4);
        }
        Point3D p1 = localToParent(localBounds.getMinX(), localBounds.getMinY(), localBounds.getMinZ());
        Point3D p2 = localToParent(localBounds.getMinX(), localBounds.getMinY(), localBounds.getMaxZ());
        Point3D p3 = localToParent(localBounds.getMinX(), localBounds.getMaxY(), localBounds.getMinZ());
        Point3D p4 = localToParent(localBounds.getMinX(), localBounds.getMaxY(), localBounds.getMaxZ());
        Point3D p5 = localToParent(localBounds.getMaxX(), localBounds.getMaxY(), localBounds.getMinZ());
        Point3D p6 = localToParent(localBounds.getMaxX(), localBounds.getMaxY(), localBounds.getMaxZ());
        Point3D p7 = localToParent(localBounds.getMaxX(), localBounds.getMinY(), localBounds.getMinZ());
        Point3D p8 = localToParent(localBounds.getMaxX(), localBounds.getMinY(), localBounds.getMaxZ());
        return createBoundingBox(p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Copy the localToParent transform into specified transform.
     */
    BaseTransform getLocalToParentTransform(BaseTransform tx) {
        updateLocalToParentTransform();
        tx.setTransform(localToParentTx);
        return tx;
    }

    /**
     * Currently used only by AnimationPath.createAnimationPathHelper().
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public BaseTransform impl_getLeafTransform() {
        return getLocalToParentTransform(TempState.getInstance().leafTx);
    }

    /**
     * Invoked whenever the transforms[] ObservableList changes, or by the transforms
     * in that ObservableList whenever they are changed.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_transformsChanged() {
        impl_markDirty(DirtyBits.NODE_TRANSFORM);
        transformDirty = true;
        transformedBoundsChanged();
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public double impl_getPivotX() {
        final Bounds bounds = getLayoutBounds();
        return bounds.getMinX() + bounds.getWidth()/2;
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public double impl_getPivotY() {
        final Bounds bounds = getLayoutBounds();
        return bounds.getMinY() + bounds.getHeight()/2;
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public double impl_getPivotZ() {
        final Bounds bounds = getLayoutBounds();
        return bounds.getMinZ() + bounds.getDepth()/2;
    }

    /**
     * This helper function will update the transform matrix on the peer based
     * on the "complete" transform for this node.
     */
    void updateLocalToParentTransform() {
        if (transformDirty) {
            localToParentTx.setToIdentity();
            if (getScaleX() != 1 || getScaleY() != 1 || getScaleZ() != 1 || getRotate() != 0) {
                // recompute pivotX, pivotY and pivotZ
                double pivotX = impl_getPivotX();
                double pivotY = impl_getPivotY();
                double pivotZ = impl_getPivotZ();
                localToParentTx.translate(getTranslateX() + getLayoutX() + pivotX, getTranslateY() + getLayoutY() + pivotY, getTranslateZ() + pivotZ);
                localToParentTx.rotate(Math.toRadians(getRotate()), getRotationAxis().getX(), getRotationAxis().getY(), getRotationAxis().getZ());
                localToParentTx.scale(getScaleX(), getScaleY(), getScaleZ());
                localToParentTx.translate(-pivotX, -pivotY, -pivotZ);
            } else {
                localToParentTx.translate(getTranslateX() + getLayoutX(), getTranslateY() + getLayoutY(), getTranslateZ());
            }

            if (hasTransforms()) {
                for (Transform t : getTransforms()) {
                    t.impl_apply(localToParentTx);
                }
            }

            transformDirty = false;
        }
    }

    /**
     * Transforms in place the specified point from parent coords to local
     * coords. Made package private for the sake of testing.
     */
    void parentToLocal(com.sun.javafx.geom.Point2D pt) throws NoninvertibleTransformException {
        updateLocalToParentTransform();
        localToParentTx.inverseTransform(pt, pt);
    }

    void parentToLocal(com.sun.javafx.geom.Vec3d pt) throws NoninvertibleTransformException {
        updateLocalToParentTransform();
        localToParentTx.inverseTransform(pt, pt);
    }

    void sceneToLocal(com.sun.javafx.geom.Point2D pt) throws NoninvertibleTransformException {
        if (getParent() != null) {
            getParent().sceneToLocal(pt);
        }
        parentToLocal(pt);
    }

    void sceneToLocal(com.sun.javafx.geom.Vec3d pt) throws NoninvertibleTransformException {
        if (getParent() != null) {
            getParent().sceneToLocal(pt);
        }
        parentToLocal(pt);
    }

    void localToScene(com.sun.javafx.geom.Point2D pt) {
        localToParent(pt);
        if (getParent() != null) {
            getParent().localToScene(pt);
        }
    }

    void localToScene(com.sun.javafx.geom.Vec3d pt) {
        localToParent(pt);
        if (getParent() != null) {
            getParent().localToScene(pt);
        }
    }

    /***************************************************************************
     *                                                                         *
     * Mouse event related APIs                                                *
     *                                                                         *
     **************************************************************************/

    /**
     * Transforms in place the specified point from local coords to parent
     * coords. Made package private for the sake of testing.
     */
    void localToParent(com.sun.javafx.geom.Point2D pt) {
        updateLocalToParentTransform();
        localToParentTx.transform(pt, pt);
    }

    void localToParent(com.sun.javafx.geom.Vec3d pt) {
        updateLocalToParentTransform();
        localToParentTx.transform(pt, pt);
    }

    /**
     * Finds a top-most child node that contains the given coordinates.
     *
     * Returns the picked node, null if no such node was found.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Node impl_pickNode(double parentX, double parentY) {

        // TODO this check for whether there is no scene is dubious and complicates testing
        // In some conditions we can omit picking this node or subgraph
        if (getScene() == null || !isVisible() || isDisable() || isMouseTransparent()) {
            return null;
        }

        final com.sun.javafx.geom.Point2D tempPt =
                TempState.getInstance().point;

        // convert to local coordinates
        tempPt.setLocation((float)parentX, (float)parentY);
        try {
            parentToLocal(tempPt);
        } catch (NoninvertibleTransformException e) {
            return null;
        }

        // Delegate to a function which can be overridden by subclasses which
        // actually does the pick. The implementation is markedly different
        // for leaf nodes vs. parent nodes vs. region nodes.
        return impl_pickNodeLocal(tempPt.x, tempPt.y);
    }

    /**
     * Finds a top-most child node that contains the given local coordinates.
     *
     * Returns the picked node, null if no such node was found.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected Node impl_pickNodeLocal(double localX, double localY) {
        if (contains(localX, localY)) {
            return this;
        }
        return null;
    }

    /**
     * Finds a top-most child node that contains the given local coordinates.
     *
     * Returns the picked node, null if no such node was found.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected Node impl_pickNodeLocal(PickRay localPickRay) {
        if (impl_intersects(localPickRay)) {
            return this;
        }
        return null;
    }

    /**
     * Finds a top-most child node that intersects the given ray.
     *
     * Returns the picked node, null if no such node was found.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Node impl_pickNode(PickRay pickRay) {

        // TODO this check for whether there is no scene is dubious and complicates testing
        // In some conditions we can omit picking this node or subgraph
        if (getScene() == null || !isVisible() || isDisable() || isMouseTransparent()) {
            return null;
        }

        final BaseTransform tempPickTx = TempState.getInstance().pickTx;

        getLocalToParentTransform(tempPickTx);
        try {
            tempPickTx.invert();
        } catch (NoninvertibleTransformException e) {
            throw new RuntimeException(e);
        }
        PickRay localPickRay = pickRay.copy();
        localPickRay.transform(tempPickTx);

        // Delegate to a function which can be overridden by subclasses which
        // actually does the pick. The implementation is markedly different
        // for leaf nodes vs. parent nodes vs. region nodes.
        return impl_pickNodeLocal(localPickRay);
    }

    /**
     * Returns {@code true} if the given ray (start, dir), specified in the
     * local coordinate space of this {@code Node}, intersects the
     * shape of this {@code Node}. Note that this method does not take visibility
     * into account; the test is based on the geometry of this {@code Node} only.
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *
     * @profile common conditional scene3d
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected boolean impl_intersects(PickRay pickRay) {
        // TODO: Need to handle clip and effect
        return contentIntersects(pickRay);
    }

    /**
     * Returns true if the content of this node contains given coordinates.
     * "Content" doesn't include filters like effect or clip.
     *
     * @param pickRay
     * @return true if node's content contains passed point, false otherwise
     */
    private boolean contentIntersects(PickRay pickRay) {

        double origZ = pickRay.getOriginNoClone().z;
        double dirZ = pickRay.getDirectionNoClone().z;
        // Handle the case where pickRay is almost parallel to the Z-plane
        if (almostZero(dirZ)) {
            return false;
        }
        double t = -origZ / dirZ;
        double x = pickRay.getOriginNoClone().x + (pickRay.getDirectionNoClone().x * t);
        double y = pickRay.getOriginNoClone().y + (pickRay.getDirectionNoClone().y * t);
        return contains((float) x, (float) y); // was contentContains, the difference is that effect / clip are included
    }

    // Good to find a home for commonly use util. code such as EPS.
    // and almostZero. This code currently defined in multiple places,
    // such as Affine3D and GeneralTransform3D.
    private static final double EPSILON_ABSOLUTE = 1.0e-5;

    static boolean almostZero(double a) {
        return ((a < EPSILON_ABSOLUTE) && (a > -EPSILON_ABSOLUTE));
    }
    /***************************************************************************
     *                                                                         *
     *                             Transformations                             *
     *                                                                         *
     **************************************************************************/
    /**
     * Defines the ObservableList of {@link javafx.scene.transform.Transform} objects
     * to be applied to this {@code Node}. This ObservableList of transforms is applied
     * before {@link #translateXProperty translateX}, {@link #translateYProperty translateY}, {@link #scaleXProperty scaleX}, and
     * {@link #scaleYProperty scaleY}, {@link #rotateProperty rotate} transforms.
     *
     * @profile common
     * @defaultvalue empty
     */
    public final ObservableList<Transform> getTransforms() {
        return transformsProperty();
    }

    private ObservableList<Transform> transformsProperty() {
        return getNodeTransformation().getTransforms();
    }

    public final void setTranslateX(double value) {
        translateXProperty().set(value);
    }

    public final double getTranslateX() {
        return (nodeTransformation == null)
                ? DEFAULT_TRANSLATE_X
                : nodeTransformation.getTranslateX();
    }

    /**
     * Defines the x coordinate of the translation that is added to this {@code Node}'s
     * transform.
     * <p>
     * The node's final translation will be computed as {@link #layoutXProperty layoutX} + {@code translateX},
     * where {@code layoutX} establishes the node's stable position and {@code translateX}
     * optionally makes dynamic adjustments to that position.
     *<p>
     * This variable can be used to alter the location of a node without disturbing
     * its {@link #layoutBoundsProperty layoutBounds}, which makes it useful for animating a node's location.
     *
     * @profile common
     * @defaultvalue 0
     */
    public final DoubleProperty translateXProperty() {
        return getNodeTransformation().translateXProperty();
    }

    public final void setTranslateY(double value) {
        translateYProperty().set(value);
    }

    public final double getTranslateY() {
        return (nodeTransformation == null)
                ? DEFAULT_TRANSLATE_Y
                : nodeTransformation.getTranslateY();
    }

    /**
     * Defines the y coordinate of the translation that is added to this {@code Node}'s
     * transform.
     * <p>
     * The node's final translation will be computed as {@link #layoutYProperty layoutY} + {@code translateY},
     * where {@code layoutY} establishes the node's stable position and {@code translateY}
     * optionally makes dynamic adjustments to that position.
     *<p>
     * This variable can be used to alter the location of a node without disturbing
     * its {@link #layoutBoundsProperty layoutBounds}, which makes it useful for animating a node's location.
     *
     * @profile common
     * @defaultvalue 0
     */
    public final DoubleProperty translateYProperty() {
        return getNodeTransformation().translateYProperty();
    }

    public final void setTranslateZ(double value) {
        translateZProperty().set(value);
    }

    public final double getTranslateZ() {
        return (nodeTransformation == null)
                ? DEFAULT_TRANSLATE_Z
                : nodeTransformation.getTranslateZ();
    }

    /**
     * Defines the Z coordinate of the translation that is added to the
     * transformed coordinates of this {@code Node}.  This value will be added
     * to any translation defined by the {@code transforms} ObservableList and
     * {@code layoutZ}.
     *<p>
     * This variable can be used to alter the location of a Node without
     * disturbing its layout bounds, which makes it useful for animating a
     * node's location.
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *
     * @profile common conditional scene3d
     * @defaultvalue 0
     * @since JavaFX 1.3
     */
    public final DoubleProperty translateZProperty() {
        return getNodeTransformation().translateZProperty();
    }

    public final void setScaleX(double value) {
        scaleXProperty().set(value);
    }

    public final double getScaleX() {
        return (nodeTransformation == null) ? DEFAULT_SCALE_X
                                            : nodeTransformation.getScaleX();
    }

    /**
     * Defines the factor by which coordinates are scaled about the center of the
     * object along the X axis of this {@code Node}. This is used to stretch or
     * animate the node either manually or by using an animation.
     * <p>
     * This scale factor is not included in {@link #layoutBoundsProperty layoutBounds} by
     * default, which makes it ideal for scaling the entire node after
     * all effects and transforms have been taken into account.
     * <p>
     * The pivot point about which the scale occurs is the center of the
     * untransformed {@link #layoutBoundsProperty layoutBounds}.
     *
     * @profile common
     * @defaultvalue 1.0
     */
    public final DoubleProperty scaleXProperty() {
        return getNodeTransformation().scaleXProperty();
    }

    public final void setScaleY(double value) {
        scaleYProperty().set(value);
    }

    public final double getScaleY() {
        return (nodeTransformation == null) ? DEFAULT_SCALE_Y
                                            : nodeTransformation.getScaleY();
    }

    /**
     * Defines the factor by which coordinates are scaled about the center of the
     * object along the Y axis of this {@code Node}. This is used to stretch or
     * animate the node either manually or by using an animation.
     * <p>
     * This scale factor is not included in {@link #layoutBoundsProperty layoutBounds} by
     * default, which makes it ideal for scaling the entire node after
     * all effects and transforms have been taken into account.
     * <p>
     * The pivot point about which the scale occurs is the center of the
     * untransformed {@link #layoutBoundsProperty layoutBounds}.
     *
     * @profile common
     * @defaultvalue 1.0
     */
    public final DoubleProperty scaleYProperty() {
        return getNodeTransformation().scaleYProperty();
    }

    public final void setScaleZ(double value) {
        scaleZProperty().set(value);
    }

    public final double getScaleZ() {
        return (nodeTransformation == null) ? DEFAULT_SCALE_Z
                                            : nodeTransformation.getScaleZ();
    }

    /**
     * Defines the factor by which coordinates are scaled about the center of the
     * object along the Z axis of this {@code Node}. This is used to stretch or
     * animate the node either manually or by using an animation.
     * <p>
     * This scale factor is not included in {@link #layoutBoundsProperty layoutBounds} by
     * default, which makes it ideal for scaling the entire node after
     * all effects and transforms have been taken into account.
     * <p>
     * The pivot point about which the scale occurs is the center of the
     * rectangular bounds formed by taking {@link #boundsInLocalProperty boundsInLocal} and applying
     * all the transforms in the {@link #getTransforms transforms} ObservableList.
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *
     * @profile common conditional scene3d
     * @defaultvalue 1.0
     * @since JavaFX 1.3
     */
    public final DoubleProperty scaleZProperty() {
        return getNodeTransformation().scaleZProperty();
    }

    public final void setRotate(double value) {
        rotateProperty().set(value);
    }

    public final double getRotate() {
        return (nodeTransformation == null) ? DEFAULT_ROTATE
                                            : nodeTransformation.getRotate();
    }

    /**
     * Defines the angle of rotation about the {@code Node}'s center, measured in
     * degrees. This is used to rotate the {@code Node}.
     * <p>
     * This rotation factor is not included in {@link #layoutBoundsProperty layoutBounds} by
     * default, which makes it ideal for rotating the entire node after
     * all effects and transforms have been taken into account.
     * <p>
     * The pivot point about which the rotation occurs is the center of the
     * untransformed {@link #layoutBoundsProperty layoutBounds}.
     * <p>
     * Note that because the pivot point is computed as the center of this
     * {@code Node}'s layout bounds, any change to the layout bounds will cause
     * the pivot point to change, which can move the object. For a leaf node,
     * any change to the geometry will cause the layout bounds to change.
     * For a group node, any change to any of its children, including a
     * change in a child's geometry, clip, effect, position, orientation, or
     * scale, will cause the group's layout bounds to change. If this movement
     * of the pivot point is not
     * desired, applications should instead use the Node's {@link #getTransforms transforms}
     * ObservableList, and add a {@link javafx.scene.transform.Rotate} transform,
     * which has a user-specifiable pivot point.
     *
     * @profile common
     * @defaultvalue 0.0
     */
    public final DoubleProperty rotateProperty() {
        return getNodeTransformation().rotateProperty();
    }

    public final void setRotationAxis(Point3D value) {
        rotationAxisProperty().set(value);
    }

    public final Point3D getRotationAxis() {
        return (nodeTransformation == null)
                ? DEFAULT_ROTATION_AXIS
                : nodeTransformation.getRotationAxis();
    }

    /**
     * Defines the axis of rotation of this {@code Node}.
     * <p>
     * Note that this is a conditional feature. See
     * {@link javafx.application.ConditionalFeature#SCENE3D ConditionalFeature.SCENE3D}
     * for more information.
     *
     * @profile common conditional scene3d
     * @defaultvalue Rotate.Z_AXIS
     * @since JavaFX 1.3
     */
    public final ObjectProperty<Point3D> rotationAxisProperty() {
        return getNodeTransformation().rotationAxisProperty();
    }

    private NodeTransformation nodeTransformation;

    private NodeTransformation getNodeTransformation() {
        if (nodeTransformation == null) {
            nodeTransformation = new NodeTransformation();
        }

        return nodeTransformation;
    }

    private boolean hasTransforms() {
        return (nodeTransformation != null)
                && nodeTransformation.hasTransforms();
    }

    private static final double DEFAULT_TRANSLATE_X = 0;
    private static final double DEFAULT_TRANSLATE_Y = 0;
    private static final double DEFAULT_TRANSLATE_Z = 0;
    private static final double DEFAULT_SCALE_X = 1;
    private static final double DEFAULT_SCALE_Y = 1;
    private static final double DEFAULT_SCALE_Z = 1;
    private static final double DEFAULT_ROTATE = 0;
    private static final Point3D DEFAULT_ROTATION_AXIS = Rotate.Z_AXIS;

    private final class NodeTransformation {
        private DoubleProperty translateX;
        private DoubleProperty translateY;
        private DoubleProperty translateZ;
        private DoubleProperty scaleX;
        private DoubleProperty scaleY;
        private DoubleProperty scaleZ;
        private DoubleProperty rotate;
        private ObjectProperty<Point3D> rotationAxis;
        private ObservableList<Transform> transforms;

        public double getTranslateX() {
            return (translateX == null) ? DEFAULT_TRANSLATE_X
                                        : translateX.get();
        }

        public final DoubleProperty translateXProperty() {
            if (translateX == null) {
                translateX = new StyleableDoubleProperty(DEFAULT_TRANSLATE_X) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.TRANSLATE_X;
                    }
                    
                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "translateX";
                    }
                };
            }
            return translateX;
        }

        public double getTranslateY() {
            return (translateY == null) ? DEFAULT_TRANSLATE_Y : translateY.get();
        }

        public final DoubleProperty translateYProperty() {
            if (translateY == null) {
                translateY = new StyleableDoubleProperty(DEFAULT_TRANSLATE_Y) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.TRANSLATE_Y;
                    }
                    
                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "translateY";
                    }
                };
            }
            return translateY;
        }

        public double getTranslateZ() {
            return (translateZ == null) ? DEFAULT_TRANSLATE_Z : translateZ.get();
        }

        public final DoubleProperty translateZProperty() {
            if (translateZ == null) {
                translateZ = new StyleableDoubleProperty(DEFAULT_TRANSLATE_Z) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.TRANSLATE_Z;
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "translateZ";
                    }
                };
            }
            return translateZ;
        }

        public double getScaleX() {
            return (scaleX == null) ? DEFAULT_SCALE_X : scaleX.get();
        }

        public final DoubleProperty scaleXProperty() {
            if (scaleX == null) {
                scaleX = new StyleableDoubleProperty(DEFAULT_SCALE_X) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.SCALE_X;
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "scaleX";
                    }
                };
            }
            return scaleX;
        }

        public double getScaleY() {
            return (scaleY == null) ? DEFAULT_SCALE_Y : scaleY.get();
        }

        public final DoubleProperty scaleYProperty() {
            if (scaleY == null) {
                scaleY = new StyleableDoubleProperty(DEFAULT_SCALE_Y) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.SCALE_Y;
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "scaleY";
                    }
                };
            }
            return scaleY;
        }

        public double getScaleZ() {
            return (scaleZ == null) ? DEFAULT_SCALE_Z : scaleZ.get();
        }

        public final DoubleProperty scaleZProperty() {
            if (scaleZ == null) {
                scaleZ = new StyleableDoubleProperty(DEFAULT_SCALE_Z) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.SCALE_Z;
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "scaleZ";
                    }
                };
            }
            return scaleZ;
        }

        public double getRotate() {
            return (rotate == null) ? DEFAULT_ROTATE : rotate.get();
        }

        public final DoubleProperty rotateProperty() {
            if (rotate == null) {
                rotate = new StyleableDoubleProperty(DEFAULT_ROTATE) {
                    @Override
                    public void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.ROTATE;
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "rotate";
                    }
                };
            }
            return rotate;
        }

        public Point3D getRotationAxis() {
            return (rotationAxis == null) ? DEFAULT_ROTATION_AXIS
                                          : rotationAxis.get();
        }

        public final ObjectProperty<Point3D> rotationAxisProperty() {
            if (rotationAxis == null) {
                rotationAxis = new ObjectPropertyBase<Point3D>(
                                           DEFAULT_ROTATION_AXIS) {
                    @Override
                    protected void invalidated() {
                        impl_transformsChanged();
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "rotationAxis";
                    }
                };
            }
            return rotationAxis;
        }

        public ObservableList<Transform> getTransforms() {
            if (transforms == null) {
                transforms = new TrackableObservableList<Transform>() {
                    @Override
                    protected void onChanged(Change<Transform> c) {
                        while (c.next()) {
                            for (Transform t : c.getRemoved()) {
                                t.impl_remove(Node.this);
                            }
                            for (Transform t : c.getAddedSubList()) {
                                t.impl_add(Node.this);
                            }
                        }

                        impl_transformsChanged();
                    }
                };
            }

            return transforms;
        }

        public boolean canSetTranslateX() {
            return (translateX == null) || !translateX.isBound();
        }

        public boolean canSetTranslateY() {
            return (translateY == null) || !translateY.isBound();
        }

        public boolean canSetTranslateZ() {
            return (translateZ == null) || !translateZ.isBound();
        }

        public boolean canSetScaleX() {
            return (scaleX == null) || !scaleX.isBound();
        }

        public boolean canSetScaleY() {
            return (scaleY == null) || !scaleY.isBound();
        }

        public boolean canSetScaleZ() {
            return (scaleZ == null) || !scaleZ.isBound();
        }

        public boolean canSetRotate() {
            return (rotate == null) || !rotate.isBound();
        }

        public boolean hasTransforms() {
            return (transforms != null);
        }

    }

    ////////////////////////////
    //  Private Implementation
    ////////////////////////////

    /***************************************************************************
     *                                                                         *
     *                        Event Handler Properties                         *
     *                                                                         *
     **************************************************************************/

    private EventHandlerProperties eventHandlerProperties;

    private EventHandlerProperties getEventHandlerProperties() {
        if (eventHandlerProperties == null) {
            eventHandlerProperties =
                    new EventHandlerProperties(
                        getInternalEventDispatcher().getEventHandlerManager(),
                        this);
        }

        return eventHandlerProperties;
    }

    /***************************************************************************
     *                                                                         *
     *                       Misc Seldom Used Properties                       *
     *                                                                         *
     **************************************************************************/

    private MiscProperties miscProperties;

    private MiscProperties getMiscProperties() {
        if (miscProperties == null) {
            miscProperties = new MiscProperties();
        }

        return miscProperties;
    }

    private static final boolean DEFAULT_CACHE = false;
    private static final CacheHint DEFAULT_CACHE_HINT = CacheHint.DEFAULT;
    private static final Node DEFAULT_CLIP = null;
    private static final Cursor DEFAULT_CURSOR = null;
    private static final DepthTest DEFAULT_DEPTH_TEST = DepthTest.INHERIT;
    private static final boolean DEFAULT_DISABLE = false;
    private static final Effect DEFAULT_EFFECT = null;
    private static final InputMethodRequests DEFAULT_INPUT_METHOD_REQUESTS =
            null;
    private static final boolean DEFAULT_MOUSE_TRANSPARENT = false;

    private final class MiscProperties {
        private LazyBoundsProperty boundsInParent;
        private LazyBoundsProperty boundsInLocal;
        private BooleanProperty cache;
        private ObjectProperty<CacheHint> cacheHint;
        private ObjectProperty<Node> clip;
        private ObjectProperty<Cursor> cursor;
        private ObjectProperty<DepthTest> depthTest;
        private BooleanProperty disable;
        private ObjectProperty<Effect> effect;
        private ObjectProperty<InputMethodRequests> inputMethodRequests;
        private BooleanProperty mouseTransparent;

        public final Bounds getBoundsInParent() {
            return boundsInParentProperty().get();
        }

        public final ReadOnlyObjectProperty<Bounds> boundsInParentProperty() {
            if (boundsInParent == null) {
                boundsInParent = new LazyBoundsProperty() {
                    /**
                     * Computes the bounds including the clip, effects, and all
                     * transforms. This function is essentially how to compute
                     * the boundsInParent. Optimizations are made to compute as
                     * little as possible and create as little trash as
                     * possible.
                     */
                    @Override
                    protected Bounds computeBounds() {
                        BaseBounds tempBounds = TempState.getInstance().bounds;
                        tempBounds = getTransformedBounds(
                                             tempBounds,
                                             BaseTransform.IDENTITY_TRANSFORM);
                        return new BoundingBox(tempBounds.getMinX(),
                                               tempBounds.getMinY(),
                                               tempBounds.getMinZ(),
                                               tempBounds.getWidth(),
                                               tempBounds.getHeight(),
                                               tempBounds.getDepth());
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "boundsInParent";
                    }
                };
            }

            return boundsInParent;
        }

        public void invalidateBoundsInParent() {
            if (boundsInParent != null) {
                boundsInParent.invalidate();
            }
        }

        public final Bounds getBoundsInLocal() {
            return boundsInLocalProperty().get();
        }

        public final ReadOnlyObjectProperty<Bounds> boundsInLocalProperty() {
            if (boundsInLocal == null) {
                boundsInLocal = new LazyBoundsProperty() {
                    @Override
                    protected Bounds computeBounds() {
                        BaseBounds tempBounds = TempState.getInstance().bounds;
                        tempBounds = getLocalBounds(
                                             tempBounds,
                                             BaseTransform.IDENTITY_TRANSFORM);
                        return new BoundingBox(tempBounds.getMinX(),
                                               tempBounds.getMinY(),
                                               tempBounds.getMinZ(),
                                               tempBounds.getWidth(),
                                               tempBounds.getHeight(),
                                               tempBounds.getDepth());
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "boundsInLocal";
                    }
                };
            }

            return boundsInLocal;
        }

        public void invalidateBoundsInLocal() {
            if (boundsInLocal != null) {
                boundsInLocal.invalidate();
            }
        }

        public final boolean isCache() {
            return (cache == null) ? DEFAULT_CACHE
                                   : cache.get();
        }

        public final BooleanProperty cacheProperty() {
            if (cache == null) {
                cache = new BooleanPropertyBase(DEFAULT_CACHE) {
                    @Override
                    protected void invalidated() {
                        impl_markDirty(DirtyBits.NODE_CACHE);
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "cache";
                    }
                };
            }
            return cache;
        }

        public final CacheHint getCacheHint() {
            return (cacheHint == null) ? DEFAULT_CACHE_HINT
                                       : cacheHint.get();
        }

        public final ObjectProperty<CacheHint> cacheHintProperty() {
            if (cacheHint == null) {
                cacheHint = new ObjectPropertyBase<CacheHint>(DEFAULT_CACHE_HINT) {
                    @Override
                    protected void invalidated() {
                        impl_markDirty(DirtyBits.NODE_CACHE);
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "cacheHint";
                    }
                };
            }
            return cacheHint;
        }

        public final Node getClip() {
            return (clip == null) ? DEFAULT_CLIP : clip.get();
        }

        public final ObjectProperty<Node> clipProperty() {
            if (clip == null) {
                clip = new ObjectPropertyBase<Node>(DEFAULT_CLIP) {

                    //temp variables used when clip was invalid to rollback to
                    // last value
                    private Node oldClip;

                    @Override
                    protected void invalidated() {
                        final Node newClip = get();
                        if ((newClip != null)
                                && ((newClip.isConnected()
                                           && newClip.clipParent != Node.this)
                                       || wouldCreateCycle(Node.this,
                                                           newClip))) {
                            // Assigning this node to clip is illegal.
                            // Roll back to the previous state and throw an
                            // exception.
                            final String cause =
                                    newClip.isConnected()
                                        && (newClip.clipParent != Node.this)
                                            ? "node already connected"
                                            : "cycle detected";

                            if (isBound()) {
                                unbind();
                                set(oldClip);
                                throw new IllegalArgumentException(
                                        "Node's clip set to incorrect value "
                                            + " through binding"
                                            + " (" + cause + ", node  = "
                                                   + Node.this + ", clip = "
                                                   + clip + ")."
                                            + " Binding has been removed.");
                            } else {
                                set(oldClip);
                                throw new IllegalArgumentException(
                                        "Node's clip set to incorrect value"
                                            + " (" + cause + ", node  = "
                                                   + Node.this + ", clip = "
                                                   + clip + ").");
                            }
                        } else {
                            if (oldClip != null) {
                                oldClip.clipParent = null;
                                oldClip.setScene(null);
                            }

                            if (newClip != null) {
                                newClip.clipParent = Node.this;
                                newClip.setScene(getScene());
                            }

                            impl_markDirty(DirtyBits.NODE_CLIP);

                            // the local bounds have (probably) changed
                            localBoundsChanged();

                            oldClip = newClip;
                        }
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "clip";
                    }
                };
            }
            return clip;
        }

        public final Cursor getCursor() {
            return (cursor == null) ? DEFAULT_CURSOR : cursor.get();
        }

        public final ObjectProperty<Cursor> cursorProperty() {
            if (cursor == null) {
                cursor = new StyleableObjectProperty<Cursor>(DEFAULT_CURSOR) {

                    @Override 
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.CURSOR;
                    }
                    
                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "cursor";
                    }
                    
                };
            }
            return cursor;
        }

        public final DepthTest getDepthTest() {
            return (depthTest == null) ? DEFAULT_DEPTH_TEST
                                       : depthTest.get();
        }

        public final ObjectProperty<DepthTest> depthTestProperty() {
            if (depthTest == null) {
                depthTest = new ObjectPropertyBase<DepthTest>(DEFAULT_DEPTH_TEST) {
                    @Override protected void invalidated() {
                        computeDerivedDepthTest();
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "depthTest";
                    }
                };
            }
            return depthTest;
        }

        public final boolean isDisable() {
            return (disable == null) ? DEFAULT_DISABLE : disable.get();
        }

        public final BooleanProperty disableProperty() {
            if (disable == null) {
                disable = new BooleanPropertyBase(DEFAULT_DISABLE) {
                    @Override
                    protected void invalidated() {
                        updateDisabled();
                    }

                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "disable";
                    }
                };
            }
            return disable;
        }

        public final Effect getEffect() {
            return (effect == null) ? DEFAULT_EFFECT : effect.get();
        }

        public final ObjectProperty<Effect> effectProperty() {
            if (effect == null) {
                effect = new StyleableObjectProperty<Effect>(DEFAULT_EFFECT) {
                    private Effect oldEffect = null;
                    private int oldBits;

                    private final AbstractNotifyListener effectChangeListener =
                            new AbstractNotifyListener() {                        

                        @Override
                        public void invalidated(Observable valueModel) {
                            int newBits = ((IntegerProperty) valueModel).get();
                            int changedBits = newBits ^ oldBits;
                            oldBits = newBits;
                            if (EffectDirtyBits.isSet(
                                    changedBits,
                                    EffectDirtyBits.EFFECT_DIRTY)
                                && EffectDirtyBits.isSet(
                                       newBits,
                                       EffectDirtyBits.EFFECT_DIRTY)) {
                                impl_markDirty(DirtyBits.EFFECT_EFFECT);
                            }
                            if (EffectDirtyBits.isSet(
                                    changedBits,
                                    EffectDirtyBits.BOUNDS_CHANGED)) {
                                localBoundsChanged();
                            }
                        }
                    };

                    @Override
                    protected void invalidated() {
                        Effect _effect = get();
                        if (oldEffect != null) {
                            oldEffect.impl_effectDirtyProperty().removeListener(
                                    effectChangeListener.getWeakListener());
                        }
                        oldEffect = _effect;
                        if (_effect != null) {
                            _effect.impl_effectDirtyProperty()
                                   .addListener(
                                       effectChangeListener.getWeakListener());
                            if (_effect.impl_isEffectDirty()) {
                                impl_markDirty(DirtyBits.EFFECT_EFFECT);
                            }
                            oldBits = _effect.impl_effectDirtyProperty().get();
                        }

                        impl_markDirty(DirtyBits.NODE_EFFECT);
                        // bounds may have changed regardeless whether
                        // the dirty flag on efffect is set
                        localBoundsChanged();
                    }

                    @Override 
                    public StyleableProperty getStyleableProperty() {
                        return StyleableProperties.EFFECT;
                    }
                    
                    @Override
                    public Object getBean() {
                        return Node.this;
                    }

                    @Override
                    public String getName() {
                        return "effect";
                    }
                };
            }
            return effect;
        }

        public final InputMethodRequests getInputMethodRequests() {
            return (inputMethodRequests == null) ? DEFAULT_INPUT_METHOD_REQUESTS
                                                 : inputMethodRequests.get();
        }

        public ObjectProperty<InputMethodRequests>
                inputMethodRequestsProperty() {
            if (inputMethodRequests == null) {
                inputMethodRequests =
                        new SimpleObjectProperty<InputMethodRequests>(
                                Node.this,
                                "inputMethodRequests",
                                DEFAULT_INPUT_METHOD_REQUESTS);
            }
            return inputMethodRequests;
        }

        public final boolean isMouseTransparent() {
            return (mouseTransparent == null) ? DEFAULT_MOUSE_TRANSPARENT
                                              : mouseTransparent.get();
        }

        public final BooleanProperty mouseTransparentProperty() {
            if (mouseTransparent == null) {
                mouseTransparent =
                        new SimpleBooleanProperty(
                                Node.this,
                                "mouseTransparent",
                                DEFAULT_MOUSE_TRANSPARENT);
            }
            return mouseTransparent;
        }

        public boolean canSetCursor() {
            return (cursor == null) || !cursor.isBound();
        }

        public boolean canSetEffect() {
            return (effect == null) || !effect.isBound();
        }
    }

    /* *************************************************************************
     *                                                                         *
     *                             Mouse Handling                              *
     *                                                                         *
     **************************************************************************/

    public final void setMouseTransparent(boolean value) {
        mouseTransparentProperty().set(value);
    }

    public final boolean isMouseTransparent() {
        return (miscProperties == null) ? DEFAULT_MOUSE_TRANSPARENT
                                        : miscProperties.isMouseTransparent();
    }

    /**
     * If {@code true}, this node (together with all its children) is completely
     * transparent to mouse events. When choosing target for mouse event, nodes
     * with {@code mouseTransparent} set to {@code true} and their subtrees
     * won't be taken into account.
     */
    public final BooleanProperty mouseTransparentProperty() {
        return getMiscProperties().mouseTransparentProperty();
    }

    /**
     * Whether or not this {@code Node} is being hovered over. Typically this is
     * due to the mouse being over the node, though it could be due to a pen
     * hovering on a graphics tablet or other form of input.
     *
     * the <p>NOTE current implementation of hover relies on mouse enter and
     * exit events to determine whether this Node is in the hover state; this
     * means that this feature is currently supported only on systems that
     * have a mouse. Future implementations may provide alternative means of
     * supporting hover.
     *
     * @profile common
     * @defaultvalue false
     */
    private ReadOnlyBooleanWrapper hover;

    protected final void setHover(boolean value) {
        hoverPropertyImpl().set(value);
    }

    public final boolean isHover() {
        return hover == null ? false : hover.get();
    }

    public final ReadOnlyBooleanProperty hoverProperty() {
        return hoverPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyBooleanWrapper hoverPropertyImpl() {
        if (hover == null) {
            hover = new ReadOnlyBooleanWrapper() {

                @Override
                protected void invalidated() {
                    PlatformLogger logger = Logging.getInputLogger();
                    if (logger.isLoggable(PlatformLogger.FINER)) {
                        logger.finer(this + " hover=" + get());
                    }
                    impl_pseudoClassStateChanged("hover");
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "hover";
                }
            };
        }
        return hover;
    }

    /**
     * Whether or not the {@code Node} is pressed. Typically this is true when
     * the primary mouse button is down, though subclasses may define other
     * mouse button state or key state to cause the node to be "pressed".
     *
     * @profile common
     * @defaultvalue false
     */
    private ReadOnlyBooleanWrapper pressed;

    protected final void setPressed(boolean value) {
        pressedPropertyImpl().set(value);
    }

    public final boolean isPressed() {
        return pressed == null ? false : pressed.get();
    }

    public final ReadOnlyBooleanProperty pressedProperty() {
        return pressedPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyBooleanWrapper pressedPropertyImpl() {
        if (pressed == null) {
            pressed = new ReadOnlyBooleanWrapper() {

                @Override
                protected void invalidated() {
                    PlatformLogger logger = Logging.getInputLogger();
                    if (logger.isLoggable(PlatformLogger.FINER)) {
                        logger.finer(this + " pressed=" + get());
                    }
                    impl_pseudoClassStateChanged("pressed");
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "pressed";
                }
            };
        }
        return pressed;
    }

    public final void setOnContextMenuRequested(
            EventHandler<? super ContextMenuEvent> value) {
        onContextMenuRequestedProperty().set(value);
    }

    public final EventHandler<? super ContextMenuEvent> getOnContextMenuRequested() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.onContextMenuRequested();
    }
    
    /**
     * Defines a function to be called when a context menu
     * has been requested on this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super ContextMenuEvent>>
            onContextMenuRequestedProperty() {
        return getEventHandlerProperties().onContextMenuRequestedProperty();
    }

    public final void setOnMouseClicked(
            EventHandler<? super MouseEvent> value) {
        onMouseClickedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseClicked() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseClicked();
    }

    /**
     * Defines a function to be called when a mouse button has been clicked
     * (pressed and released) on this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMouseClickedProperty() {
        return getEventHandlerProperties().onMouseClickedProperty();
    }

    public final void setOnMouseDragged(
            EventHandler<? super MouseEvent> value) {
        onMouseDraggedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseDragged() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseDragged();
    }

    /**
     * Defines a function to be called when a mouse button is pressed
     * on this {@code Node} and then dragged.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMouseDraggedProperty() {
        return getEventHandlerProperties().onMouseDraggedProperty();
    }

    public final void setOnMouseEntered(
            EventHandler<? super MouseEvent> value) {
        onMouseEnteredProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseEntered() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseEntered();
    }

    /**
     * Defines a function to be called when the mouse enters this {@code Node}.
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMouseEnteredProperty() {
        return getEventHandlerProperties().onMouseEnteredProperty();
    }

    public final void setOnMouseExited(
            EventHandler<? super MouseEvent> value) {
        onMouseExitedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseExited() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseExited();
    }

    /**
     * Defines a function to be called when the mouse exits this {@code Node}.
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMouseExitedProperty() {
        return getEventHandlerProperties().onMouseExitedProperty();
    }

    public final void setOnMouseMoved(
            EventHandler<? super MouseEvent> value) {
        onMouseMovedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseMoved() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseMoved();
    }

    /**
     * Defines a function to be called when mouse cursor moves within
     * this {@code Node} but no buttons have been pushed.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMouseMovedProperty() {
        return getEventHandlerProperties().onMouseMovedProperty();
    }

    public final void setOnMousePressed(
            EventHandler<? super MouseEvent> value) {
        onMousePressedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMousePressed() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMousePressed();
    }

    /**
     * Defines a function to be called when a mouse button
     * has been pressed on this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMousePressedProperty() {
        return getEventHandlerProperties().onMousePressedProperty();
    }

    public final void setOnMouseReleased(
            EventHandler<? super MouseEvent> value) {
        onMouseReleasedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnMouseReleased() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseReleased();
    }

    /**
     * Defines a function to be called when a mouse button
     * has been released on this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onMouseReleasedProperty() {
        return getEventHandlerProperties().onMouseReleasedProperty();
    }

    public final void setOnDragDetected(
            EventHandler<? super MouseEvent> value) {
        onDragDetectedProperty().set(value);
    }

    public final EventHandler<? super MouseEvent> getOnDragDetected() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnDragDetected();
    }

    /**
     * Defines a function to be called when drag gesture has been
     * detected. This is the right place to start drag and drop operation.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseEvent>>
            onDragDetectedProperty() {
        return getEventHandlerProperties().onDragDetectedProperty();
    }

   public final void setOnScroll(
            EventHandler<? super ScrollEvent> value) {
        onScrollProperty().set(value);
    }

    public final EventHandler<? super ScrollEvent> getOnScroll() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnScroll();
    }

    /**
     * Defines a function to be called when user performs a scrolling action. 
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super ScrollEvent>>
            onScrollProperty() {
        return getEventHandlerProperties().onScrollProperty();
    }

    public final void setOnMouseDragOver(
            EventHandler<? super MouseDragEvent> value) {
        onMouseDragOverProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragOver() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseDragOver();
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * progresses within this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseDragEvent>>
            onMouseDragOverProperty() {
        return getEventHandlerProperties().onMouseDragOverProperty();
    }

    public final void setOnMouseDragReleased(
            EventHandler<? super MouseDragEvent> value) {
        onMouseDragReleasedProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragReleased() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseDragReleased();
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * ends (by releasing mouse button) within this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseDragEvent>>
            onMouseDragReleasedProperty() {
        return getEventHandlerProperties().onMouseDragReleasedProperty();
    }

    public final void setOnMouseDragEntered(
            EventHandler<? super MouseDragEvent> value) {
        onMouseDragEnteredProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragEntered() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseDragEntered();
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * enters this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseDragEvent>>
            onMouseDragEnteredProperty() {
        return getEventHandlerProperties().onMouseDragEnteredProperty();
    }

    public final void setOnMouseDragExited(
            EventHandler<? super MouseDragEvent> value) {
        onMouseDragExitedProperty().set(value);
    }

    public final EventHandler<? super MouseDragEvent> getOnMouseDragExited() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnMouseDragExited();
    }

    /**
     * Defines a function to be called when a full press-drag-release gesture
     * leaves this {@code Node}.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super MouseDragEvent>>
            onMouseDragExitedProperty() {
        return getEventHandlerProperties().onMouseDragExitedProperty();
    }


    /* *************************************************************************
     *                                                                         *
     *                           Keyboard Handling                             *
     *                                                                         *
     **************************************************************************/

    public final void setOnKeyPressed(
            EventHandler<? super KeyEvent> value) {
        onKeyPressedProperty().set(value);
    }

    public final EventHandler<? super KeyEvent> getOnKeyPressed() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnKeyPressed();
    }

    /**
     * Defines a function to be called when this {@code Node} or its child
     * {@code Node} has input focus and a key has been pressed. The function
     * is called only if the event hasn't been already consumed during its
     * capturing or bubbling phase.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super KeyEvent>>
            onKeyPressedProperty() {
        return getEventHandlerProperties().onKeyPressedProperty();
    }

    public final void setOnKeyReleased(
            EventHandler<? super KeyEvent> value) {
        onKeyReleasedProperty().set(value);
    }

    public final EventHandler<? super KeyEvent> getOnKeyReleased() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnKeyReleased();
    }

    /**
     * Defines a function to be called when this {@code Node} or its child
     * {@code Node} has input focus and a key has been released. The function
     * is called only if the event hasn't been already consumed during its
     * capturing or bubbling phase.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super KeyEvent>>
            onKeyReleasedProperty() {
        return getEventHandlerProperties().onKeyReleasedProperty();
    }

    public final void setOnKeyTyped(
            EventHandler<? super KeyEvent> value) {
        onKeyTypedProperty().set(value);
    }

    public final EventHandler<? super KeyEvent> getOnKeyTyped() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnKeyTyped();
    }

    /**
     * Defines a function to be called when this {@code Node} or its child
     * {@code Node} has input focus and a key has been typed. The function
     * is called only if the event hasn't been already consumed during its
     * capturing or bubbling phase.
     *
     * @profile common
     */
    public final ObjectProperty<EventHandler<? super KeyEvent>>
            onKeyTypedProperty() {
        return getEventHandlerProperties().onKeyTypedProperty();
    }

    /* *************************************************************************
     *                                                                         *
     *                           Input Method Handling                         *
     *                                                                         *
     **************************************************************************/

    public final void setOnInputMethodTextChanged(
            EventHandler<? super InputMethodEvent> value) {
        onInputMethodTextChangedProperty().set(value);
    }

    public final EventHandler<? super InputMethodEvent>
            getOnInputMethodTextChanged() {
        return (eventHandlerProperties == null)
                ? null : eventHandlerProperties.getOnInputMethodTextChanged();
    }

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
    public final ObjectProperty<EventHandler<? super InputMethodEvent>>
            onInputMethodTextChangedProperty() {
        return getEventHandlerProperties().onInputMethodTextChangedProperty();
    }

    public final void setInputMethodRequests(InputMethodRequests value) {
        inputMethodRequestsProperty().set(value);
    }

    public final InputMethodRequests getInputMethodRequests() {
        return (miscProperties == null)
                       ? DEFAULT_INPUT_METHOD_REQUESTS
                       : miscProperties.getInputMethodRequests();
    }

    /**
     * Property holding InputMethodRequests.
     *
     * @return InputMethodRequestsProperty
     */
    public final ObjectProperty<InputMethodRequests> inputMethodRequestsProperty() {
        return getMiscProperties().inputMethodRequestsProperty();
    }

    /***************************************************************************
     *                                                                         *
     *                             Focus Traversal                             *
     *                                                                         *
     **************************************************************************/
    /**
     * Indicates whether this {@code Node} currently has the input focus.
     * To have the input focus, a node must be the {@code Scene}'s focus
     * owner, and the scene must be in a {@code Stage} that is visible
     * and active. See {@code requestFocus()} for more information.
     *
     * @profile common
     * @defaultvalue false
     */
    private ReadOnlyBooleanWrapper focused;

    protected final void setFocused(boolean value) {
        focusedPropertyImpl().set(value);
    }

    public final boolean isFocused() {
        return focused == null ? false : focused.get();
    }

    public final ReadOnlyBooleanProperty focusedProperty() {
        return focusedPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyBooleanWrapper focusedPropertyImpl() {
        if (focused == null) {
            focused = new ReadOnlyBooleanWrapper() {

                @Override
                protected void invalidated() {
                    impl_pseudoClassStateChanged("focused");
                    PlatformLogger logger = Logging.getFocusLogger();
                    if (logger.isLoggable(PlatformLogger.FINE)) {
                        logger.fine(this + " focused=" + get());
                    }
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "focused";
                }
            };
        }
        return focused;
    }

    /**
     * Specifies whether this {@code Node} should be a part of focus traversal
     * cycle. When this property is {@code true} focus can be moved to this
     * {@code Node} and from this {@code Node} using regular focus traversal
     * keys. On a desktop such keys are usually {@code TAB} for moving focus
     * forward and {@code SHIFT+TAB} for moving focus backward.
     *
     * When a {@code Scene} is created, the system gives focus to a
     * {@code Node} whose {@code focusTraversable} variable is true
     * and that is eligible to receive the focus,
     * unless the focus had been set explicitly via a call
     * to {@code requestFocus()}.
     *
     * @profile common
     * @defaultvalue false
     */
    private BooleanProperty focusTraversable;

    public final void setFocusTraversable(boolean value) {
        focusTraversableProperty().set(value);
    }
    public final boolean isFocusTraversable() {
        return focusTraversable == null ? false : focusTraversable.get();
    }

    public final BooleanProperty focusTraversableProperty() {
        if (focusTraversable == null) {
            focusTraversable = new StyleableBooleanProperty() {

                @Override
                public void invalidated() {
                    Scene _scene = getScene();
                    if (_scene != null) {
                        if (get()) {
                            _scene.registerTraversable(Node.this);
                        } else {
                            _scene.unregisterTraversable(Node.this);
                        }
                        focusSetDirty(_scene);
                    }
                }
                
                @Override
                public StyleableProperty getStyleableProperty() {
                    return StyleableProperties.FOCUS_TRAVERSABLE;
                }

                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "focusTraversable";
                }
            };
        }
        return focusTraversable;
    }

    /**
     * Called when something has changed on this node that *may* have made the
     * scene's focus dirty. This covers the cases where this node is the focus
     * owner and it may have lost eligibility, or it's traversable and it may
     * have gained eligibility. Note that we do not want to use disabled
     * or treeVisible here, as this function is called from their
     * "on invalidate" triggers, and using them will cause them to be
     * revalidated. The pulse will revalidate everything and make the final
     * determination.
     */
    private void focusSetDirty(Scene s) {
        if (s != null &&
            (this == s.impl_getFocusOwner() || isFocusTraversable())) {
                s.setFocusDirty(true);
        }
    }

    /**
     * Requests that this {@code Node} get the input focus, and that this
     * {@code Node}'s top-level ancestor become the focused window. To be
     * eligible to receive the focus, the node must be part of a scene, it and
     * all of its ancestors must be visible, and it must not be disabled.
     * If this node is eligible, this function will cause it to become this
     * {@code Scene}'s "focus owner". Each scene has at most one focus owner
     * node. The focus owner will not actually have the input focus, however,
     * unless the scene belongs to a {@code Stage} that is both visible
     * and active.
     *
     * @profile common
     */
    public void requestFocus() {
        if (getScene() != null) {
            getScene().requestFocus(this);
        }
    }

    /**
     * Some nodes require a special handling to request focus.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_requestFocusImpl(Runnable r) {
        r.run();
    }

    /**
     * Traverses from this node in the direction indicated. Note that this
     * node need not actually have the focus, nor need it be focusTraversable.
     * However, the node must be part of a scene, otherwise this request
     * is ignored.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_traverse(Direction dir) {
        if (getScene() == null) {
            return;
        }
        getScene().traverse(this, dir);
    }

    ////////////////////////////
    //  Private Implementation
    ////////////////////////////

     /**
      * Returns a string representation for the object.
      * @return a string representation for the object.
      */
    @Override
    public String toString() {
        String klassName = getClass().getName();
        String simpleName = klassName.substring(klassName.lastIndexOf(".")+1);
        StringBuilder sbuf = new StringBuilder(simpleName);
        boolean hasId = id != null && !"".equals(getId());
        boolean hasStyleClass = !getStyleClass().isEmpty();

        if (!hasId) {
            sbuf.append('@');
            sbuf.append(Integer.toHexString(hashCode()));
        } else {
            sbuf.append("[id=");
            sbuf.append(getId());
            if (!hasStyleClass) sbuf.append("]");
        }
        if (hasStyleClass) {
            if (!hasId) sbuf.append('[');
            else sbuf.append(", ");
            sbuf.append("styleClass=");
            sbuf.append(getStyleClass());
            sbuf.append("]");
        }
        return sbuf.toString();
    }

    private void preprocessMouseEvent(MouseEvent e) {
        final EventType<?> eventType = e.getEventType();
        if (eventType == MouseEvent.MOUSE_PRESSED) {
            for (Node n = this; n != null; n = n.getParent()) {
                n.setPressed(e.isPrimaryButtonDown());
            }
            return;
        }
        if (eventType == MouseEvent.MOUSE_RELEASED) {
            for (Node n = this; n != null; n = n.getParent()) {
                n.setPressed(e.isPrimaryButtonDown());
            }
            return;
        }

        if (e.getTarget() == this) {
            // the mouse event types are translated only when the node uses
            // its internal event dispatcher, so both entered / exited variants
            // are possible here

            if ((eventType == MouseEvent.MOUSE_ENTERED)
                    || (eventType == MouseEvent.MOUSE_ENTERED_TARGET)) {
                setHover(true);
                return;
            }

            if ((eventType == MouseEvent.MOUSE_EXITED)
                    || (eventType == MouseEvent.MOUSE_EXITED_TARGET)) {
                setHover(false);
                return;
            }
        }
    }

    private void updateTreeVisible() {
        setTreeVisible(isVisible() && ((getParent() == null) || getParent().impl_isTreeVisible()));
    }

    private boolean treeVisible;
    private TreeVisiblePropertyReadOnly treeVisibleRO;

    private void setTreeVisible(boolean value) {
        if (treeVisible != value) {
            treeVisible = value;
            updateCanReceiveFocus();
            focusSetDirty(getScene());
            ((TreeVisiblePropertyReadOnly)treeVisibleProperty()).invalidate();
        }
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public boolean impl_isTreeVisible() {
        return treeVisibleProperty().get();
    }

    BooleanExpression treeVisibleProperty() {
        if (treeVisibleRO == null) {
            treeVisibleRO = new TreeVisiblePropertyReadOnly();
        }
        return treeVisibleRO;
    }

    class TreeVisiblePropertyReadOnly extends BooleanExpression {

        private ExpressionHelper<Boolean> helper;
        private boolean valid;

        @Override 
        public void addListener(InvalidationListener listener) {
            helper = ExpressionHelper.addListener(helper, this, listener);
        }

        @Override 
        public void removeListener(InvalidationListener listener) {
            helper = ExpressionHelper.removeListener(helper, listener);
        }
        
        @Override
        public void addListener(ChangeListener<? super Boolean> listener) {
            helper = ExpressionHelper.addListener(helper, this, listener);
        }

        @Override 
        public void removeListener(ChangeListener<? super Boolean> listener) {
            helper = ExpressionHelper.removeListener(helper, listener);
        }
        
        protected void invalidate() {
            if (valid) {
                valid = false;
                ExpressionHelper.fireValueChangedEvent(helper);
            }
        }

        @Override
        public boolean get() {
            valid = true;
            return Node.this.treeVisible;
        }

    }

    private boolean canReceiveFocus = false;

    private void setCanReceiveFocus(boolean value) {
        canReceiveFocus = value;
    }

    final boolean isCanReceiveFocus() {
        return canReceiveFocus;
    }

    private void updateCanReceiveFocus() {
        setCanReceiveFocus(getScene() != null
          && !isDisabled()
          && impl_isTreeVisible());
    }

    // for indenting messages based on scene-graph depth
    String indent() {
        String indent = "";
        Parent p = this.getParent();
        while (p != null) {
            indent += "  ";
            p = p.getParent();
        }
        return indent;
    }




    /**
     * Should we underline the mnemonic character?
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    private BooleanProperty impl_showMnemonics;

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public final void impl_setShowMnemonics(boolean value) {
        impl_showMnemonicsProperty().set(value);
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public final boolean impl_isShowMnemonics() {
        return impl_showMnemonics == null ? false : impl_showMnemonics.get();
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public BooleanProperty impl_showMnemonicsProperty() {
        if (impl_showMnemonics == null) {
            impl_showMnemonics = new BooleanPropertyBase(false) {

                @Override
                protected void invalidated() {
                    impl_pseudoClassStateChanged("show-mnemonics");
                }
                @Override
                public Object getBean() {
                    return Node.this;
                }

                @Override
                public String getName() {
                    return "showMnemonics";
                }
            };
        }
        return impl_showMnemonics;
    }





    /***************************************************************************
     *                                                                         *
     *                         Event Dispatch                                  *
     *                                                                         *
     **************************************************************************/

    // PENDING_DOC_REVIEW
    /**
     * Specifies the event dispatcher for this node. The default event
     * dispatcher sends the received events to the registered event handlers and
     * filters. When replacing the value with a new {@code EventDispatcher},
     * the new dispatcher should forward events to the replaced dispatcher
     * to maintain the node's default event handling behavior.
     */
    private ObjectProperty<EventDispatcher> eventDispatcher;

    public final void setEventDispatcher(EventDispatcher value) {
        eventDispatcherProperty().set(value);
    }

    public final EventDispatcher getEventDispatcher() {
        return eventDispatcherProperty().get();
    }

    public final ObjectProperty<EventDispatcher> eventDispatcherProperty() {
        initializeInternalEventDispatcher();
        return eventDispatcher;
    }

    private NodeEventDispatcher internalEventDispatcher;

    // PENDING_DOC_REVIEW
    /**
     * Registers an event handler to this node. The handler is called when the
     * node receives an {@code Event} of the specified type during the bubbling
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
     * Unregisters a previously registered event handler from this node. One
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
        getInternalEventDispatcher()
                .getEventHandlerManager()
                .removeEventHandler(eventType, eventHandler);
    }

    // PENDING_DOC_REVIEW
    /**
     * Registers an event filter to this node. The filter is called when the
     * node receives an {@code Event} of the specified type during the capturing
     * phase of event delivery.
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
     * Unregisters a previously registered event filter from this node. One
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
     * Sets the handler to use for this event type. There can only be one such handler
     * specified at a time. This handler is guaranteed to be called first. This is
     * used for registering the user-defined onFoo event handlers.
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

    private NodeEventDispatcher getInternalEventDispatcher() {
        initializeInternalEventDispatcher();
        return internalEventDispatcher;
    }

    private void initializeInternalEventDispatcher() {
        if (internalEventDispatcher == null) {
            internalEventDispatcher = createInternalEventDispatcher();
            eventDispatcher = new SimpleObjectProperty<EventDispatcher>(
                                          Node.this,
                                          "eventDispatcher",
                                          internalEventDispatcher);
        }
    }

    private NodeEventDispatcher createInternalEventDispatcher() {
        return new NodeEventDispatcher(this);
    }

    /**
     * Event dispatcher for invoking preprocessing of mouse events
     *
     * TODO: The dispatcher is inserted into the event dispatch chain, this
     * needs to be solved differently, this way it's impossible for user
     * to override this behavior.
     */
    private EventDispatcher preprocessMouseEventDispatcher;

    // PENDING_DOC_REVIEW
    /**
     * Construct an event dispatch chain for this node. The event dispatch chain
     * contains all event dispatchers from the stage to this node.
     *
     * @param tail the initial chain to build from
     * @return the resulting event dispatch chain for this node
     */
    @Override
    public EventDispatchChain buildEventDispatchChain(
            EventDispatchChain tail) {

        if (preprocessMouseEventDispatcher == null) {
            preprocessMouseEventDispatcher = new EventDispatcher() {
                @Override
                public Event dispatchEvent(Event event,
                                           EventDispatchChain tail) {
                    event = tail.dispatchEvent(event);
                    if (event instanceof MouseEvent) {
                        preprocessMouseEvent((MouseEvent) event);
                    }

                    return event;
                }
            };
        }

        tail = tail.prepend(preprocessMouseEventDispatcher);

        // prepend all event dispatchers from this node to the root
        Node curNode = this;
        do {
            if (curNode.eventDispatcher != null) {
                tail = tail.prepend(curNode.eventDispatcher.get());
            }
            curNode = curNode.getParent();
        } while (curNode != null);

        if (getScene() != null) {
            // prepend scene's dispatch chain
            tail = getScene().buildEventDispatchChain(tail);
        }

        return tail;
    }

    // PENDING_DOC_REVIEW
    /**
     * Fires the specified event. By default the event will travel through the
     * hierarchy from the stage to this node. Any event filter encountered will
     * be notified and can consume the event. If not consumed by the filters,
     * the event handlers on this node are notified. If these don't consume the
     * event eighter, the event will travel back the same path it arrived to
     * this node. All event handlers encountered are called and can consume the
     * event.
     * <p>
     * This method must be called on the FX user thread.
     *
     * @param event the event to fire
     */
    public final void fireEvent(Event event) {

        /* Log input events.  We do a coarse filter for at least the FINE
         * level and then granularize from there.
         */
        if (event instanceof InputEvent) {
            PlatformLogger logger = Logging.getInputLogger();
            if (logger.isLoggable(PlatformLogger.FINE)) {
                EventType eventType = event.getEventType();
                if (eventType == MouseEvent.MOUSE_ENTERED ||
                    eventType == MouseEvent.MOUSE_EXITED) {
                    logger.finer(event.toString());
                } else if (eventType == MouseEvent.MOUSE_MOVED ||
                           eventType == MouseEvent.MOUSE_DRAGGED) {
                    logger.finest(event.toString());
                } else {
                    logger.fine(event.toString());
                }
            }
        }

        Event.fireEvent(this, event);
    }

    /***************************************************************************
     *                                                                         *
     *                         Stylesheet Handling                             *
     *                                                                         *
     **************************************************************************/

         
     /**
      * Super-lazy instantiation pattern from Bill Pugh.
      * @treatasprivate implementation detail
      */
     private static class StyleableProperties {
         
        private static final StyleableProperty<Node,Cursor> CURSOR =
            new StyleableProperty<Node,Cursor>("-fx-cursor", CursorConverter.getInstance()) {

                @Override
                public boolean isSettable(Node node) {
                    return node.miscProperties == null || node.miscProperties.canSetCursor();
                }

                @Override
                public WritableValue<Cursor> getWritableValue(Node node) {
                    return node.cursorProperty();
                }
            };
        private static final StyleableProperty<Node,Effect> EFFECT =
            new StyleableProperty<Node,Effect>("-fx-effect", EffectConverter.getInstance()) {

                @Override
                public boolean isSettable(Node node) {
                    return node.miscProperties == null || node.miscProperties.canSetEffect();
                }

                @Override
                public WritableValue<Effect> getWritableValue(Node node) {
                    return node.effectProperty();
                }
            };
        private static final StyleableProperty<Node,Boolean> FOCUS_TRAVERSABLE =
            new StyleableProperty<Node,Boolean>("-fx-focus-traversable", 
                BooleanConverter.getInstance(), Boolean.FALSE) {

                @Override
                public boolean isSettable(Node node) {
                    return node.focusTraversable == null || !node.focusTraversable.isBound();
                }

                @Override
                public BooleanProperty getWritableValue(Node node) {
                    return node.focusTraversableProperty();
                }
            };
        private static final StyleableProperty<Node,Number> OPACITY =
            new StyleableProperty<Node,Number>("-fx-opacity", 
                SizeConverter.getInstance(), 1.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.opacity == null || !node.opacity.isBound();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.opacityProperty();
                }
            };
        private static final StyleableProperty<Node,BlendMode> BLEND_MODE =
            new StyleableProperty<Node,BlendMode>("-fx-blend-mode", new EnumConverter<BlendMode>(BlendMode.class)) {

                @Override
                public boolean isSettable(Node node) {
                    return node.blendMode == null || !node.blendMode.isBound();
                }

                @Override
                public WritableValue<BlendMode> getWritableValue(Node node) {
                    return node.blendModeProperty();
                }
            };
        private static final StyleableProperty<Node,Number> ROTATE =
            new StyleableProperty<Node,Number>("-fx-rotate", 
                SizeConverter.getInstance(), 0.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.rotate == null 
                        || node.nodeTransformation.canSetRotate();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.rotateProperty();
                }
            };
        private static final StyleableProperty<Node,Number> SCALE_X =
            new StyleableProperty<Node,Number>("-fx-scale-x", 
                SizeConverter.getInstance(), 1.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.scaleX == null 
                        || node.nodeTransformation.canSetScaleX();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.scaleXProperty();
                }
            };
        private static final StyleableProperty<Node,Number> SCALE_Y =
            new StyleableProperty<Node,Number>("-fx-scale-y", 
                SizeConverter.getInstance(), 1.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.scaleY == null 
                        || node.nodeTransformation.canSetScaleY();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.scaleYProperty();
                }
            };
        private static final StyleableProperty<Node,Number> SCALE_Z =
            new StyleableProperty<Node,Number>("-fx-scale-z", 
                SizeConverter.getInstance(), 1.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.scaleZ == null 
                        || node.nodeTransformation.canSetScaleZ();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.scaleZProperty();
                }
            };
        private static final StyleableProperty<Node,Number> TRANSLATE_X =
            new StyleableProperty<Node,Number>("-fx-translate-x", 
                SizeConverter.getInstance(), 0.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.translateX == null 
                        || node.nodeTransformation.canSetTranslateX();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.translateXProperty();
                }
            };
        private static final StyleableProperty<Node,Number> TRANSLATE_Y =
            new StyleableProperty<Node,Number>("-fx-translate-y", 
                SizeConverter.getInstance(), 0.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.translateY == null 
                        || node.nodeTransformation.canSetTranslateY();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.translateYProperty();
                }
            };
        private static final StyleableProperty<Node,Number> TRANSLATE_Z =
            new StyleableProperty<Node,Number>("-fx-translate-z", 
                SizeConverter.getInstance(), 0.0) {

                @Override
                public boolean isSettable(Node node) {
                    return node.nodeTransformation == null
                        || node.nodeTransformation.translateZ == null 
                        || node.nodeTransformation.canSetTranslateZ();
                }

                @Override
                public WritableValue<Number> getWritableValue(Node node) {
                    return node.translateZProperty();
                }
            };
        private static final StyleableProperty<Node,Boolean> VISIBILITY =
            new StyleableProperty<Node,Boolean>("visibility", 
                new StyleConverter<String,Boolean>() {

                    @Override
                    // [ visible | hidden | collapse | inherit ]
                    public Boolean convert(ParsedValue<String, Boolean> value, Font font) {
                        final String sval = value != null ? value.getValue() : null;
                        return "visible".equalsIgnoreCase(sval);
                    }

                },
                Boolean.TRUE) {
                    
                @Override
                public boolean isSettable(Node node) {
                    return node.visible == null || !node.visible.isBound();
                }

                @Override
                public WritableValue<Boolean> getWritableValue(Node node) {
                    return node.visibleProperty();
                }
            };

         private static final List<StyleableProperty> STYLEABLES;

         static {

             final List<StyleableProperty> styleables = new ArrayList<StyleableProperty>();
             Collections.addAll(styleables, 
                 CURSOR,
                 EFFECT,
                 FOCUS_TRAVERSABLE,
                 OPACITY,
                 BLEND_MODE,
                 ROTATE,
                 SCALE_X,
                 SCALE_Y,
                 SCALE_Z,
                 TRANSLATE_X,
                 TRANSLATE_Y,
                 TRANSLATE_Z,
                 VISIBILITY);
             STYLEABLES = Collections.unmodifiableList(styleables);

         }
    }

     /**
      * Super-lazy instantiation pattern from Bill Pugh. StyleableProperties is referenced
      * no earlier (and therefore loaded no earlier by the class loader) than
      * the moment that  impl_CSS_STYLEABLES() is called.
      *
      * @treatasprivate implementation detail
      * @deprecated This is an internal API that is not intended for use and will be removed in the next version
      */
     @Deprecated
     public static List<StyleableProperty> impl_CSS_STYLEABLES() {
         return Node.StyleableProperties.STYLEABLES;
     }

     /**
      * RT-17293
      * @treatasprivate implementation detail
      * @deprecated This is an experimental API that is not intended for use
      */
     private ObservableMap<WritableValue, List<Style>> styleMap;
     
     /**
      * RT-17293
      * @treatasprivate implementation detail
      * @deprecated This is an experimental API that is not intended for general use and is subject to change in future versions
      */
     public ObservableMap<WritableValue, List<Style>> impl_getStyleMap() {
         return styleMap;
     }

     /**
      * RT-17293
      * @treatasprivate implementation detail
      * @deprecated This is an experimental API that is not intended for general use and is subject to change in future versions
      */
     public void impl_setStyleMap(ObservableMap<WritableValue, List<Style>> styleMap) {
         this.styleMap = styleMap;
     }
          
    /**
     * Hook for SkinnablePopup
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public Class impl_getClassToStyle() {
        return getClass();
    }

    /**
     * Flags used to indicate in which way this node is dirty (or whether it
     * is clean) and what must happen during the next CSS cycle on the
     * scenegraph.
     */
    CSSFlags cssFlag = CSSFlags.CLEAN;

    /**
     * A Reference to a StyleHelper for this node.
     * A StyleHelper contains all the css styles for this node
     * and knows how to apply them when our state changes.
     */
    private java.lang.ref.Reference<StyleHelper> styleHelperRef;
    
    /**
     * Needed for testing.
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    CSSFlags impl_getCSSFlags() { return cssFlag; }

    /**
     * Used to specify that the list of which pseudoclasses apply to this
     * Node has changed. The given parameter is the name of the pseudoclass
     * that has changed.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected void impl_pseudoClassStateChanged(String pseudoClass) {
        // If there is no scene, then we cannot make it dirty, so we'll leave
        // the flag alone
        if (getScene() == null) return;
        // Don't bother doing anything if the cssFlag is not CLEAN.
        // If the flag indicates a DIRTY_BRANCH, the flag might need to be
        // changed to UPDATE if the pseudoclass is used. This is necessary
        // to ensure that impl_processCSS is called on the node.
        if (cssFlag == CSSFlags.CLEAN || cssFlag == CSSFlags.DIRTY_BRANCH) {
            // We only want to mark the node as needing an UPDATE if the
            // pseudoclass is actually used by some CSS rule in the stylesheet.
            // If not, then we can safely ignore it
            if (isPseudoclassUsed(pseudoClass)) {
                cssFlag = CSSFlags.UPDATE;
                notifyParentsOfInvalidatedCSS();
            }
        }
    }

    // Walks up the tree telling each parent that the pseudo class state of
    // this node has changed.
    /** @treatasprivate */
    private void notifyParentsOfInvalidatedCSS() {
        if (!getScene().getRoot().impl_isDirty(DirtyBits.NODE_CSS)) {
            // Ensure that Scene.root is marked as dirty. If the scene isn't
            // dirty, nothing will get repainted. This bit is cleared from
            // Scene in doCSSPass().
            getScene().getRoot().impl_markDirty(DirtyBits.NODE_CSS);
        }
        Parent parent = getParent();
        while (parent != null) {
            if (parent.cssFlag == CSSFlags.CLEAN) {
                parent.cssFlag = CSSFlags.DIRTY_BRANCH;
                parent = parent.getParent();
            } else {
                parent = null;
            }
        }
    }

    // this function primarily exists as a hook to aid in testing
    boolean isPseudoclassUsed(String pseudoclass) {
        final StyleHelper styleHelper = impl_getStyleHelper();
        return (styleHelper != null) ? styleHelper.isPseudoclassUsed(pseudoclass) : false;
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_reapplyCSS() {
        // If there is no scene, then we cannot make it dirty, so we'll leave
        // the flag alone
        if (getScene() == null) return;
        // If the css flag is already "REAPPLY", then do nothing
        if (cssFlag == CSSFlags.REAPPLY) return;
        // Update the flag
        cssFlag = CSSFlags.REAPPLY;

        // One idiom employed by developers is to, during the layout pass,
        // add or remove nodes from the scene. For example, a ScrollPane
        // might add scroll bars to itself if it determines during layout
        // that it needs them, or a ListView might add cells to itself if
        // it determines that it needs to. In such situations we must
        // apply the CSS immediately and not add it to the scene's queue
        // for deferred action.
        if (getParent() != null && getParent().performingLayout) {
            impl_processCSS(true);
        } else if (getScene() != null) {
            notifyParentsOfInvalidatedCSS();
        }
    }

    void processCSS() {
        switch (cssFlag) {
            case CLEAN:
                break;
            case DIRTY_BRANCH:
                Parent me = (Parent)this;
                // clear the flag first in case the flag is set to something
                // other than clean by downstream processing.
                me.cssFlag = CSSFlags.CLEAN;
                List<Node> children = me.getChildren();
                for (int i=0, max=children.size(); i<max; i++) {
                    children.get(i).processCSS();
                }
                break;
            case REAPPLY:
            case UPDATE:
            default:
                // I can just pass false here because the node will take
                // care of actually doing a REAPPLY if it has to
                impl_processCSS(false);
        }
    }

    /**
     * If invoked, will update / reapply styles from here on down. If reapply
     * is false, then we will only update from here on down, otherwise we will
     * do a full reapply.
     *
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public void impl_processCSS(boolean reapply) {

        StyleHelper styleHelper = null;
        // Create a new StyleHelper either if I am told I need to reapply
        // or if my own flag indicates I need to reapply
        if (reapply || (cssFlag == CSSFlags.REAPPLY)) {

            styleHelper = impl_createStyleHelper();

        } else {
            styleHelper = impl_getStyleHelper();
        }
        
        // Clear the flag first in case the flag is set to something
        // other than clean by downstream processing.
        cssFlag = CSSFlags.CLEAN;

        // Transition to the new state
        if (styleHelper != null) {
            styleHelper.transitionToState(this);
        }
    }

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    protected StyleHelper impl_createStyleHelper() {

        StyleHelper styleHelper = null;
        if (styleHelperRef != null && (styleHelper = styleHelperRef.get()) != null) {
            // if we're creating a StyleHelper when we've already got one,
            // then we're reapplying CSS. This means that the CSS structure
            // has changed for this node and the cached values are no longer
            // valid.
            styleHelper.clearCachedValues(styleCacheKeyRef);
        }

        styleHelperRef = StyleManager.getInstance().getStyleHelper(this);

        if (styleHelperRef != null) {
            styleHelper = styleHelperRef.get();
        }

        styleCacheKeyRef = null;
        return styleHelper;
    }

    /**
     * Get this nodes StyleHelper
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public StyleHelper impl_getStyleHelper() {
        StyleHelper helper = null;
        // If we had a helper before, but don't now, then this
        // node's StyleHelper was nuked by the StyleManager
        // and we need to create a new one.
        if (styleHelperRef != null && (helper = styleHelperRef.get()) == null) {

            helper = impl_createStyleHelper();

        }
        return helper;
    }

    private java.lang.ref.Reference<StyleCacheKey> styleCacheKeyRef;
    /**
     * Return a key for this Node's cached values in StyleHelper. The key is
     * unique to the set of StyleHelpers of this node and its parents'
     * StyleHelpers, but not necessarily unique to all Nodes.
     * @treatasprivate Implementation detail
     */
    public java.lang.ref.Reference<StyleCacheKey> impl_getStyleCacheKey() {

        final StyleHelper styleHelper = impl_getStyleHelper();

        // cannot have a cache key without a helper...
        if (styleHelper == null) {
            styleCacheKeyRef = null;
            return null;
        }

        //  we have a helper, do we have a key?
        if (styleCacheKeyRef == null || styleCacheKeyRef.get() == null) {
            styleCacheKeyRef = styleHelper.createStyleCacheKey(this);
        } 
        
        return styleCacheKeyRef;
    }

    private static final long HOVER_PSEUDOCLASS_STATE = StyleManager.getInstance().getPseudoclassMask("hover");
    private static final long PRESSED_PSEUDOCLASS_STATE = StyleManager.getInstance().getPseudoclassMask("pressed");
    private static final long DISABLED_PSEUDOCLASS_STATE = StyleManager.getInstance().getPseudoclassMask("disabled");
    private static final long FOCUSED_PSEUDOCLASS_STATE = StyleManager.getInstance().getPseudoclassMask("focused");
    private static final long SHOW_MNEMONICS_PSEUDOCLASS_STATE = StyleManager.getInstance().getPseudoclassMask("show-mnemonics");

    /**
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public long impl_getPseudoClassState() {
        long mask = 0;
        if(isHover()) mask |= HOVER_PSEUDOCLASS_STATE;
        if(isPressed()) mask |= PRESSED_PSEUDOCLASS_STATE;
        if(isDisabled()) mask |= DISABLED_PSEUDOCLASS_STATE;
        if(isFocused()) mask |= FOCUSED_PSEUDOCLASS_STATE;
        if(impl_isShowMnemonics()) mask |= SHOW_MNEMONICS_PSEUDOCLASS_STATE;
        return mask;
    }

    private static abstract class LazyBoundsProperty
            extends ReadOnlyObjectProperty<Bounds> {
        private ExpressionHelper<Bounds> helper;
        private boolean valid;

        private Bounds bounds;

        @Override 
        public void addListener(InvalidationListener listener) {
            helper = ExpressionHelper.addListener(helper, this, listener);
        }

        @Override 
        public void removeListener(InvalidationListener listener) {
            helper = ExpressionHelper.removeListener(helper, listener);
        }
        
        @Override
        public void addListener(ChangeListener<? super Bounds> listener) {
            helper = ExpressionHelper.addListener(helper, this, listener);
        }

        @Override 
        public void removeListener(ChangeListener<? super Bounds> listener) {
            helper = ExpressionHelper.removeListener(helper, listener);
        }
        
        @Override
        public Bounds get() {
            if (!valid) {
                bounds = computeBounds();
                valid = true;
            }

            return bounds;
        }

        public void invalidate() {
            if (valid) {
                valid = false;
                ExpressionHelper.fireValueChangedEvent(helper);
            }
        }

        protected abstract Bounds computeBounds();
    }

    private static final BoundsAccessor boundsAccessor = new BoundsAccessor() {
        @Override
        public BaseBounds getGeomBounds(BaseBounds bounds, BaseTransform tx, Node node) {
            return node.getGeomBounds(bounds, tx);
        }
    };

    /**
     * This method is used by Scene-graph JMX bean to obtain the Scene-graph structure.
     *
     * @param alg current algorithm to process this node
     * @param ctx current context
     * @return the algorithm specific result for this node
     * @treatasprivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    public abstract Object impl_processMXNode(MXNodeAlgorithm alg, MXNodeAlgorithmContext ctx);
}
