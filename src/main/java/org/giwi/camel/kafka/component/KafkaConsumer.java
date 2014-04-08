/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.giwi.camel.kafka.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.Message;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Giwi Softwares
 * 
 */
public class KafkaConsumer extends DefaultConsumer {
	private ExecutorService executor;
	private final List<KafkaStream<byte[], byte[]>> streams;
	private final KafkaEndpoint endpoint;
	private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumer.class);
	private final ConsumerConnector connector;

	/**
	 * @param endpoint
	 * @param processor
	 */
	public KafkaConsumer(final KafkaEndpoint endpoint, final Processor processor) {
		super(endpoint, processor);
		this.endpoint = endpoint;

		final String topic = endpoint.getTopicName();
		final Properties props = new Properties();
		props.put("zookeeper.connect", endpoint.getZkConnect());
		props.put("group.id", endpoint.getGroupId());
		props.put("socket.timeout.ms", endpoint.getSocketTimeoutMs());
		props.put("socket.receive.buffer.bytes", endpoint.getSocketBuffersize());
		props.put("fetch.message.max.bytes", endpoint.getFetchSize());
		//props.put("backoff.increment.ms", endpoint.getBackoffIncrementMs());
		props.put("queued.max.message.chunks", endpoint.getQueuedchunksMax());
		props.put("auto.commit.enable", endpoint.getAutocommitEnable());
		props.put("auto.commit.interval.ms", endpoint.getAutocommitIntervalMs());
		props.put("auto.offset.reset", endpoint.getAutooffsetReset());
		props.put("consumer.timeout.ms", endpoint.getConsumerTimeoutMs());
		props.put("rebalance.max.retries", endpoint.getRebalanceRetriesMax());
		//props.put("mirror.topics.whitelist", endpoint.getMirrorTopicsWhitelist());
		//props.put("mirror.topics.blacklist", endpoint.getMirrorTopicsBlacklist());
		//props.put("mirror.consumer.numthreads", endpoint.getMirrorConsumerNumthreads());

		final ConsumerConfig config = new ConsumerConfig(props);
		connector = Consumer.createJavaConsumerConnector(config);

		final Map<String, Integer> topicmap = new HashMap<String, Integer>() {
			private static final long serialVersionUID = 1L;
			{
				put(topic, endpoint.getConcurrentConsumers());
			}
		};
		streams = connector.createMessageStreams(topicmap).get(topic);
		if (LOG.isInfoEnabled()) {
			LOG.info("Kafka Consumer Component initialized");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.impl.DefaultConsumer#doStart()
	 */
	@Override
	protected void doStart() throws Exception {
		super.doStart();
		if (LOG.isInfoEnabled()) {
			LOG.info("Kafka Consumer Component started");
		}
		executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), endpoint.getConcurrentConsumers());
		// consume the messages in the threads
		if (!executor.isShutdown()) {
			for (final KafkaStream<byte[], byte[]> stream : streams) {
				final Klistener kl = new Klistener();
				kl.setConsumer(this);
				kl.setStream(stream);
				kl.setEndpoint(endpoint);
				executor.submit(kl);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.impl.DefaultConsumer#doStop()
	 */
	@Override
	protected void doStop() throws Exception {
		super.doStop();
		if (connector != null) {
			connector.commitOffsets();
			connector.shutdown();
		}
		if (LOG.isInfoEnabled()) {
			LOG.info("Kafka Consumer Component stoped");
		}
	}

}
