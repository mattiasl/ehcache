/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.ehcache.tests.servermap;

import org.terracotta.ehcache.tests.AbstractExpressCacheTest;

import com.tc.simulator.app.ApplicationConfig;
import com.tc.simulator.listener.ListenerProvider;

import java.util.Iterator;
import java.util.List;

public class ServerMapL1CapacityEvictionExpressTest extends AbstractExpressCacheTest {

  public ServerMapL1CapacityEvictionExpressTest() {
    super("/servermap/servermap-l1-capacity-test.xml", ServerMapL1CapacityEvictionExpressTestClient.class);
  }

  @Override
  protected Class<?> getApplicationClass() {
    return App.class;
  }

  public static class App extends AbstractExpressCacheTest.App {

    public App(final String appId, final ApplicationConfig cfg, final ListenerProvider listenerProvider) {
      super(appId, cfg, listenerProvider);
      addClientJvmarg("-Dcom.tc.l1.lockmanager.timeout.interval=600000");
      addClientJvmarg("-Dcom.tc.ehcache.evictor.logging.enabled=true");
    }

    @Override
    protected void addTestTcPropertiesFile(final List<String> jvmArgs) {
      // do not add tc properties file for this test
    }

    @Override
    protected void configureClientExtraJVMArgs(final List<String> jvmArgs) {
      super.configureClientExtraJVMArgs(jvmArgs);
      final Iterator<String> iter = jvmArgs.iterator();
      while (iter.hasNext()) {
        final String prop = iter.next();
        if (prop.contains("ehcache.storageStrategy.dcv2.localcache.enabled")) {
          // remove it and always enable localcache for this test
          iter.remove();
        }
      }
      // always enable local cache
      jvmArgs.add("-Dcom.tc.ehcache.storageStrategy.dcv2.localcache.enabled=true");
    }

  }

}
