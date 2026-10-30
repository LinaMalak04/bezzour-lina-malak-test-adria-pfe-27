package com.adria.bank.audit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Read-only : this service has no database and no client to the business services
@Component
public class TransactionEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransactionEventsConsumer.class);

    @KafkaListener(topics = "bank.transactions.created")
    public void onTransactionCreated(ConsumerRecord<String, TransactionCreatedEvent> record) {
        log.info("Event received [partition={}, offset={}, key={}] : {}",
                record.partition(), record.offset(), record.key(), record.value());
    }
}
