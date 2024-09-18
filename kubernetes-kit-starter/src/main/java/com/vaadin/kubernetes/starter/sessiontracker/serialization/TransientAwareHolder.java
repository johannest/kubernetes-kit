/*-
 * Copyright (C) 2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.server.VaadinSession;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A serializable class that holds information about an object to be
 * serialized/deserialized and its transient fields that can be injected after
 * deserialization.
 *
 * For internal use only.
 */
final class TransientAwareHolder implements Serializable {

    static final TransientAwareHolder NULL = new TransientAwareHolder(null,
            Collections.emptyList());

    private final List<TransientDescriptor> transientDescriptors;
    private final Object source; // NOSONAR
    private final VaadinSession session;
    private final UI ui;

    TransientAwareHolder(Object source, List<TransientDescriptor> descriptors) {
        this.source = source;
        this.transientDescriptors = new ArrayList<>(descriptors);
        this.session = VaadinSession.getCurrent();
        this.ui = UI.getCurrent();
    }

    /**
     * Gets the list of descriptor of transient fields capable to be injected
     * after deserialization.
     *
     * @return list of injectable transient fields descriptors.
     */
    List<TransientDescriptor> transients() {
        return new ArrayList<>(transientDescriptors);
    }

    /**
     * Gets the object to be serialized and deserialized.
     *
     * @return object to be serialized and deserialized.
     */
    Object source() {
        return source;
    }

    void runWithVaadin(Runnable runnable) {
        Map<Class<?>, CurrentInstance> old = null;
        if (ui != null) {
            old = CurrentInstance.setCurrent(ui);
        } else if (session != null) {
            old = CurrentInstance.setCurrent(session);
        }
        try {
            executeWithSessionLock(session, runnable);
        } finally {
            if (old != null) {
                CurrentInstance.restoreInstances(old);
            }
        }
    }

    private static void executeWithSessionLock(VaadinSession session,
                                               Runnable runnable) {
        if (session != null) {
            Lock lock = session.getLockInstance();
            Field lockField = null;
            if (lock == null) {
                lock = new ReentrantLock();
                try {
                    lockField = VaadinSession.class.getDeclaredField("lock");
                    ReflectTools.setJavaFieldValue(session, lockField, lock);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            lock.lock();
            try {
                runnable.run();
            } finally {
                lock.unlock();
                if (lockField != null) {
                    ReflectTools.setJavaFieldValue(session, lockField, null);
                }
            }
        } else {
            runnable.run();
        }
    }

}