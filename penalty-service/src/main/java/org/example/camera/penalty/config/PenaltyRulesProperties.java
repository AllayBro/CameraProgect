package org.example.camera.penalty.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "penalty")
public class PenaltyRulesProperties {

    private Double speedLimitKmh;
    private Double reviewConfidenceThreshold;
    private List<FineBracket> amounts = new ArrayList<>();

    public Double getSpeedLimitKmh() { return speedLimitKmh; }
    public void setSpeedLimitKmh(Double speedLimitKmh) { this.speedLimitKmh = speedLimitKmh; }

    public Double getReviewConfidenceThreshold() { return reviewConfidenceThreshold; }
    public void setReviewConfidenceThreshold(Double reviewConfidenceThreshold) { this.reviewConfidenceThreshold = reviewConfidenceThreshold; }

    public List<FineBracket> getAmounts() { return amounts; }
    public void setAmounts(List<FineBracket> amounts) { this.amounts = amounts; }

    public static class FineBracket {
        private Double fromOverKmh;
        private Double toOverKmh; // null = без верхней границы
        private Double amount;

        public Double getFromOverKmh() { return fromOverKmh; }
        public void setFromOverKmh(Double fromOverKmh) { this.fromOverKmh = fromOverKmh; }

        public Double getToOverKmh() { return toOverKmh; }
        public void setToOverKmh(Double toOverKmh) { this.toOverKmh = toOverKmh; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }

    @PostConstruct
    void validateConfig() {
        if (speedLimitKmh == null) throw new IllegalStateException("penalty.speed-limit-kmh is required");
        if (speedLimitKmh <= 0) throw new IllegalStateException("penalty.speed-limit-kmh must be > 0");

        if (reviewConfidenceThreshold == null) throw new IllegalStateException("penalty.review-confidence-threshold is required");
        if (reviewConfidenceThreshold < 0.0 || reviewConfidenceThreshold > 1.0)
            throw new IllegalStateException("penalty.review-confidence-threshold must be in [0..1]");

        if (amounts == null || amounts.isEmpty()) throw new IllegalStateException("penalty.amounts must not be empty");

        List<FineBracket> sorted = new ArrayList<>(amounts);
        sorted.sort(Comparator.comparing(FineBracket::getFromOverKmh, Comparator.nullsFirst(Double::compareTo)));

        Double expectedFrom = 0.0;

        for (int i = 0; i < sorted.size(); i++) {
            FineBracket b = sorted.get(i);

            if (b.getFromOverKmh() == null) throw new IllegalStateException("penalty.amounts[" + i + "].from-over-kmh is required");
            if (b.getAmount() == null) throw new IllegalStateException("penalty.amounts[" + i + "].amount is required");
            if (b.getFromOverKmh() < 0) throw new IllegalStateException("penalty.amounts[" + i + "].from-over-kmh must be >= 0");

            if (!b.getFromOverKmh().equals(expectedFrom)) {
                throw new IllegalStateException("penalty.amounts has gap/overlap: expected from-over-kmh=" + expectedFrom + " but got " + b.getFromOverKmh());
            }

            Double to = b.getToOverKmh();
            if (to == null) {
                if (i != sorted.size() - 1) {
                    throw new IllegalStateException("Only last penalty.amounts bracket may have to-over-kmh=null");
                }
                return; // последняя ступень без верхней границы
            }

            if (to <= b.getFromOverKmh()) {
                throw new IllegalStateException("penalty.amounts[" + i + "].to-over-kmh must be > from-over-kmh");
            }

            expectedFrom = to;
        }

        throw new IllegalStateException("Last penalty.amounts bracket must have to-over-kmh=null");
    }
}
