package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Một lần thanh toán (1 booking có thể có nhiều lần: lần đầu thất bại, khách thử lại).
 * txn_ref là mã giao dịch gửi sang VNPay và là khóa để nhận diện IPN (UNIQUE).
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "txn_ref", nullable = false)
    private String txnRef;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_txn_no")
    private String providerTxnNo;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "bank_code")
    private String bankCode;

    /**
     * Lưu NGUYÊN VẸN dữ liệu VNPay gửi về (cột JSONB) để đối soát / khiếu nại sau này.
     * Hibernate 6 tự chuyển Map <-> JSON nhờ @JdbcTypeCode(SqlTypes.JSON).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_callback")
    private Map<String, String> rawCallback;

    @Column(name = "paid_at")
    private Instant paidAt;

    public static Payment newVnPay(Booking booking, String txnRef, long amount) {
        Payment payment = new Payment();
        payment.booking = booking;
        payment.txnRef = txnRef;
        payment.amount = amount;
        payment.provider = "VNPAY";
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public void markSuccess(String providerTxnNo, String bankCode, String responseCode,
                            Instant paidAt, Map<String, String> rawCallback) {
        recordResult(PaymentStatus.SUCCESS, providerTxnNo, bankCode, responseCode, paidAt, rawCallback);
    }

    public void markRefundRequired(String providerTxnNo, String bankCode, String responseCode,
                                   Instant paidAt, Map<String, String> rawCallback) {
        recordResult(PaymentStatus.REFUND_REQUIRED, providerTxnNo, bankCode, responseCode, paidAt, rawCallback);
    }

    public void markFailed(String providerTxnNo, String bankCode, String responseCode, Map<String, String> rawCallback) {
        recordResult(PaymentStatus.FAILED, providerTxnNo, bankCode, responseCode, null, rawCallback);
    }

    private void recordResult(PaymentStatus newStatus, String providerTxnNo, String bankCode, String responseCode,
                              Instant paidAt, Map<String, String> rawCallback) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment " + txnRef + " already processed: " + status);
        }
        this.status = newStatus;
        this.providerTxnNo = providerTxnNo;
        this.bankCode = bankCode;
        this.responseCode = responseCode;
        this.paidAt = paidAt;
        this.rawCallback = rawCallback;
    }
}
