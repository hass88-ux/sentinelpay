package com.sentinelpay.backend.pipeline;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.sentinelpay.backend.incident.IncidentStore;
import com.sentinelpay.backend.monitoring.MonitoringService;
import com.sentinelpay.backend.transaction.*;
import static org.awaitility.Awaitility.await;

/** Historical synthetic fixtures, sent through real Kafka; never a production data generator. */
final class IncidentDemo {
    record Result(Instant forecastAsOf, UUID caseId) {}

    static Result seed(TestPipeline runtime) {
        var end=Instant.now().truncatedTo(ChronoUnit.MINUTES).minusSeconds(180);
        var publisher=runtime.context.getBean(PaymentPublisher.class);
        var payments=runtime.context.getBean(PaymentStore.class);
        var ids=new ArrayList<String>();
        for(int i=0;i<5;i++) {
            var events=batch(100,end.minusSeconds((4-i)*60L),i*4,200+i*150);
            publisher.publish(events);
            events.forEach(e -> ids.add(e.id()));
        }
        var spike=batch(20,end.plusSeconds(60),18,2500);
        publisher.publish(spike);
        spike.forEach(e -> ids.add(e.id()));
        await().atMost(Duration.ofSeconds(30)).until(() -> ids.stream().allMatch(id -> payments.find(id).isPresent()));
        runtime.context.getBean(MonitoringService.class).run();
        var incident=runtime.context.getBean(IncidentStore.class).recent(false,100).stream()
                .filter(c -> c.currency().equals("USD") && c.bucket().equals(end.plusSeconds(60))).findFirst().orElseThrow();
        return new Result(end,incident.id());
    }

    private static List<TransactionEvent> batch(int count,Instant bucket,int failures,long latency) {
        var result=new ArrayList<TransactionEvent>();
        for(int i=0;i<count;i++) result.add(new TransactionEvent(UUID.randomUUID().toString(),bucket,
                BigDecimal.ONE,Currency.getInstance("USD"),i<failures ? TransactionStatus.FAILED : TransactionStatus.SUCCESS,latency));
        return result;
    }
}
