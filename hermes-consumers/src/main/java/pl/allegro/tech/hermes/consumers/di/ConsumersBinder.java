package pl.allegro.tech.hermes.consumers.di;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import pl.allegro.tech.hermes.common.admin.zookeeper.ZookeeperAdminCache;
import pl.allegro.tech.hermes.common.di.factories.UndeliveredMessageLogFactory;
import pl.allegro.tech.hermes.common.message.undelivered.UndeliveredMessageLog;
import pl.allegro.tech.hermes.common.metric.executor.InstrumentedExecutorServiceFactory;
import pl.allegro.tech.hermes.consumers.consumer.ConsumerAuthorizationHandler;
import pl.allegro.tech.hermes.consumers.consumer.ConsumerMessageSenderFactory;
import pl.allegro.tech.hermes.consumers.consumer.batch.ByteBufferMessageBatchFactoryProvider;
import pl.allegro.tech.hermes.consumers.consumer.batch.MessageBatchFactory;
import pl.allegro.tech.hermes.consumers.consumer.converter.AvroToJsonMessageConverter;
import pl.allegro.tech.hermes.consumers.consumer.converter.DefaultMessageConverterResolver;
import pl.allegro.tech.hermes.consumers.consumer.converter.MessageConverterResolver;
import pl.allegro.tech.hermes.consumers.consumer.converter.NoOperationMessageConverter;
import pl.allegro.tech.hermes.consumers.consumer.interpolation.MessageBodyInterpolator;
import pl.allegro.tech.hermes.consumers.consumer.interpolation.UriInterpolator;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthAccessTokens;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthAccessTokensLoader;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthConsumerAuthorizationHandler;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthProvidersNotifyingCache;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthProvidersNotifyingCacheFactory;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthSubscriptionAccessTokens;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthSubscriptionHandlerFactory;
import pl.allegro.tech.hermes.consumers.consumer.oauth.OAuthTokenRequestRateLimiterFactory;
import pl.allegro.tech.hermes.consumers.consumer.oauth.client.OAuthClient;
import pl.allegro.tech.hermes.consumers.consumer.oauth.client.OAuthHttpClient;
import pl.allegro.tech.hermes.consumers.consumer.oauth.client.OAuthHttpClientFactory;
import pl.allegro.tech.hermes.consumers.consumer.offset.ConsumerPartitionAssignmentState;
import pl.allegro.tech.hermes.consumers.consumer.offset.OffsetQueue;
import pl.allegro.tech.hermes.consumers.consumer.rate.ConsumerRateLimitSupervisor;
import pl.allegro.tech.hermes.consumers.consumer.rate.calculator.OutputRateCalculatorFactory;
import pl.allegro.tech.hermes.consumers.consumer.rate.maxrate.MaxRatePathSerializer;
import pl.allegro.tech.hermes.consumers.consumer.rate.maxrate.MaxRateProviderFactory;
import pl.allegro.tech.hermes.consumers.consumer.rate.maxrate.MaxRateRegistry;
import pl.allegro.tech.hermes.consumers.consumer.rate.maxrate.MaxRateRegistryFactory;
import pl.allegro.tech.hermes.consumers.consumer.rate.maxrate.MaxRateSupervisor;
import pl.allegro.tech.hermes.consumers.consumer.receiver.ReceiverFactory;
import pl.allegro.tech.hermes.consumers.consumer.receiver.kafka.BasicMessageContentReaderFactory;
import pl.allegro.tech.hermes.consumers.consumer.receiver.kafka.KafkaHeaderExtractor;
import pl.allegro.tech.hermes.consumers.consumer.receiver.kafka.KafkaMessageReceiverFactory;
import pl.allegro.tech.hermes.consumers.consumer.receiver.kafka.MessageContentReaderFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.HttpMessageBatchSenderFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.MessageBatchSenderFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.MessageSenderFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.MessageSendingResult;
import pl.allegro.tech.hermes.consumers.consumer.sender.ProtocolMessageSenderProvider;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.DefaultHttpMetadataAppender;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.DefaultHttpRequestFactoryProvider;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.DefaultSendingResultHandlers;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.EmptyHttpHeadersProvidersFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.Http2ClientFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.Http2ClientHolder;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.HttpClientFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.HttpClientsFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.HttpClientsWorkloadReporter;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.HttpHeadersProvidersFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.HttpRequestFactoryProvider;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.JettyHttpMessageSenderProvider;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.SendingResultHandlers;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.SslContextFactoryProvider;
import pl.allegro.tech.hermes.consumers.consumer.sender.http.auth.HttpAuthorizationProviderFactory;
import pl.allegro.tech.hermes.consumers.consumer.sender.jms.JmsHornetQMessageSenderProvider;
import pl.allegro.tech.hermes.consumers.consumer.sender.jms.JmsMetadataAppender;
import pl.allegro.tech.hermes.consumers.consumer.sender.resolver.EndpointAddressResolver;
import pl.allegro.tech.hermes.consumers.consumer.sender.resolver.InterpolatingEndpointAddressResolver;
import pl.allegro.tech.hermes.consumers.consumer.sender.timeout.FutureAsyncTimeout;
import pl.allegro.tech.hermes.consumers.consumer.sender.timeout.FutureAsyncTimeoutFactory;
import pl.allegro.tech.hermes.consumers.consumer.trace.MetadataAppender;
import pl.allegro.tech.hermes.consumers.health.ConsumerHttpServer;
import pl.allegro.tech.hermes.consumers.health.ConsumerMonitor;
import pl.allegro.tech.hermes.consumers.message.undelivered.UndeliveredMessageLogPersister;
import pl.allegro.tech.hermes.consumers.registry.ConsumerNodesRegistry;
import pl.allegro.tech.hermes.consumers.registry.ConsumerNodesRegistryFactory;
import pl.allegro.tech.hermes.consumers.subscription.cache.SubscriptionCacheFactory;
import pl.allegro.tech.hermes.consumers.subscription.cache.SubscriptionsCache;
import pl.allegro.tech.hermes.consumers.subscription.id.SubscriptionIdProvider;
import pl.allegro.tech.hermes.consumers.subscription.id.SubscriptionIdProviderFactory;
import pl.allegro.tech.hermes.consumers.subscription.id.SubscriptionIds;
import pl.allegro.tech.hermes.consumers.subscription.id.SubscriptionIdsCacheFactory;
import pl.allegro.tech.hermes.consumers.supervisor.ConsumerFactory;
import pl.allegro.tech.hermes.consumers.supervisor.ConsumersExecutorService;
import pl.allegro.tech.hermes.consumers.supervisor.ConsumersSupervisor;
import pl.allegro.tech.hermes.consumers.supervisor.NonblockingConsumersSupervisor;
import pl.allegro.tech.hermes.consumers.supervisor.monitor.ConsumersRuntimeMonitor;
import pl.allegro.tech.hermes.consumers.supervisor.monitor.ConsumersRuntimeMonitorFactory;
import pl.allegro.tech.hermes.consumers.supervisor.process.Retransmitter;
import pl.allegro.tech.hermes.consumers.supervisor.workload.ClusterAssignmentCache;
import pl.allegro.tech.hermes.consumers.supervisor.workload.ClusterAssignmentCacheFactory;
import pl.allegro.tech.hermes.consumers.supervisor.workload.ConsumerAssignmentCache;
import pl.allegro.tech.hermes.consumers.supervisor.workload.ConsumerAssignmentCacheFactory;
import pl.allegro.tech.hermes.consumers.supervisor.workload.ConsumerAssignmentRegistry;
import pl.allegro.tech.hermes.consumers.supervisor.workload.ConsumerAssignmentRegistryFactory;
import pl.allegro.tech.hermes.consumers.supervisor.workload.SupervisorController;
import pl.allegro.tech.hermes.consumers.supervisor.workload.SupervisorControllerFactory;
import pl.allegro.tech.hermes.domain.filtering.MessageFilterSource;
import pl.allegro.tech.hermes.domain.filtering.MessageFilters;
import pl.allegro.tech.hermes.domain.filtering.chain.FilterChainFactory;

