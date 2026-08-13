package com.example.rag.config;

import com.example.rag.service.event.AgentEventRedisChannel;
import com.example.rag.service.event.AgentRunEventRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Agent SSE 跨实例 Redis Pub/Sub 配置。 */
@Configuration
public class RedisPubSubConfig {
    @Bean
    public RedisMessageListenerContainer agentEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            AgentRunEventRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(AgentEventRedisChannel.NAME));
        return container;
    }
}
