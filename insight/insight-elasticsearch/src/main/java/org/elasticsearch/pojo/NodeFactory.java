/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.elasticsearch.pojo;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;

import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalNode;
import org.osgi.framework.BundleContext;

public class NodeFactory extends BaseManagedServiceFactory<Node> {

    private Map<String,String> settings;

    public NodeFactory(BundleContext context, Map<String, String> settings) {
        super(context, "ElasticSearch Node factory");
        this.settings = settings;
    }

    @Override
    protected Node doCreate(Dictionary properties) {
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder();
        builder.put(settings);
        builder.classLoader(NodeFactory.class.getClassLoader());
        if (properties != null) {
            for (Enumeration e = properties.keys(); e.hasMoreElements();) {
                String key = e.nextElement().toString();
                Object oval = properties.get(key);
                String val = oval != null ? oval.toString() : null;
                builder.put(key, val);
            }
        }
        Node node = new InternalNode(builder.build(), false);
        try {
            node.start();
            node.client().admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        } catch (RuntimeException t) {
            doDestroy(node);
            throw t;
        }
        return node;
    }

    @Override
    protected void doDestroy(Node node) {
        node.close();
    }

    @Override
    protected String[] getExposedClasses() {
        return new String[] { Node.class.getName() };
    }

}
