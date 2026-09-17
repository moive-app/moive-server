package com.moive.MoiveBE.domain.recommendation.event;

import com.moive.MoiveBE.domain.recommendation.service.AreaRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AreaRecommendationEventListener {

    private final AreaRecommendationService areaRecommendationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AreaRecommendationRequestedEvent event) {

        log.info("지역 추천 AFTER_COMMIT 이벤트 수신 - meetingId={}",
                event.meetingId());

        areaRecommendationService.recommend(event.meetingId());
    }
}