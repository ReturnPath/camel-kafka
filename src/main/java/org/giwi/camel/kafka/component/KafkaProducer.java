package org.giwi.camel.kafka.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.javaapi.producer.ProducerData;
import kafka.message.Message;
import kafka.producer.ProducerConfig;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.TypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

/**
 * @author Giwi Softwares
 *
 */
public class KafkaProducer extends DefaultProducer {

	private final KafkaEndpoint endpoint;
	private final Producer<String, Message> producer;
	private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);

	/**
	 * @param endpoint
	 * @throws ClassNotFoundException
	 */
	public KafkaProducer(final KafkaEndpoint endpoint) throws ClassNotFoundException {
		super(endpoint);
		this.endpoint = endpoint;
		final Properties props = new Properties();
		props.put("zk.connect", endpoint.getZkConnect());
		if (!"".equals(endpoint.getSerializerClass())) {
			props.put("serializer.class", endpoint.getSerializerClass());
		}
		if (!"".equals(endpoint.getPartitionerClass())) {
			props.put("partitioner.class", endpoint.getPartitionerClass());
		}
		props.put("producer.type", endpoint.getProducerType());
		props.put("broker.list", endpoint.getBrokerList());
		props.put("buffer.size", endpoint.getBufferSize());
		props.put("connect.timeout.ms", endpoint.getConnectTimeoutMs());
		props.put("socket.timeout.ms", endpoint.getSocketTimeoutMs());
		props.put("reconnect.interval", endpoint.getReconnectInterval());
		props.put("max.message.size", endpoint.getMaxMessageSize());
		props.put("compression.codec", endpoint.getCompressionCodec());
		props.put("compressed.topics", endpoint.getCompressedTopics());
		props.put("zk.read.num.retries", endpoint.getZkReadNumRetries());
		// producer.type=async
		if ("async".equals(endpoint.getProducerType())) {
			props.put("queue.time", endpoint.getQueueTime());
			props.put("queue.size", endpoint.getQueueSize());
			props.put("batch.size", endpoint.getBatchSize());
			if (!"".equals(endpoint.getEventHandler())) {
				props.put("event.handler", endpoint.getEventHandler());
			}
			props.put("event.handler.props", endpoint.getEventHandlerProps());
			if (!"".equals(endpoint.getCallbackHandler())) {
				props.put("callback.handler", endpoint.getCallbackHandler());
			}
			props.put("callback.handler.props", endpoint.getCallbackHandlerProps());
		}

		final ProducerConfig config = new ProducerConfig(props);
		if (LOG.isInfoEnabled()) {
			LOG.info("Kafka producer Component initialized");
		}
		producer = new kafka.javaapi.producer.Producer<String, Message>(config);
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.camel.impl.DefaultConsumer#doStart()
	 */
	@Override
	protected void doStart() throws Exception {
		super.doStart();
		if (LOG.isInfoEnabled()) {
			LOG.info("Kafka Producer Component started");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.camel.impl.DefaultProducer#doStop()
	 */
	@Override
	protected void doStop() throws Exception {
		super.doStop();
		producer.close();
		if (LOG.isInfoEnabled()) {
			LOG.info("Kafka Producer Component stoped");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.camel.Processor#process(org.apache.camel.Exchange)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void process(final Exchange exchange) throws Exception {
		String topicName;
		if (exchange.getIn().getHeaders().containsKey(KafkaComponent.TOPIC_NAME)) {
			topicName = exchange.getIn().getHeader(KafkaComponent.TOPIC_NAME, String.class);
		} else {
			topicName = endpoint.getTopicName();
		}

		// Don't just try to getBody(List.class) as TypeConverter's really good at taking non-list
		// items and turning them into lists.
		final Object body = exchange.getIn().getBody();
		if (body != null) {
			TypeConverter converter = exchange.getContext().getTypeConverter();

			if (body instanceof List) {
				final List<ProducerData<String, Message>> data = new ArrayList<ProducerData<String, Message>>();
				for(Object obj: (List)body) {
					final byte[] bytes = converter.convertTo(byte[].class, exchange, obj);
					if (bytes != null) {
						data.add(new ProducerData<String, Message>(topicName, new Message(bytes)));
					} else {
						throw new UnsupportedEncodingException("Unable to convert " + obj + " to byte[]");
					}
				}
				producer.send(data);
			} else {
				final byte[] bytes = converter.convertTo(byte[].class, exchange, body);
				if (bytes != null) {
					producer.send(new ProducerData<String, Message>(topicName, new Message(bytes)));
				} else {
					throw new UnsupportedEncodingException("Unable to convert " + body + " to byte[]");
				}
			}
			if (LOG.isInfoEnabled()) {
				LOG.info("Kafka Producer sent : " + body);
			}
		}
	}
}

