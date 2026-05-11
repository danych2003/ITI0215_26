package model;

import util.CanonicalJsonUtils;

import java.math.BigDecimal;

public record TransactionPayload(
        String from,
        String to,
        BigDecimal amount,
        String timestamp
) {
    public String canonicalJson() {
        return "{"
                + "\"from\":" + CanonicalJsonUtils.quote(from)
                + ",\"to\":" + CanonicalJsonUtils.quote(to)
                + ",\"amount\":" + CanonicalJsonUtils.decimal(amount)
                + ",\"timestamp\":" + CanonicalJsonUtils.quote(timestamp)
                + "}";
    }
}
