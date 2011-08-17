/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.apollo.broker.store.leveldb

import dto.LevelDBStoreDTO
import org.apache.activemq.apollo.broker.store.{Store, StoreFunSuiteSupport}
import org.apache.activemq.apollo.util.FileSupport._

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class IQ80LevelDBStoreTest extends StoreFunSuiteSupport {

  def create_store(flushDelay:Long):Store = {
    new LevelDBStore({
      val rc = new LevelDBStoreDTO
      rc.driver = "iq80"
      rc.directory = basedir / "target" / "apollo-data"
      rc.flush_delay = flushDelay
      rc
    })
  }

}
