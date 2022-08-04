package com.instashare.instasharecore.events;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class Amqp {

  private final AmqpAdmin amqpAdmin;

  private final DestinationInfo destinationInfo;

  @PostConstruct
  public void setup() {
    val exchange = ExchangeBuilder.directExchange(destinationInfo.exchange()).durable(true).build();
    amqpAdmin.declareExchange(exchange);
    val queue = QueueBuilder.durable(destinationInfo.routingKey()).build();
    amqpAdmin.declareQueue(queue);
    val binding =
        BindingBuilder.bind(queue).to(exchange).with(destinationInfo.routingKey()).noargs();
    amqpAdmin.declareBinding(binding);
  }
}
