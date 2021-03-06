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
package org.fusesource.fabric.bridge.zk.internal;

import org.apache.zookeeper.KeeperException;
import org.fusesource.fabric.api.FabricService;
import org.fusesource.fabric.service.FabricServiceImpl;
import org.fusesource.fabric.zookeeper.IZKClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.SmartLifecycle;

/**
 * @author Dhiraj Bokde
 */
public class ZkServerSetupBean implements SmartLifecycle {

    private static final String FABRIC_ROOT_PATH = "/fabric";
    private FabricService fabricService;
    private volatile boolean running;
    private static final Logger LOG = LoggerFactory.getLogger(ZkServerSetupBean.class);

    public FabricService getFabricService() {
        return fabricService;
    }

    public void setFabricService(FabricService fabricService) {
        this.fabricService = fabricService;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * Setup vanilla ZK server.
     */
    @Override
    public void start() {
        IZKClient client = ((FabricServiceImpl)fabricService).getZooKeeper();

        // import ZK contents
        TestImport testImport = new TestImport();
        testImport.setSource("target/test-classes/zkexport");
        testImport.setNRegEx(new String[] {"dummy"});
        testImport.setZooKeeper(client);
        try {
            testImport.doExecute(client);
        } catch (Exception e) {
            String msg = "Error setting up ZK config: " + e.getMessage();
            LOG.error(msg, e);
            throw new BeanCreationException(msg, e);
        }
        running = true;
    }

    @Override
    public void stop() {
        // clean up old ZK configuration
        try {
            IZKClient client = ((FabricServiceImpl)fabricService).getZooKeeper();
            client.deleteWithChildren(FABRIC_ROOT_PATH);
        } catch (InterruptedException e) {
            String msg = "Error cleaning up old ZK config: " + e.getMessage();
            LOG.error(msg, e);
            throw new BeanCreationException(msg, e);
        } catch (KeeperException e) {
            String msg = "Error cleaning up old ZK config: " + e.getMessage();
            LOG.error(msg, e);
            throw new BeanCreationException(msg, e);
        }

        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 1;
    }

}
