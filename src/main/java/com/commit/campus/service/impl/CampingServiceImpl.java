package com.commit.campus.service.impl;

import com.commit.campus.entity.Camping;
import com.commit.campus.repository.CampingRepository;
import com.commit.campus.service.CampingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CampingServiceImpl implements CampingService {

    // CampingRepository를 주입받음
    @Autowired
    private CampingRepository campingRepository;

    // 모든 캠핑장 정보를 조회하는 메서드
    @Override
    public List<Camping> getAllCampings() {
        List<Camping> campings = campingRepository.findAll(); // 모든 캠핑장 정보를 데이터베이스에서 조회
        return campings; // 조회한 캠핑장 리스트를 반환
    }

    // 새로운 캠핑장을 생성하는 메서드
    @Override
    public Camping createCamping(Camping camping) {
        return campingRepository.save(camping); // 새로운 캠핑장을 데이터베이스에 저장하고 저장된 객체를 반환
    }

    // 특정 도와 시군구의 캠핑장 리스트를 페이지네이션과 정렬을 적용하여 조회하는 메서드
    @Override
    public List<Camping> getCampings(String doName, String sigunguName, int page, int size, String sort, String order) {
        int offset = page * size; // 페이지 번호와 페이지 크기를 기반으로 오프셋 계산
        List<Camping> campings = campingRepository.findCampings(doName, sigunguName, offset, size); // 특정 도와 시군구의 캠핑장 리스트를 조회
        return campings.stream()
                .sorted(getComparator(sort, order)) // 정렬 기준과 순서를 적용하여 캠핑장 리스트 정렬
                .collect(Collectors.toList()); // 정렬된 캠핑장 리스트를 리스트로 수집하여 반환
    }

    // 정렬 기준과 순서를 기반으로 Comparator를 반환하는 메서드
    private Comparator<Camping> getComparator(String sort, String order) {
        Comparator<Camping> comparator;
        if ("campName".equals(sort)) { // 정렬 기준이 캠핑장 이름인 경우
            comparator = Comparator.comparing(Camping::getCampName);
        } else { // 그 외에는 생성 날짜를 기준으로 정렬
            comparator = Comparator.comparing(Camping::getCreatedDate);
        }

        if ("desc".equalsIgnoreCase(order)) { // 정렬 순서가 내림차순인 경우
            comparator = comparator.reversed(); // Comparator를 역순으로 변경
        }

        return comparator; // 최종 Comparator 반환
    }
}
