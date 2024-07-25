package com.commit.campus.controller;

import com.commit.campus.entity.Reservation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/reservations")
public class ReservationController {

    // 예약 등록
    @PostMapping("/")
    public ResponseEntity<Void> createReservation() {

        // 고객이 예약 페이지에 접속한 시점에 예약 가능 현황을 하나 차감
        // 만료 시간을 설정하여 만료 전까지 예약 확정 요청이 들어오지 않으면 자동 취소(자동으로 예약 취소 api 요청 호출)
        // 만료 시간은 1일로 설정

        return ResponseEntity.ok().build();
    }

    // 예약 확정(결제)
    @PutMapping
    public ResponseEntity<Void> finalizeReservation(@RequestBody Reservation reservation) {

        // 프론트에서 결제 버튼(기능은 구현x)을 누르면 예약 확정이 되며 만료 시간 해제됨.
        // 그밖에 변동 사항은 없음.
        // +) 추후 이 시점에 고객에게 예약 확정 알림 발송

        return ResponseEntity.ok().build();
    }

    // 예약 변경
    @PutMapping
    public ResponseEntity<Void> modifyReservation(@RequestBody List<Reservation> reservations) {

        // 예약 변경은 날짜만 가능하도록 설정
        // 캠핑장 or 시설 변경을 원할 시에는 예약 취소후 재 예약
        // 기존 날짜의 예약 가능 건수 차감한 것을 되돌리고, 새로운 날짜의 예약 가능 건수를 차감

        return ResponseEntity.ok().build();
    }

    // 예약 취소
    @PutMapping
    public ResponseEntity<Void> cancelReservation(@RequestBody List<Reservation> reservations, @RequestHeader("Authorization") String token) {

        // 기존 날짜의 예약 가능 건수 차감한 것을 되돌림
        // 예약 히스토리의 상태값 변경
        return ResponseEntity.ok().build();
    }
}
