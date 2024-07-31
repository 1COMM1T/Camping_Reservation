package com.commit.campus.service;

import com.commit.campus.common.exceptions.ReviewAlreadyExistsException;
import com.commit.campus.dto.ReviewDTO;
import com.commit.campus.entity.MyReview;
import com.commit.campus.entity.Review;
import com.commit.campus.repository.MyReviewRepository;
import com.commit.campus.repository.RatingSummaryRepository;
import com.commit.campus.repository.ReviewRepository;
import com.commit.campus.repository.UserRepository;
import com.commit.campus.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTests {

    @Mock private ReviewRepository reviewRepository;
    @Mock private MyReviewRepository myReviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private RatingSummaryRepository ratingSummaryRepository;
    @Mock private ModelMapper modelMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private Review review;
    private ReviewDTO reviewDTO;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        review = Review.builder()
                .reviewId(1L)
                .campId(1L)
                .userId(101L)
                .reviewContent("리뷰 테스트")
                .rating((byte) 5)
                .reviewCreatedDate(now)
                .reviewModificationDate(now)
                .reviewImageUrl("image1.jpg")
                .build();

        reviewDTO = new ReviewDTO();
        reviewDTO.setReviewId(1L);
        reviewDTO.setCampId(1L);
        reviewDTO.setUserId(101L);
        reviewDTO.setReviewContent("리뷰 테스트");
        reviewDTO.setRating((byte) 5);
        reviewDTO.setReviewImageUrl("image1.jpg");
    }

    @Test
    void 정상적인_리뷰_조회() {
        // Given
        long campId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        List<Review> reviews = createTestReviews(campId, 2);
        Page<Review> reviewPage = new PageImpl<>(reviews, pageable, reviews.size());

        when(reviewRepository.findByCampId(campId, pageable)).thenReturn(reviewPage);
        when(userRepository.findNicknameByUserId(anyLong())).thenReturn("테스트 닉네임");
        when(modelMapper.map(any(Review.class), eq(ReviewDTO.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return convertToReviewDTO(review);
        });

        // When
        Page<ReviewDTO> result = reviewService.getReviewsByCampId(campId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);

        ReviewDTO firstReviewDTO = result.getContent().get(0);
        assertThat(firstReviewDTO.getCampId()).isEqualTo(campId);
        assertThat(firstReviewDTO.getUserNickname()).isEqualTo("테스트 닉네임");
        assertThat(firstReviewDTO.getReviewContent()).isEqualTo("리뷰 내용1");
        assertThat(firstReviewDTO.getRating()).isEqualTo((byte) 5);

        verify(reviewRepository).findByCampId(campId, pageable);
        verify(userRepository, times(2)).findNicknameByUserId(anyLong());
        verify(modelMapper, times(2)).map(any(Review.class), eq(ReviewDTO.class));
    }

    @Test
    void 빈_결과_조회() {
        // Given
        long campId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Review> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(reviewRepository.findByCampId(campId, pageable)).thenReturn(emptyPage);

        // When
        Page<ReviewDTO> result = reviewService.getReviewsByCampId(campId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(reviewRepository).findByCampId(campId, pageable);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(modelMapper);
    }

    @Test
    void 페이지_범위_초과_조회() {
        // Given
        long campId = 1L;
        Pageable outOfRangePageable = PageRequest.of(100, 10);
        Page<Review> emptyPage = new PageImpl<>(Collections.emptyList(), outOfRangePageable, 0);

        when(reviewRepository.findByCampId(campId, outOfRangePageable)).thenReturn(emptyPage);

        // When
        Page<ReviewDTO> result = reviewService.getReviewsByCampId(campId, outOfRangePageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getNumber()).isEqualTo(100);
        assertThat(result.getSize()).isEqualTo(10);

        verify(reviewRepository).findByCampId(campId, outOfRangePageable);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(modelMapper);
    }

    @Test
    void 캠핑장_정상_등록() throws ReviewAlreadyExistsException {
        // Given
        when(reviewRepository.existsByUserIdAndCampId(anyLong(), anyLong())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(review);
        when(myReviewRepository.findById(anyLong())).thenReturn(Optional.of(new MyReview(101L)));

        // When
        reviewService.createReview(reviewDTO);

        // Then

        // review 타입을 캡처하는 캡터 생성
        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        // 캡터로 저장된 값이 내가 예상한 값과 일치하는지 확인
        Review savedReview = reviewCaptor.getValue();
        assertThat(savedReview.getCampId()).isEqualTo(reviewDTO.getCampId());
        assertThat(savedReview.getUserId()).isEqualTo(reviewDTO.getUserId());
        assertThat(savedReview.getReviewContent()).isEqualTo(reviewDTO.getReviewContent());
        assertThat(savedReview.getRating()).isEqualTo(reviewDTO.getRating());
        assertThat(savedReview.getReviewImageUrl()).isEqualTo(reviewDTO.getReviewImageUrl());

        ArgumentCaptor<MyReview> myReviewCaptor = ArgumentCaptor.forClass(MyReview.class);
        verify(myReviewRepository).save(myReviewCaptor.capture());
        MyReview savedMyReview = myReviewCaptor.getValue();
        assertThat(savedMyReview.getReviewIds()).contains(review.getReviewId());

        verify(ratingSummaryRepository).ratingUpdate(reviewDTO.getCampId(), reviewDTO.getRating());
    }

    @Test
    void 캠핑장_리뷰_이미_존재_예외() {
        // Given
        when(reviewRepository.existsByUserIdAndCampId(anyLong(), anyLong())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> reviewService.createReview(reviewDTO))
                .isInstanceOf(ReviewAlreadyExistsException.class)
                .hasMessage("이미 이 캠핑장에 대한 리뷰를 작성하셨습니다.")
                .satisfies(exception -> {
                    ReviewAlreadyExistsException castedEx = (ReviewAlreadyExistsException) exception;
                    assertThat(castedEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(reviewRepository).existsByUserIdAndCampId(reviewDTO.getUserId(), reviewDTO.getCampId());
        verify(reviewRepository, never()).save(any(Review.class));
        verify(myReviewRepository, never()).findById(anyLong());
        verify(myReviewRepository, never()).save(any(MyReview.class));
        verify(ratingSummaryRepository, never()).ratingUpdate(anyLong(), anyByte());
    }


    // 테스트 데이터 생성을 위한 헬퍼 메소드
    private List<Review> createTestReviews(long campId, int count) {
        List<Review> reviews = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reviews.add(new Review(
                    (long) (i + 1), campId, 100L + i,
                    "리뷰 내용" + (i + 1), (byte) (5),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "image" + (i + 1) + ".jpg"
            ));
        }
        return reviews;
    }

    // Review를 ReviewDTO로 변환하는 헬퍼 메소드
    private ReviewDTO convertToReviewDTO(Review review) {
        ReviewDTO dto = new ReviewDTO();
        dto.setReviewId(review.getReviewId());
        dto.setCampId(review.getCampId());
        dto.setUserId(review.getUserId());
        dto.setReviewContent(review.getReviewContent());
        dto.setRating(review.getRating());
        dto.setReviewImageUrl(review.getReviewImageUrl());
        return dto;
    }
}
