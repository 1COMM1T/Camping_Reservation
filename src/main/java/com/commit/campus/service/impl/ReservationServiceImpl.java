package com.commit.campus.service.impl;

import com.commit.campus.dto.ReservationDTO;
import com.commit.campus.entity.Availability;
import com.commit.campus.entity.Camping;
import com.commit.campus.entity.Reservation;
import com.commit.campus.repository.AvailabilityRepository;
import com.commit.campus.repository.CampingRepository;
import com.commit.campus.repository.ReservationRepository;
import com.commit.campus.service.ReservationService;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final AvailabilityRepository availabilityRepository;
    private final CampingRepository campingRepository;
    private final RedisTemplate redisTemplate;
    private final RedisCommands redisCommands;

    private static long index = 1;
    private static final long DEFAULT_TTL_SECONDS = 7200;

    @Autowired
    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  AvailabilityRepository availabilityRepository,
                                  CampingRepository campingRepository,
                                  RedisTemplate redisTemplate,
                                  RedisCommands redisCommands) {
        this.reservationRepository = reservationRepository;
        this.availabilityRepository = availabilityRepository;
        this.campingRepository = campingRepository;
        this.redisTemplate = redisTemplate;
        this.redisCommands = redisCommands;
    }

    @Override
    public String redisHealthCheck() {
        try {
            ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
            opsForValue.set("health_check", "OK");
            String result = opsForValue.get("health_check");

            if ("OK".equals(result)) {
                return "레디스 실행중 ~,~";
            } else {
                return "레디스 서버 연결 실패!";
            }

        } catch (Exception e) {
            return "연결 실패했따: " + e.getMessage();
        }
    }

    @Override
    public String createReservation(ReservationDTO reservationDTO) {

        // 예약 가능 여부 체크
//        if (!isAvailable(reservationDTO)) {
//            throw new RuntimeException("해당 날짜에 예약 가능한 사이트가 없습니다.");
//        }

        LocalDateTime reservationDate = reservationDTO.getReservationDate();
        String reservationId = createReservationId(reservationDate);

        String key = "reservationInfo:" + reservationId;

        log.info("key = " + key);

        redisCommands.expire(key, DEFAULT_TTL_SECONDS);

        try {
            redisCommands.hset(key, "reservationId", reservationId);
            redisCommands.hset(key, "userId", reservationDTO.getUserId().toString());
            redisCommands.hset(key, "campId", reservationDTO.getCampId().toString());
            redisCommands.hset(key, "campFacsId", reservationDTO.getCampFacsId().toString());
            redisCommands.hset(key, "reservationDate", reservationDTO.getReservationDate().toString());
            redisCommands.hset(key, "entryDate", reservationDTO.getEntryDate().toString());
            redisCommands.hset(key, "leavingDate", reservationDTO.getLeavingDate().toString());
            redisCommands.hset(key, "gearRentalStatus", reservationDTO.getGearRentalStatus());

        } catch (RuntimeException e) {
            throw new RuntimeException("redis에 저장이 되지 않음");
        }

        return reservationId;
    }

    @Override
    public ReservationDTO confirmReservation(String reservationId) {

//        - 예약 확정
//        1. 사용자가 예약 아이디와 함께 예약 확정 요청을 보냄
//        2. 서비스 단에서 받아온 예약 아이디로 가장 먼저 redis의 데이터가 존재하는지 유무 판별
//        3. 데이터가 있다면 예약 테이블에 예약 정보를 저장
//        4. 예약 가능 테이블을 스캔하여 입실 날짜와 퇴실 날짜 사이의 예약 가능 개수를 차감
//        4.1. 스캔했는데 해당 날짜의 데이터가 없는 경우 캠핑장 아이디를 기준으로 캠핑장 테이블을 불러와 각 시설의 개수를 가져옴
//        4.2. 해당 날짜의 데이터가 있는 경우 받아온 시설 유형에 해당하는 개수를 하나 차감

        String key = "reservationInfo:" + reservationId;

        log.info("redis key = " + key);

        // redis 데이터가 살아있는지 판별
        Map<String, String> reservationInfo = redisCommands.hgetall(key);
        if (reservationInfo.isEmpty()) {
            throw new RuntimeException("이미 만료된 예약입니다.");
        }

        // 데이터 확인용 로그 출력
        for (Map.Entry<String, String> entry : reservationInfo.entrySet()) {
            log.info("Key: " + entry.getKey() + " / Value: " + entry.getValue());
        }

        // 예약 테이블에 정보 저장
        ReservationDTO reservationDTO = mapToReservationDTO(reservationInfo);

        Reservation reservation = Reservation.builder()
                .reservationId(reservationDTO.getReservationId())
                .campId(reservationDTO.getCampId())
                .campFacsId(reservationDTO.getCampFacsId())
                .userId(reservationDTO.getUserId())
                .reservationDate(reservationDTO.getReservationDate())
                .entryDate(reservationDTO.getEntryDate())
                .leavingDate(reservationDTO.getLeavingDate())
                .reservationStatus(reservationDTO.getReservationStatus())
                .gearRentalStatus(reservationDTO.getGearRentalStatus())
                .build();

        reservationRepository.save(reservation);

        // 캠핑장 아이디로 예약 가능 테이블을 스캔하여 입실 ~ 퇴실 날짜의 데이터가 존재하는지 여부 판단
        long campId = reservationDTO.getCampId();
        LocalDateTime entryDate = reservationDTO.getEntryDate();
        LocalDateTime leavingDate = reservationDTO.getLeavingDate();

        availabilityRepository.findByCampIdAndDate(campId, entryDate, leavingDate);
        // 스캔했는데 데이터가 없는 경우 -> 입실 ~ 퇴실 날짜 사이의 데이터를 새로 생성
            // 이 때 캠핑장 아이디를 가지고 캠핑 테이블을 조회하여 해당 캠핑장의 시설 개수 정보를 매핑해줌
        // 스캔했는데 데이터가 있는 경우 -> 입실 ~ 퇴실 날짜에 사용자가 예약한 시설 유형에 해당하는 카운트를 하나 차감(업데이트)
        // 이용 가능 개수 차감
        decreaseAvailability(reservationDTO);

        return reservationDTO;
    }

    private synchronized String createReservationId(LocalDateTime reservationDate) {

        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyMMddhhmmss");
        String formattedDate = reservationDate.format(dateFormat);

        String indexCode = String.format("%06d", index);
        index++;

        String reservationId = formattedDate + indexCode;

        return reservationId;
    }

    private ReservationDTO mapToReservationDTO(Map<String, String> reservationInfo) {

        ReservationDTO reservationDTO = new ReservationDTO();

        reservationDTO.setReservationId(Long.valueOf(reservationInfo.get("reservationId")));
        reservationDTO.setUserId(Long.valueOf(reservationInfo.get("userId")));
        reservationDTO.setCampId(Long.valueOf(reservationInfo.get("campId")));
        reservationDTO.setCampFacsId(Long.valueOf(reservationInfo.get("campFacsId")));
        reservationDTO.setReservationDate(LocalDateTime.parse(reservationInfo.get("reservationDate")));
        reservationDTO.setEntryDate(LocalDateTime.parse(reservationInfo.get("entryDate")));
        reservationDTO.setLeavingDate(LocalDateTime.parse(reservationInfo.get("leavingDate")));
        reservationDTO.setReservationStatus("예약 확정");
        reservationDTO.setGearRentalStatus(reservationInfo.get("gearRentalStatus"));
        reservationDTO.setCampFacsType(Integer.valueOf(reservationInfo.get("campFacsType")));

        return reservationDTO;
    }

    // 예약 가능 여부 체크 메소드
    private boolean isAvailable(ReservationDTO reservationDTO) {
        List<Date> dates = reservationDTO.getEntryDate().toLocalDate().datesUntil(reservationDTO.getLeavingDate().toLocalDate()).toList();

        for (LocalDate date : dates) {
            Availability availability = availabilityRepository.findByCampIdAndDate(
                    reservationDTO.getCampId(), java.sql.Date.valueOf(date)
            );

            if (availability == null) {
                Camping camping = campingRepository.findById(reservationDTO.getCampId()).orElseThrow(() -> new RuntimeException("캠핑 정보를 찾을 수 없습니다."));
                availability = createAvailability(camping, date);
                availabilityRepository.save(availability);
            }

            switch (reservationDTO.getCampFacsType()) {
                case 1: // 일반 사이트
                    return availability.getGeneralSiteAvail() > 0;
                case 2: // 자동차 사이트
                    return availability.getCarSiteAvail() > 0;
                case 3: // 글램핑 사이트
                    return availability.getGlampingSiteAvail() > 0;
                case 4: // 카라반 사이트
                    return availability.getCaravanSiteAvail() > 0;
                default:
                    return false;
            }
        }
    }

        // 예약 가능 개수 생성 메소드
    private Availability createAvailability(Camping camping, Date date) {
        Availability availability = new Availability();
        availability.setCampId(camping.getCampId());
        availability.setDate(java.sql.Date.valueOf(date));
        availability.setGeneralSiteAvail(camping.getGeneralSiteCnt());
        availability.setCarSiteAvail(camping.getCarSiteCnt());
        availability.setGlampingSiteAvail(camping.getGlampingSiteCnt());
        availability.setCaravanSiteAvail(camping.getCaravanSiteCnt());

        return availability;
    }

    // 이용 가능 개수 차감 메소드
    private void decreaseAvailability(ReservationDTO reservationDTO) {
        Availability availability = availabilityRepository.findByCampIdAndDate(
                reservationDTO.getCampId(), java.sql.Date.valueOf(reservationDTO.getReservationDate().toLocalDate())
        );

        if (availability != null) {
            switch (reservationDTO.getCampFacsId().intValue()) {
                case 1: // 일반 사이트
                    availability.setGeneralSiteAvail(availability.getGeneralSiteAvail() - 1);
                    break;
                case 2: // 자동차 사이트
                    availability.setCarSiteAvail(availability.getCarSiteAvail() - 1);
                    break;
                case 3: // 글램핑 사이트
                    availability.setGlampingSiteAvail(availability.getGlampingSiteAvail() - 1);
                    break;
                case 4: // 카라반 사이트
                    availability.setCaravanSiteAvail(availability.getCaravanSiteAvail() - 1);
                    break;
                default:
                    throw new RuntimeException("잘못된 캠프 시설 ID입니다.");
            }

            availabilityRepository.save(availability);
        }
    }
}

/*
    - 예약 등록
    1. 사용자가 예약 정보(캠핑장, 시설아이디, 입실일, 퇴실일, 장비 대여 여부)를 입력 후 요청을 보냄.
    2. 컨트롤러에서 해당 예약 정보를 받아 DTO에 위 정보들 + 시설 아이디로 해당 시설의 데이터를 가져와 시설 유형을 campFacsType에 저장
    3.

    - 예약 확정
    1. 사용자가 예약 아이디와 함께 예약 확정 요청을 보냄
    2. 서비스 단에서 받아온 예약 아이디로 가장 먼저 redis의 데이터가 존재하는지 유무 판별
    3. 데이터가 있다면 예약 테이블에 예약 정보를 저장
    4. 예약 가능 테이블을 스캔하여 입실 날짜와 퇴실 날짜 사이의 예약 가능 개수를 차감
        4.1. 스캔했는데 해당 날짜의 데이터가 없는 경우 캠핑장 아이디를 기준으로 캠핑장 테이블을 불러와 각 시설의 개수를 가져옴
        4.2. 해당 날짜의 데이터가 있는 경우 받아온 시설 유형에 해당하는 개수를 하나 차감
*/
