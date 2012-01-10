/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.coherence;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.filters.StringInputStream;
import org.terracotta.modules.BasicTimInfo;
import org.terracotta.modules.TimInfo;

import com.tc.config.schema.builder.InstrumentedClassConfigBuilder;
import com.tc.config.schema.builder.RootConfigBuilder;
import com.tc.config.schema.test.InstrumentedClassConfigBuilderImpl;
import com.tc.config.schema.test.RootConfigBuilderImpl;
import com.tc.config.schema.test.TerracottaConfigBuilder;
import com.tc.object.config.ConfigVisitor;
import com.tc.object.config.DSOClientConfigHelper;
import com.tc.objectserver.control.ExtraL1ProcessControl;
import com.tc.simulator.app.ApplicationConfig;
import com.tc.simulator.listener.ListenerProvider;
import com.tc.util.Assert;
import com.tc.util.PortChooser;
import com.tc.util.runtime.Os;
import com.tctest.TransparentTestBase;
import com.tctest.TransparentTestIface;
import com.tctest.runner.AbstractErrorCatchingTransparentApp;
import com.tctest.runner.TransparentAppConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RestartingL1ClientTest extends TransparentTestBase {

  private static final int NODE_COUNT   = 1;
  public static final int  CLIENT_COUNT = 3;

  private int              port;
  private File             configFile;
  private int              adminPort;

  public RestartingL1ClientTest() {
    if (Os.isWindows()) {
      disableTest();
    }
  }

  @Override
  protected Class getApplicationClass() {
    return App.class;
  }

  @Override
  public void setUp() throws Exception {
    PortChooser pc = new PortChooser();
    port = pc.chooseRandomPort();
    adminPort = pc.chooseRandomPort();
    int groupPort = pc.chooseRandomPort();
    configFile = getTempFile("config-file.xml");
    writeConfigFile();

    setUpControlledServer(configFactory(), configHelper(), port, adminPort, groupPort, configFile.getAbsolutePath());
    doSetUp(this);
  }

  @Override
  public void doSetUp(final TransparentTestIface t) throws Exception {
    t.getTransparentAppConfig().setClientCount(RestartingL1ClientTest.NODE_COUNT);
    t.initializeTestRunner();
    TransparentAppConfig cfg = t.getTransparentAppConfig();
    cfg.setAttribute(App.CONFIG_FILE, configFile.getAbsolutePath());
    cfg.setAttribute(App.PORT_NUMBER, String.valueOf(port));
    cfg.setAttribute(App.HOST_NAME, "localhost");
  }

  private synchronized void writeConfigFile() {
    try {
      TerracottaConfigBuilder builder = createConfig(port, adminPort);
      FileOutputStream out = new FileOutputStream(configFile);
      IOUtils.copy(new StringInputStream(builder.toString()), out);
      out.close();
    } catch (Exception e) {
      throw Assert.failure("Can't create config file", e);
    }
  }

  public static TerracottaConfigBuilder createConfig(final int port, final int adminPort) {
    TerracottaConfigBuilder out = new TerracottaConfigBuilder();

    out.getServers().getL2s()[0].setDSOPort(port);
    out.getServers().getL2s()[0].setJMXPort(adminPort);

    String testClient = RestartingL1Client.class.getName();
    InstrumentedClassConfigBuilder instrumented = new InstrumentedClassConfigBuilderImpl();
    instrumented.setClassExpression(testClient);

    out.getApplication().getDSO().setInstrumentedClasses(new InstrumentedClassConfigBuilder[] { instrumented });

    RootConfigBuilder root = new RootConfigBuilderImpl();
    root.setFieldName(RestartingL1Client.class.getName() + ".barrier");
    root.setRootName("barrier");

    out.getApplication().getDSO().setRoots(new RootConfigBuilder[] { root });

    TimInfo timInfo = new BasicTimInfo("org.terracotta.modules", "tim-ehcache-2.x");
    out.getClient().addModule(timInfo.artifactId(), timInfo.groupId(), timInfo.version());

    return out;
  }

  public static class App extends AbstractErrorCatchingTransparentApp {

    public static final String      CONFIG_FILE = "config-file";
    public static final String      PORT_NUMBER = "port-number";
    public static final String      HOST_NAME   = "host-name";

    private final ApplicationConfig config;

    public App(String appId, ApplicationConfig cfg, ListenerProvider listenerProvider) {
      super(appId, cfg, listenerProvider);
      this.config = cfg;
    }

    @Override
    protected void runTest() throws Throwable {
      doRestartingL1Test();
      System.out.println("All clients finished test successfully");
    }

    private void doRestartingL1Test() throws Exception {
      System.out.println("Running cache coherence test - restarting client");
      final List<Integer> clientsWithErrors = new ArrayList<Integer>();
      Thread[] clientThreads = new Thread[CLIENT_COUNT];
      for (int i = 0; i < CLIENT_COUNT; i++) {
        final int id = i;
        clientThreads[i] = new Thread(new Runnable() {

          public void run() {
            try {
              List<String> args = new ArrayList<String>();
              if (id == 0) {
                args.add("crasher");
              }
              ExtraL1ProcessControl client = spawnNewClient("restarting-test", id, RestartingL1Client.class, args,
                                                            false);
              if (client.waitFor() != 0) {
                clientsWithErrors.add(id);
              }
            } catch (Exception e) {
              e.printStackTrace();
              clientsWithErrors.add(id);
            }
          }
        }, "Spawned-client-thread-" + i);
        clientThreads[i].start();
      }
      System.out.println("MAIN TEST THREAD WAITING FOR Crashing client TO FINISH");
      clientThreads[0].join();
      System.out.println("Crashing client finished. Restarting client...");

      clientThreads[0] = new Thread(new Runnable() {

        public void run() {
          try {
            List<String> args = Arrays.asList("crasher", "afterRestart");
            ExtraL1ProcessControl client = spawnNewClient("after-restart", 0, RestartingL1Client.class, args, false);
            if (client.waitFor() != 0) {
              clientsWithErrors.add(0);
            }
          } catch (Exception e) {
            e.printStackTrace();
            clientsWithErrors.add(0);
          }
        }
      }, "Spawned-client-thread-after-restart");
      clientThreads[0].start();

      System.out.println("MAIN TEST THREAD WAITING FOR ALL CLIENTS TO FINISH");
      // now restart the first client again
      for (Thread clientThread : clientThreads) {
        clientThread.join();
      }
      if (clientsWithErrors.size() > 0) { throw new Exception("Clients did not exit successfully: " + clientsWithErrors); }

    }

    public static void visitL1DSOConfig(ConfigVisitor visitor, DSOClientConfigHelper config) {
      config.getOrCreateSpec(RestartingL1Client.class.getName()).addRoot("barrier", "barrier");

      String module_name = "tim-ehcache-2.x";
      TimInfo timInfo = new BasicTimInfo("org.terracotta.modules", module_name);
      config.addModule(timInfo.artifactId(), timInfo.version());
    }

    private ExtraL1ProcessControl spawnNewClient(final String name, final int id, Class clientClass, List<String> args,
                                                 boolean debug) throws Exception {
      final String hostName = config.getAttribute(HOST_NAME);
      final int port = Integer.parseInt(config.getAttribute(PORT_NUMBER));
      final File configFile = new File(config.getAttribute(CONFIG_FILE));
      File workingDir = new File(configFile.getParentFile(), "client-" + (name == null ? "" : name + "-") + id);
      FileUtils.forceMkdir(workingDir);

      List<String> jvmArgs = new ArrayList<String>();
      if (debug) {
        jvmArgs.add("-Xdebug");
        int debugPort = 9000 + id;
        jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=" + debugPort + ",server=y,suspend=y");
      }
      addTestTcPropertiesFile(jvmArgs);
      ExtraL1ProcessControl client = new ExtraL1ProcessControl(hostName, port, clientClass,
                                                               configFile.getAbsolutePath(), args, workingDir, jvmArgs);
      client.start();
      client.mergeSTDERR();
      client.mergeSTDOUT();
      System.err.println("\n### Started New Client");
      return client;
    }

    private void addTestTcPropertiesFile(final List jvmArgs) {
      URL url = getClass().getResource("/com/tc/properties/tests.properties");
      if (url == null) { return; }
      String pathToTestTcProperties = url.getPath();
      if (pathToTestTcProperties == null || pathToTestTcProperties.equals("")) { return; }
      System.err.println("\n##### -Dcom.tc.properties=" + pathToTestTcProperties);
      jvmArgs.add("-Dcom.tc.properties=" + pathToTestTcProperties);
    }
  }

}
