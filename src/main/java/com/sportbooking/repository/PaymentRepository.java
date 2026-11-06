package com.sportbooking.repository;

import com.sportbooking.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxnRef(String txnRef);

    /**
     * SELECT ... FOR UPDATE trên ĐÚNG 1 dòng payment.
     * VNPay có thể gửi cùng 1 IPN nhiều lần, thậm chí đồng thời (retry). Request thứ 2 phải đợi
     * request thứ 1 commit rồi mới đọc -> thấy payment đã xử lý -> trả "02 Order already confirmed".
     *
     * Ở đây dùng pessimistic lock (khác GĐ 4) vì: chỉ khóa 1 dòng của 1 giao dịch, thời gian khóa rất ngắn,
     * và với cùng 1 txn_ref thì tranh chấp không phải "hiếm" mà là kịch bản retry có chủ đích.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.txnRef = :txnRef")
    Optional<Payment> findByTxnRefForUpdate(@Param("txnRef") String txnRef);
}
