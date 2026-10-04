package com.msa7.v1.delivery.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {
	// order -> delivery (수신)
	public static final String ORDER_EXCHANGE = "order-exchange";
	public static final String ORDER_CREATED_QUEUE = "order-created-queue";
	public static final String ORDER_CREATED_ROUTING_KEY = "order.created";

	// delivery -> order (Outbox Relay가 발행)
	public static final String DELIVERY_EXCHANGE = "delivery-exchange";
	public static final String DELIVERY_ROUTING_KEY_PREFIX = "delivery.";

	// 재시도 한도를 넘긴 배송 이벤트를 모아두는 곳
	public static final String DELIVERY_DLX = "delivery-exchange.dlx";
	public static final String DELIVERY_DLQ = "delivery-dlq";
	public static final String DELIVERY_DLQ_ROUTING_KEY = "delivery.dlq";

	@Bean
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public DirectExchange orderExchange() {
		return new DirectExchange(ORDER_EXCHANGE);
	}
	@Bean
	public Queue orderCreatedQueue(){
		return QueueBuilder.durable(ORDER_CREATED_QUEUE).build();
	}

	/*
	 * DirectExchange 빈이 여러 개이므로 파라미터 이름을 빈 이름과 일치시켜 주입 대상을 특정한다.
	 * (이름을 맞추지 않으면 NoUniqueBeanDefinitionException이 난다.)
	 */
	@Bean
	public Binding orderCreatedBinding(Queue orderCreatedQueue, DirectExchange orderExchange){
		return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with(ORDER_CREATED_ROUTING_KEY);
	}

	/*
	 * 발행 대상 익스체인지.
	 * order-service도 같은 이름으로 선언하지만 선언은 멱등이므로 어느 쪽이 먼저 떠도 문제가 없다.
	 */
	@Bean
	public DirectExchange deliveryExchange() {
		return new DirectExchange(DELIVERY_EXCHANGE);
	}

	@Bean
	public DirectExchange deliveryDeadLetterExchange() {
		return new DirectExchange(DELIVERY_DLX);
	}

	@Bean
	public Queue deliveryDeadLetterQueue() {
		return QueueBuilder.durable(DELIVERY_DLQ).build();
	}

	@Bean
	public Binding deliveryDeadLetterBinding(Queue deliveryDeadLetterQueue,
		DirectExchange deliveryDeadLetterExchange) {
		return BindingBuilder.bind(deliveryDeadLetterQueue).to(deliveryDeadLetterExchange)
			.with(DELIVERY_DLQ_ROUTING_KEY);
	}

}
