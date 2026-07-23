package com.stock.service;

import com.stock.datafetch.MarketDataProvider;
import com.stock.datafetch.StockQuote;
import com.stock.domain.Market;
import com.stock.domain.StockPool;
import com.stock.repository.StockPoolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 自选股池管理服务。
 */
@Slf4j
@Service
public class StockPoolService {

    private final StockPoolRepository repository;
    private final MarketDataProvider provider;

    public StockPoolService(StockPoolRepository repository, MarketDataProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    public List<StockPool> list() {
        return repository.findAll();
    }

    /**
     * 新增自选股。若名称为空则自动通过行情接口补全。
     */
    @Transactional
    public StockPool add(String code, String name, String groupName) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("股票代码须为6位数字: " + code);
        }
        Optional<StockPool> exist = repository.findByCode(code);
        StockPool pool = exist.orElseGet(StockPool::new);
        pool.setCode(code);
        Market market = Market.inferByCode(code);
        pool.setMarket(market);
        pool.setGroupName(groupName != null ? groupName : "默认");
        if (name != null && !name.trim().isEmpty()) {
            pool.setName(name.trim());
        } else if (pool.getName() == null || pool.getName().isEmpty()) {
            StockQuote quote = provider.fetchQuote(code, market);
            pool.setName(quote != null ? quote.getName() : code);
        }
        if (pool.getEnabled() == null) {
            pool.setEnabled(true);
        }
        return repository.save(pool);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public StockPool setEnabled(Long id, boolean enabled) {
        StockPool pool = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("自选股不存在: id=" + id));
        pool.setEnabled(enabled);
        return repository.save(pool);
    }

    public List<StockPool> enabledList() {
        return repository.findByEnabledTrue();
    }
}
