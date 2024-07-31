package com.commit.campus.entity;

import lombok.Getter;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Getter
public class Reservation implements Serializable {
    @Id
    private int reservationId;  // 예약 ID
    private long campId;        // 예약한 캠핑장
    private long campFacsId;    // 예약한 시설
    private LocalDateTime reservationDate;  // 예약 날짜
    private Date entryDate;  // 입실 날짜
    private Date leavingDate;  // 퇴실 날짜
    private String reservationStatus;  // 예약 상태
    private String gearRentalStatus;  // 장비 대여 상태

    // fk
    @ManyToOne
    @JoinColumn(name = "camp_facs_id", insertable = false, updatable = false)
    private CampingFacilities campingFacilities;  // 캠핑장 시설 ID

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;  // 사용자 ID
}
