/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.dosgi.tcp;

import org.fusesource.fabric.dosgi.io.Service;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.transport.ProtocolCodec;
import org.fusesource.hawtdispatch.transport.Transport;
import org.fusesource.hawtdispatch.transport.TransportListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TransportPool implements Service {

    protected static final Logger LOGGER = LoggerFactory.getLogger(TransportPool.class);

    public static final int DEFAULT_POOL_SIZE = 2;

    public static final long DEFAULT_EVICTION_DELAY = TimeUnit.MINUTES.toMillis(5);

    protected final String uri;
    protected final DispatchQueue queue;
    protected final LinkedList<Object> pending = new LinkedList<Object>();
    protected final Map<Transport, Long> transports = new HashMap<Transport, Long>();
    protected AtomicBoolean running = new AtomicBoolean(false);

    protected int poolSize;
    protected long evictionDelay;

    public TransportPool(String uri, DispatchQueue queue) {
        this(uri, queue, DEFAULT_POOL_SIZE, DEFAULT_EVICTION_DELAY);
    }

    public TransportPool(String uri, DispatchQueue queue, int poolSize, long evictionDelay) {
        this.uri = uri;
        this.queue = queue;
        this.poolSize = poolSize;
        this.evictionDelay = evictionDelay;
    }

    protected abstract Transport createTransport(String uri) throws Exception;

    protected abstract ProtocolCodec createCodec();

    protected abstract void onCommand(Object command);

    public void offer(final Object data) {
        if (!running.get()) {
            throw new IllegalStateException("Transport pool stopped");
        }
        queue.execute(new Runnable() {
            public void run() {
                Transport transport = getIdleTransport();
                if (transport != null) {
                    transport.offer(data);
                    if( transport.full() ) {
                        transports.put(transport, 0L);
                    }
                } else {
                    pending.add(data);
                }
            }
        });
    }

    protected Transport getIdleTransport() {
        for (Map.Entry<Transport, Long> entry : transports.entrySet()) {
            if (entry.getValue() > 0) {
                return entry.getKey();
            }
        }
        if (transports.size() < poolSize) {
            try {
                startNewTransport();
            } catch (Exception e) {
                LOGGER.info("Unable to start new transport", e);
            }
        }
        return null;
    }

    public void start() throws Exception {
        start(null);
    }

    public void start(Runnable onComplete) throws Exception {
        running.set(true);
    }

    public void stop() {
        stop(null);
    }

    public void stop(final Runnable onComplete) {
        if (running.compareAndSet(true, false)) {
            queue.execute(new Runnable() {
                public void run() {
                    final AtomicInteger latch = new AtomicInteger(transports.size());
                    final Runnable coutDown = new Runnable() {
                        public void run() {
                            if (latch.decrementAndGet() == 0) {
                                pending.clear();
                                onComplete.run();
                            }
                        }
                    };
                    for (Transport transport : transports.keySet()) {
                        transport.stop(coutDown);
                    }
                }
            });
        } else {
            onComplete.run();
        }
    }

    protected void startNewTransport() throws Exception {
System.err.println("Creating new transport for: " + this.uri);
        Transport transport = createTransport(this.uri);
        transport.setDispatchQueue(queue);
        transport.setProtocolCodec(createCodec());
        transport.setTransportListener(new Listener(transport));
        transports.put(transport, 0L);
        transport.start(null);
    }

    protected class Listener implements TransportListener {

        private final Transport transport;

        Listener(Transport transport) {
            this.transport = transport;
        }

        public void onTransportCommand(Object command) {
            TransportPool.this.onCommand(command);
        }

        public void onRefill() {
            while (pending.size() > 0 &&  !transport.full()) {
                boolean accepted = transport.offer(pending.removeFirst());
                assert accepted: "Should have been accepted since the transport was not full";
            }

            if( transport.full() ) {
                transports.put(transport, 0L);
            } else {
                final long time = System.currentTimeMillis();
                transports.put(transport, time);
                if (evictionDelay > 0) {
                    queue.executeAfter(evictionDelay, TimeUnit.MILLISECONDS, new Runnable() {
                        public void run() {
                            if (transports.get(transport) == time) {
                                transports.remove(transport);
                                transport.stop(null);
                            }
                        }
                    });
                }
            }

        }

        public void onTransportFailure(IOException error) {
            if (!transport.isDisposed()) {
                LOGGER.info("Transport failure", error);
                transports.remove(transport);
                transport.stop(null);
            }
        }

        public void onTransportConnected() {
            transport.resumeRead();
            onRefill();
        }

        public void onTransportDisconnected(boolean reconnecting) {
            transports.remove(transport);
        }
    }
}
