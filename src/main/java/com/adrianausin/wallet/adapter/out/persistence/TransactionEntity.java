package com.adrianausin.wallet.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "transactions")
class TransactionEntity {

    enum Type {
        DEPOSIT, WITHDRAWAL, TRANSFER, CONVERSION
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "source_wallet_id")
    private @Nullable UUID sourceWalletId;

    @Column(name = "target_wallet_id")
    private @Nullable UUID targetWalletId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private @Nullable String description;

    @Column(name = "converted_amount", precision = 19, scale = 4)
    private @Nullable BigDecimal convertedAmount;

    @Column(name = "converted_currency", length = 3)
    private @Nullable String convertedCurrency;

    @Column(name = "exchange_rate", precision = 19, scale = 10)
    private @Nullable BigDecimal exchangeRate;

    @Column(name = "rate_date")
    private @Nullable LocalDate rateDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionEntity() {
    }

    TransactionEntity(UUID id, Type type, @Nullable UUID sourceWalletId, @Nullable UUID targetWalletId,
                      BigDecimal amount, String currency, @Nullable String description, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.createdAt = createdAt;
    }

    TransactionEntity withConversion(BigDecimal convertedAmount, String convertedCurrency, BigDecimal exchangeRate,
                                     LocalDate rateDate) {
        this.convertedAmount = convertedAmount;
        this.convertedCurrency = convertedCurrency;
        this.exchangeRate = exchangeRate;
        this.rateDate = rateDate;
        return this;
    }

    UUID getId() {
        return id;
    }

    Type getType() {
        return type;
    }

    @Nullable UUID getSourceWalletId() {
        return sourceWalletId;
    }

    @Nullable UUID getTargetWalletId() {
        return targetWalletId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    @Nullable String getDescription() {
        return description;
    }

    @Nullable BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    @Nullable String getConvertedCurrency() {
        return convertedCurrency;
    }

    @Nullable BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    @Nullable LocalDate getRateDate() {
        return rateDate;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
