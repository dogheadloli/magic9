package com.stock.repository;

import com.stock.domain.NewsItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    boolean existsByCodeAndUrl(String code, String url);

    List<NewsItem> findTop30ByCodeOrderByPublishTimeDesc(String code);

    List<NewsItem> findTop20ByCodeAndAnalyzedFalseOrderByPublishTimeDesc(String code);
}
