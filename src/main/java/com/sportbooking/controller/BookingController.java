package com.sportbooking.controller;

import com.sportbooking.common.ApiResponse;
import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.security.UserPrincipal;
import com.sportbooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Booking", description = "Đặt sân và xem lượt đặt của tôi")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Đặt sân",
            description = "Tạo lượt đặt ở trạng thái PENDING, giữ chỗ trong thời gian hold-duration chờ thanh toán. "
                    + "Trả 409 SLOT_ALREADY_BOOKED nếu khung giờ đã có người giữ.")
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(@AuthenticationPrincipal UserPrincipal customer,
                                                               @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bookingService.createBooking(customer.id(), request)));
    }

    @Operation(summary = "Lịch sử đặt sân của tôi")
    @GetMapping("/me")
    public ApiResponse<PageResponse<BookingResponse>> getMine(
            @AuthenticationPrincipal UserPrincipal customer,
            @RequestParam(required = false) BookingStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(bookingService.getMyBookings(customer.id(), status, pageable));
    }

    @Operation(summary = "Chi tiết 1 lượt đặt của tôi")
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getOne(@AuthenticationPrincipal UserPrincipal customer,
                                               @PathVariable Long bookingId) {
        return ApiResponse.ok(bookingService.getMyBooking(customer.id(), bookingId));
    }
}
