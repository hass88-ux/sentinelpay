package com.sentinelpay.backend.pipeline;

import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Profile("pipeline")
@EnableKafka
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource pipelineDataSource(PipelineProperties properties) {
        var config = new HikariConfig();
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(3000);
        config.setValidationTimeout(1000);
        config.addDataSourceProperty("connectTimeout", "3");
        config.addDataSourceProperty("socketTimeout", "15");
        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    Flyway pipelineMigrations(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).load();
    }

    @Bean
    PaymentStore paymentStore(DataSource dataSource, Flyway pipelineMigrations) {
        return new PaymentStore(dataSource);
    }

    @Bean
    PaymentMessageCodec paymentMessageCodec(ObjectMapper mapper) {
        return new PaymentMessageCodec(mapper);
    }

    @Bean
    PaymentConsumer paymentConsumer(PaymentStore store, PaymentMessageCodec codec) {
        return new PaymentConsumer(store, codec);
    }

    @Bean
    KafkaAdmin kafkaAdmin(PipelineProperties properties) {
        var admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers()));
        admin.setFatalIfBrokerNotAvailable(true);
        return admin;
    }

    @Bean
    KafkaAdmin.NewTopics paymentTopics(PipelineProperties properties) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(properties.topic()).partitions(properties.partitions()).replicas(1).build(),
                TopicBuilder.name(properties.deadLetterTopic()).partitions(properties.partitions()).replicas(1).build());
    }

    @Bean
    ProducerFactory<String, String> paymentProducerFactory(PipelineProperties properties) {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000));
    }

    @Bean
    KafkaTemplate<String, String> paymentKafkaTemplate(ProducerFactory<String, String> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean
    PaymentPublisher paymentPublisher(KafkaTemplate<String, String> template, PaymentMessageCodec codec,
            PipelineProperties properties) {
        return new PaymentPublisher(template, codec, properties.topic());
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            PipelineProperties properties, KafkaTemplate<String, String> template) {
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10));
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        var deadLetters = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        deadLetters.setFailIfSendResultIsError(true);
        deadLetters.setWaitForSendResultTimeout(Duration.ofSeconds(6));
        var handler = new DefaultErrorHandler((record, exception) -> {
            Throwable cause = exception;
            while (cause != null && !(cause instanceof IllegalArgumentException)) cause = cause.getCause();
            if (cause != null) {
                deadLetters.accept(record, exception);
            } else {
                // Never discard an event because the database is temporarily unavailable.
                throw new org.springframework.kafka.KafkaException("Payment remains uncommitted for retry", exception);
            }
        }, new FixedBackOff(1000, 2));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