import javax.inject.Singleton;
import javax.jms.Message;
import java.util.Collections;

public class ConsumersBinder extends AbstractBinder {

    @Override
    protected void configure() {
        bindSingleton(ConsumerHttpServer.class);

        bind(KafkaMessageReceiverFactory.class).in(Singleton.class).to(ReceiverFactory.class);
        bind(BasicMessageContentReaderFactory.class).in(Singleton.class).to(MessageContentReaderFactory.class);
        bind(MessageBodyInterpolator.class).in(Singleton.class).to(UriInterpolator.class);
        bind(InterpolatingEndpointAddressResolver.class).to(EndpointAddressResolver.class).in(Singleton.class);
        bind(JmsHornetQMessageSenderProvider.class).to(ProtocolMessageSenderProvider.class)
                .in(Singleton.class).named("defaultJmsMessageSenderProvider");
        bind(JettyHttpMessageSenderProvider.class).to(ProtocolMessageSenderProvider.class)
                .in(Singleton.class).named("defaultHttpMessageSenderProvider");
        bind(EmptyHttpHeadersProvidersFactory.class).to(HttpHeadersProvidersFactory.class).in(Singleton.class);
        bind(DefaultSendingResultHandlers.class).to(SendingResultHandlers.class).in(Singleton.class);
        bind(DefaultHttpRequestFactoryProvider.class).to(HttpRequestFactoryProvider.class).in(Singleton.class);

        bind("consumer").named("moduleName").to(String.class);

        bind(NonblockingConsumersSupervisor.class).in(Singleton.class).to(ConsumersSupervisor.class);
        bindSingleton(MessageSenderFactory.class);
        bindSingleton(HttpAuthorizationProviderFactory.class);
        bindSingleton(ConsumerFactory.class);
        bindSingleton(ConsumerRateLimitSupervisor.class);
        bindSingleton(ConsumersExecutorService.class);
        bindSingleton(OutputRateCalculatorFactory.class);
        bindSingleton(ZookeeperAdminCache.class);
        bindSingleton(InstrumentedExecutorServiceFactory.class);
        bindSingleton(ConsumerMessageSenderFactory.class);
        bindSingleton(NoOperationMessageConverter.class);
        bindSingleton(AvroToJsonMessageConverter.class);
        bind(DefaultMessageConverterResolver.class).in(Singleton.class).to(MessageConverterResolver.class);
        bindSingleton(OffsetQueue.class);
        bindSingleton(ConsumerPartitionAssignmentState.class);
        bindSingleton(Retransmitter.class);
        bindSingleton(ConsumerMonitor.class);
        bindSingleton(SslContextFactoryProvider.class);
        bindSingleton(KafkaHeaderExtractor.class);
        bind(JmsMetadataAppender.class).in(Singleton.class).to(new TypeLiteral<MetadataAppender<Message>>() {});
        bind(DefaultHttpMetadataAppender.class).in(Singleton.class)
                .to(new TypeLiteral<MetadataAppender<Request>>() {});

        bindFactory(FutureAsyncTimeoutFactory.class).in(Singleton.class)
                .to(new TypeLiteral<FutureAsyncTimeout<MessageSendingResult>>(){});
        bindSingleton(HttpClientsFactory.class);

        bindFactory(ConsumerNodesRegistryFactory.class).in(Singleton.class).to(ConsumerNodesRegistry.class);

        bindFactory(SubscriptionCacheFactory.class).in(Singleton.class).to(SubscriptionsCache.class);
        bindFactory(SubscriptionIdProviderFactory.class).in(Singleton.class).to(SubscriptionIdProvider.class);
        bindFactory(SubscriptionIdsCacheFactory.class).in(Singleton.class).to(SubscriptionIds.class);
        bindFactory(ConsumerAssignmentCacheFactory.class).in(Singleton.class).to(ConsumerAssignmentCache.class);
        bindFactory(ClusterAssignmentCacheFactory.class).in(Singleton.class).to(ClusterAssignmentCache.class);
        bindSingleton(HttpClientsWorkloadReporter.class);

        bindFactory(UndeliveredMessageLogFactory.class).in(Singleton.class).to(UndeliveredMessageLog.class);
        bindFactory(ConsumerAssignmentRegistryFactory.class).in(Singleton.class).to(ConsumerAssignmentRegistry.class);
        bindFactory(SupervisorControllerFactory.class).in(Singleton.class).to(SupervisorController.class);
        bindFactory(ConsumersRuntimeMonitorFactory.class).in(Singleton.class).to(ConsumersRuntimeMonitor.class);

        bindFactory(HttpClientFactory.class).in(Singleton.class).to(HttpClient.class).named("http-1-client");
        bindFactory(OAuthHttpClientFactory.class).in(Singleton.class).to(HttpClient.class).named("oauth-http-client");
        bindFactory(Http2ClientFactory.class).in(Singleton.class).to(Http2ClientHolder.class);

        bindSingleton(MaxRatePathSerializer.class);
        bindSingleton(MaxRateSupervisor.class);
        bindSingleton(MaxRateProviderFactory.class);
        bindFactory(MaxRateRegistryFactory.class).in(Singleton.class).to(MaxRateRegistry.class);

        bindSingleton(UndeliveredMessageLogPersister.class);
        bindFactory(ByteBufferMessageBatchFactoryProvider.class).in(Singleton.class).to(MessageBatchFactory.class);
        bind(HttpMessageBatchSenderFactory.class).to(MessageBatchSenderFactory.class).in(Singleton.class);
        bindSingleton(FilterChainFactory.class);
        bind(new MessageFilters(Collections.emptyList(), Collections.emptyList())).to(MessageFilterSource.class);

        bind(OAuthConsumerAuthorizationHandler.class).in(Singleton.class).to(ConsumerAuthorizationHandler.class);
        bindSingleton(OAuthSubscriptionHandlerFactory.class);
        bindSingleton(OAuthTokenRequestRateLimiterFactory.class);
        bindSingleton(OAuthAccessTokensLoader.class);
        bind(OAuthHttpClient.class).in(Singleton.class).to(OAuthClient.class);
        bind(OAuthSubscriptionAccessTokens.class).in(Singleton.class).to(OAuthAccessTokens.class);
        bindFactory(OAuthProvidersNotifyingCacheFactory.class).in(Singleton.class)
                .to(OAuthProvidersNotifyingCache.class);
    }

    private <T> void bindSingleton(Class<T> clazz) {
        bind(clazz).in(Singleton.class).to(clazz);
    }
}
