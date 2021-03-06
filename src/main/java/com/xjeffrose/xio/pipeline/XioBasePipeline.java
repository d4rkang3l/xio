package com.xjeffrose.xio.pipeline;

import com.xjeffrose.xio.application.ApplicationState;
import com.xjeffrose.xio.core.ChannelStatistics;
import com.xjeffrose.xio.core.ConnectionContextHandler;
import com.xjeffrose.xio.core.XioExceptionLogger;
import com.xjeffrose.xio.core.XioMessageLogger;
import com.xjeffrose.xio.filter.Http1Filter;
import com.xjeffrose.xio.filter.IpFilter;
import com.xjeffrose.xio.server.XioBehavioralRuleEngine;
import com.xjeffrose.xio.server.XioConnectionLimiter;
import com.xjeffrose.xio.server.XioResponseClassifier;
import com.xjeffrose.xio.server.XioServerConfig;
import com.xjeffrose.xio.server.XioServerLimits;
import com.xjeffrose.xio.server.XioServerState;
import com.xjeffrose.xio.server.XioService;
import com.xjeffrose.xio.server.XioWebApplicationFirewall;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

abstract public class XioBasePipeline implements XioPipelineFragment {

  protected static final XioConnectionLimiter globalConnectionLimiter = new XioConnectionLimiter(15000);

  abstract public ChannelHandler getEncryptionHandler(XioServerConfig config, XioServerState state);

  abstract public ChannelHandler getAuthenticationHandler();

  abstract public ChannelHandler getCodecHandler();

  abstract public ChannelHandler getIdleDisconnectHandler(XioServerLimits limits);

  abstract public String applicationProtocol();

  abstract public ChannelHandler getApplicationHandler();

  public void buildHandlers(ApplicationState appState, XioServerConfig config, XioServerState state, ChannelPipeline pipeline) {
    // TODO(CK): pull globalConnectionLimiter from state
    pipeline.addLast("globalConnectionLimiter", globalConnectionLimiter); // TODO(JR): Need to make this config
    pipeline.addLast("serviceConnectionLimiter", new XioConnectionLimiter(config.getLimits().maxConnections()));
    ChannelHandler idleDisconnectHandler = getIdleDisconnectHandler(config.getLimits());
    pipeline.addLast("idleDisconnectHandler", idleDisconnectHandler);
    pipeline.addLast("l4DeterministicRuleEngine", new IpFilter(appState.getIpFilterConfig()));
    pipeline.addLast("l4BehavioralRuleEngine", new XioBehavioralRuleEngine(appState.getZkClient(), true)); // TODO(JR): Need to make this config
    pipeline.addLast("connectionContext", new ConnectionContextHandler());
    pipeline.addLast("globalChannelStatistics", state.getChannelStatistics());
    ChannelHandler encryptionHandler = getEncryptionHandler(config, state);
    if (encryptionHandler != null) {
      pipeline.addLast("encryptionHandler", encryptionHandler);
    }
    if (config.isMessageLoggerEnabled()) {
      pipeline.addLast("messageLogger", new XioMessageLogger());
    }
    ChannelHandler codecHandler = getCodecHandler();
    if (codecHandler != null) {
      pipeline.addLast("codec", codecHandler);
    } else {
      throw new RuntimeException("No codec configured");
    }
    pipeline.addLast("l7DeterministicRuleEngine", new Http1Filter(appState.getHttp1FilterConfig()));
    pipeline.addLast("l7BehavioralRuleEngine", new XioBehavioralRuleEngine(appState.getZkClient(), true)); // TODO(JR): Need to make this config
    pipeline.addLast("webApplicationFirewall", new XioWebApplicationFirewall(appState.getZkClient(), true)); // TODO(JR): Need to make this config
    ChannelHandler authHandler = getAuthenticationHandler();
    if (authHandler != null) {
      pipeline.addLast("authHandler", authHandler);
    }
    pipeline.addLast("xioService", new XioService());
    // See https://finagle.github.io/blog/2016/02/09/response-classification
    pipeline.addLast("xioResponseClassifier", new XioResponseClassifier(true)); /// TODO(JR): This is a maybe
    pipeline.addLast("exceptionLogger", new XioExceptionLogger());
    ChannelHandler applicationHandler = getApplicationHandler();
    if (applicationHandler != null) {
      pipeline.addLast("applicationHandler", applicationHandler);
    }
  }
}
