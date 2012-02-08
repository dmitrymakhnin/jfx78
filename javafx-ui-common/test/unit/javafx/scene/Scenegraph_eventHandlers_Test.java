/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Rectangle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.sun.javafx.test.PropertyReference;

@RunWith(Parameterized.class)
public final class Scenegraph_eventHandlers_Test {
    private final EventType<?> eventType;
    private final PropertyReference sceneOnHandlerPropRef;
    private final PropertyReference nodeOnHandlerPropRef;
    private final Event triggeringEvent;

    @Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][] {
            {
                KeyEvent.KEY_PRESSED,
                "onKeyPressed",
                createTestKeyEvent(KeyEvent.KEY_PRESSED)
            }, {
                KeyEvent.KEY_RELEASED,
                "onKeyReleased",
                createTestKeyEvent(KeyEvent.KEY_RELEASED)
            }, {
                KeyEvent.KEY_TYPED,
                "onKeyTyped",
                createTestKeyEvent(KeyEvent.KEY_TYPED)
            }, {
                MouseEvent.MOUSE_PRESSED,
                "onMousePressed",
                createTestMouseEvent(MouseEvent.MOUSE_PRESSED)
            }, {
                MouseEvent.MOUSE_RELEASED,
                "onMouseReleased",
                createTestMouseEvent(MouseEvent.MOUSE_RELEASED)
            }, {
                MouseEvent.MOUSE_CLICKED,
                "onMouseClicked",
                createTestMouseEvent(MouseEvent.MOUSE_CLICKED)
            }, {
                MouseEvent.MOUSE_ENTERED,
                "onMouseEntered",
                createTestMouseEvent(MouseEvent.MOUSE_ENTERED)
            }, {
                MouseEvent.MOUSE_EXITED,
                "onMouseExited",
                createTestMouseEvent(MouseEvent.MOUSE_EXITED)
            }, {
                MouseEvent.MOUSE_MOVED,
                "onMouseMoved",
                createTestMouseEvent(MouseEvent.MOUSE_MOVED)
            }, {
                MouseEvent.MOUSE_DRAGGED,
                "onMouseDragged",
                createTestMouseEvent(MouseEvent.MOUSE_DRAGGED)
            }
        });
    }

    public Scenegraph_eventHandlers_Test(
            final EventType<?> eventType,
            final String onHandlerName,
            final Event triggeringEvent) {
        this.eventType = eventType;
        this.sceneOnHandlerPropRef =
                PropertyReference.createForBean(Scene.class, onHandlerName);
        this.nodeOnHandlerPropRef =
                PropertyReference.createForBean(Node.class, onHandlerName);
        this.triggeringEvent = triggeringEvent;
    }

    @Test
    public void shouldCallRegisteredHandlers() {
        final EventCountingHandler sceneHandler = new EventCountingHandler();
        final EventCountingHandler lNodeHandler = new EventCountingHandler();
        final EventCountingHandler rNodeHandler = new EventCountingHandler();
        final EventCountingHandler rlNodeHandler = new EventCountingHandler();

        setEventHandler(TEST_SCENE, sceneOnHandlerPropRef, sceneHandler);
        setEventHandler(TEST_L_NODE, nodeOnHandlerPropRef, lNodeHandler);
        TEST_R_NODE.addEventHandler(eventType, rNodeHandler);
        setEventHandler(TEST_RL_NODE, nodeOnHandlerPropRef, rlNodeHandler);

        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(0, lNodeHandler.getCounter());
        assertEquals(1, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());

        Event.fireEvent(TEST_RR_NODE, triggeringEvent);
        assertEquals(2, sceneHandler.getCounter());
        assertEquals(0, lNodeHandler.getCounter());
        assertEquals(2, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());

        Event.fireEvent(TEST_R_NODE, triggeringEvent);
        assertEquals(3, sceneHandler.getCounter());
        assertEquals(0, lNodeHandler.getCounter());
        assertEquals(3, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());
        
        Event.fireEvent(TEST_L_NODE, triggeringEvent);
        assertEquals(4, sceneHandler.getCounter());
        assertEquals(1, lNodeHandler.getCounter());
        assertEquals(3, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());

        Event.fireEvent(TEST_SCENE, triggeringEvent);
        assertEquals(5, sceneHandler.getCounter());
        assertEquals(1, lNodeHandler.getCounter());
        assertEquals(3, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());

        Event.fireEvent(TEST_RL_NODE, TEST_EVENT);
        assertEquals(5, sceneHandler.getCounter());
        assertEquals(1, lNodeHandler.getCounter());
        assertEquals(3, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());

        setEventHandler(TEST_SCENE, sceneOnHandlerPropRef, null);
        setEventHandler(TEST_L_NODE, nodeOnHandlerPropRef, null);
        TEST_R_NODE.removeEventHandler(eventType, rNodeHandler);
        setEventHandler(TEST_RL_NODE, nodeOnHandlerPropRef, null);

        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(5, sceneHandler.getCounter());
        assertEquals(1, lNodeHandler.getCounter());
        assertEquals(3, rNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());
    }

    @Test
    public void shouldNotPropagateConsumedCapturingEvents() {
        final EventCountingHandler sceneHandler = new EventCountingHandler();
        final EventCountingHandler rlNodeHandler = new EventCountingHandler();

        setEventHandler(TEST_SCENE, sceneOnHandlerPropRef, sceneHandler);
        setEventHandler(TEST_RL_NODE, nodeOnHandlerPropRef, rlNodeHandler);

        TEST_L_NODE.addEventFilter(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());
        TEST_L_NODE.removeEventFilter(eventType, EVENT_CONSUMING_HANDLER);

        TEST_R_NODE.addEventFilter(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());
        TEST_R_NODE.removeEventFilter(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(2, sceneHandler.getCounter());
        assertEquals(2, rlNodeHandler.getCounter());

        TEST_SCENE.addEventFilter(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(2, sceneHandler.getCounter());
        assertEquals(2, rlNodeHandler.getCounter());
        TEST_SCENE.removeEventFilter(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(3, sceneHandler.getCounter());
        assertEquals(3, rlNodeHandler.getCounter());

        setEventHandler(TEST_SCENE, sceneOnHandlerPropRef, null);
        setEventHandler(TEST_RL_NODE, nodeOnHandlerPropRef, null);
    }

    @Test
    public void shouldNotPropagateConsumedBubblingEvents() {
        final EventCountingHandler sceneHandler = new EventCountingHandler();
        final EventCountingHandler rootNodeHandler = new EventCountingHandler();
        final EventCountingHandler rlNodeHandler = new EventCountingHandler();

        setEventHandler(TEST_SCENE, sceneOnHandlerPropRef, sceneHandler);
        setEventHandler(TEST_ROOT_NODE, nodeOnHandlerPropRef, rootNodeHandler);
        setEventHandler(TEST_RL_NODE, nodeOnHandlerPropRef, rlNodeHandler);

        setEventHandler(TEST_L_NODE, nodeOnHandlerPropRef,
                        EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(1, rootNodeHandler.getCounter());
        assertEquals(1, rlNodeHandler.getCounter());
        setEventHandler(TEST_L_NODE, nodeOnHandlerPropRef, null);

        TEST_RL_NODE.addEventHandler(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(1, rootNodeHandler.getCounter());
        assertEquals(2, rlNodeHandler.getCounter());
        TEST_RL_NODE.removeEventHandler(eventType, EVENT_CONSUMING_HANDLER);

        setEventHandler(TEST_R_NODE, nodeOnHandlerPropRef,
                        EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(1, rootNodeHandler.getCounter());
        assertEquals(3, rlNodeHandler.getCounter());
        setEventHandler(TEST_R_NODE, nodeOnHandlerPropRef, null);

        TEST_ROOT_NODE.addEventHandler(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(1, sceneHandler.getCounter());
        assertEquals(2, rootNodeHandler.getCounter());
        assertEquals(4, rlNodeHandler.getCounter());
        TEST_ROOT_NODE.removeEventHandler(eventType, EVENT_CONSUMING_HANDLER);

        TEST_SCENE.addEventHandler(eventType, EVENT_CONSUMING_HANDLER);
        Event.fireEvent(TEST_RL_NODE, triggeringEvent);
        assertEquals(2, sceneHandler.getCounter());
        assertEquals(3, rootNodeHandler.getCounter());
        assertEquals(5, rlNodeHandler.getCounter());
        TEST_SCENE.removeEventHandler(eventType, EVENT_CONSUMING_HANDLER);

        setEventHandler(TEST_SCENE, sceneOnHandlerPropRef, null);
        setEventHandler(TEST_ROOT_NODE, nodeOnHandlerPropRef, null);
        setEventHandler(TEST_RL_NODE, nodeOnHandlerPropRef, null);
    }

    private static final Node TEST_RL_NODE = new Rectangle();
    private static final Node TEST_RR_NODE = new Rectangle();
    private static final Node TEST_L_NODE = new Rectangle();
    private static final Group TEST_R_NODE =
            new Group(TEST_RL_NODE, TEST_RR_NODE);
    private static final Group TEST_ROOT_NODE =
            new Group(TEST_L_NODE, TEST_R_NODE);
    private static final Scene TEST_SCENE = new Scene(TEST_ROOT_NODE);
    private static final Event TEST_EVENT = new Event(new EventType<Event>());

    private static final EventHandler<Event> EVENT_CONSUMING_HANDLER =
            new EventHandler<Event>() {
                @Override
                public void handle(final Event event) {
                    event.consume();
                }
            };

    private static final class EventCountingHandler 
            implements EventHandler<Event> {
        private int counter;

        @Override
        public void handle(final Event event) {
            ++counter;
        }

        public int getCounter() {
            return counter;
        }
    }

    private static Event createTestKeyEvent(
            final EventType<KeyEvent> keyEventType) {
        String character;
        String text;
        KeyCode keyCode;
        if (keyEventType == KeyEvent.KEY_TYPED) {
            character = "q";
            text = "";
            keyCode = KeyCode.UNDEFINED;
        } else {
            character = KeyEvent.CHAR_UNDEFINED;
            text = KeyCode.Q.getName();
            keyCode = KeyCode.Q;
        }

        return KeyEvent.impl_keyEvent(null, character, text,
                                      keyCode.impl_getCode(),
                                      false, false, false, false,
                                      keyEventType);
    }

    private static Event createTestMouseEvent(
            final EventType<MouseEvent> mouseEventType) {
        MouseButton button = MouseButton.NONE;
        boolean primaryButtonDown = false;
        int clickCount = 0;

        if ((mouseEventType == MouseEvent.MOUSE_RELEASED)
                || (mouseEventType == MouseEvent.MOUSE_CLICKED)) {
            button = MouseButton.PRIMARY;
            clickCount = 1;
        } else if (mouseEventType == MouseEvent.MOUSE_PRESSED) {
            button = MouseButton.PRIMARY;
            primaryButtonDown = true;
            clickCount = 1;
        }

        return MouseEvent.impl_mouseEvent(0, 0, 0, 0,
                                          button, clickCount,
                                          false, false, false, false,
                                          false, primaryButtonDown,
                                          false, false, mouseEventType);
    }

    private static void setEventHandler(
            final Object bean,
            final PropertyReference handlerPropertyReference,
            final EventHandler<? extends Event> handler) {
        handlerPropertyReference.setValue(bean, handler);
    }
}