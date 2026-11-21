package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    List<AnalyticsEvent> findByShortCode(String shortCode);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.shortCode = :shortCode")
    long countByShortCode(@Param("shortCode") String shortCode);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.shortCode = :shortCode " +
           "AND e.timestamp BETWEEN :startTime AND :endTime")
    long countByShortCodeAndTimestampBetween(
        @Param("shortCode") String shortCode,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT e.country, COUNT(e) FROM AnalyticsEvent e " +
           "WHERE e.shortCode = :shortCode AND e.country IS NOT NULL " +
           "GROUP BY e.country ORDER BY COUNT(e) DESC")
    List<Object[]> getTopCountriesByShortCode(@Param("shortCode") String shortCode);
}
