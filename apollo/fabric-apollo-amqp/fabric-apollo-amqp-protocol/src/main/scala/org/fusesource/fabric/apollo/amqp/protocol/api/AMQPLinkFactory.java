/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.api;

import org.fusesource.fabric.apollo.amqp.protocol.AMQPReceiver;
import org.fusesource.fabric.apollo.amqp.protocol.AMQPSender;

/**
 *
 */
public class AMQPLinkFactory {

    public static Sender createSender(String name) {
        return AMQPSender.create(name);
    }

    public static Receiver createReceiver(String name) {
        return AMQPReceiver.create(name);
    }
}