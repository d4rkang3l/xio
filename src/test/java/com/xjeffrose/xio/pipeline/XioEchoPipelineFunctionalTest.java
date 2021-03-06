package com.xjeffrose.xio.pipeline;

import com.xjeffrose.xio.helpers.EchoClient;
import com.xjeffrose.xio.bootstrap.ChannelConfiguration;
import com.xjeffrose.xio.bootstrap.XioServerBootstrap;
import com.xjeffrose.xio.server.XioServer;
import com.xjeffrose.xio.server.XioServerConfig;
import com.xjeffrose.xio.server.XioServerState;
import org.junit.Assert;
import org.junit.Test;

import java.lang.StringBuilder;
public class XioEchoPipelineFunctionalTest extends Assert {

  @Test
  public void testEchoServer() {
    XioServerBootstrap bootstrap = XioServerBootstrap.fromConfig("xio.testApplication")
      .addToPipeline(new XioEchoPipeline())
    ;

    try (XioServer server = bootstrap.build(); EchoClient client = new EchoClient()) {
      client.connect(server.getInstrumentation().addressBound());
      String payload = "test message";
      client.send(payload);
      String response = client.recv();
      assertEquals(payload, response);
    }
  }

  @Test
  public void testEchoServerLargePayload() {
    XioServerBootstrap bootstrap = XioServerBootstrap.fromConfig("xio.testApplication")
      .addToPipeline(new XioEchoPipeline())
    ;

    try (XioServer server = bootstrap.build(); EchoClient client = new EchoClient()) {
      client.connect(server.getInstrumentation().addressBound());
      int n = 800;
      String payload = "Netty rocks!";
      StringBuilder builder = new StringBuilder(n * payload.length());
      for (int i = 0; i < n; i += 1) {
        builder.append(payload);
      }
      payload = builder.toString();
      client.send(payload);
      String response = client.recv();
      assertEquals(payload, response);
    }
  }

}
