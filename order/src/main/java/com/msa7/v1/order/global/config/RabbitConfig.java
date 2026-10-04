package com.msa7.v1.order.global.config;

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
public class RabbitConfig {
	// order -> delivery (Outbox Relay가 발행)
	public static final String ORDER_EXCHANGE = "order-exchange";
	public static final String ORDER_CREATED_ROUTING_KEY = "order.created";

	// 재시도 한도를 넘긴 주문 이벤트를 모아두는 곳
	public static final String ORDER_DLX = "order-exchange.dlx";
	public static final String ORDER_DLQ = "order-dlq";
	public static final String ORDER_DLQ_ROUTING_KEY = "order.dlq";

	// delivery -> order (SAGA 응답 수신)
	public static final String DELIVERY_EXCHANGE = "delivery-exchange";
	public static final String DELIVERY_CREATED_QUEUE = "delivery-created-queue";
	public static final String DELIVERY_CREATED_ROUTING_KEY = "delivery.created";
	public static final String DELIVERY_FAIL_QUEUE= "delivery-fail-queue";
	public static final String DELIVERY_FAIL_ROUTING_KEY = "delivery.fail";

	@Bean
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	/*
	 * 발행 대상 익스체인지.
	 * delivery-service도 같은 이름으로 선언하지만 선언은 멱등이므로 어느 쪽이 먼저 떠도 문제가 없다.
	 */
	@Bean
	public DirectExchange orderExchange() {
		return new DirectExchange(ORDER_EXCHANGE);
	}

	@Bean
	public DirectExchange orderDeadLetterExchange() {
		return new DirectExchange(ORDER_DLX);
	}

	@Bean
	public Queue orderDeadLetterQueue() {
		return QueueBuilder.durable(ORDER_DLQ).build();
	}

	@Bean
	public Binding orderDeadLetterBinding(Queue orderDeadLetterQueue, DirectExchange orderDeadLetterExchange) {
		return BindingBuilder.bind(orderDeadLetterQueue).to(orderDeadLetterExchange).with(ORDER_DLQ_ROUTING_KEY);
	}

	@Bean
	public DirectExchange deliveryExchange() {
		return new DirectExchange(DELIVERY_EXCHANGE);
	}
	@Bean
	public Queue deliverySuccessQueue(){
		return QueueBuilder.durable(DELIVERY_CREATED_QUEUE).build();
	}

	@Bean
	public Queue deliveryFailQueue() {
		return QueueBuilder.durable(DELIVERY_FAIL_QUEUE).build();
	}

	/*
	 * DirectExchange 빈이 여러 개이므로 파라미터 이름을 빈 이름과 일치시켜 주입 대상을 특정한다.
	 * (이름을 맞추지 않으면 NoUniqueBeanDefinitionException이 난다.)
	 */
	@Bean
	public Binding deliveryFailBinding(Queue deliveryFailQueue, DirectExchange deliveryExchange){
		return BindingBuilder.bind(deliveryFailQueue).to(deliveryExchange).with(DELIVERY_FAIL_ROUTING_KEY);}

	@Bean
	public Binding deliverySuccessBinding(Queue deliverySuccessQueue, DirectExchange deliveryExchange){
		return BindingBuilder.bind(deliverySuccessQueue).to(deliveryExchange).with(DELIVERY_CREATED_ROUTING_KEY);
	}

}
