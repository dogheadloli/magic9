package com.stock.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 币安现货监控池。
 */
@Slf4j
@Service
public class CryptoPoolService {

    private static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9]{5,20}$");

    private final CryptoPoolRepository repository;
    private final BinanceSpotClient binance;

    public CryptoPoolService(CryptoPoolRepository repository, BinanceSpotClient binance) {
        this.repository = repository;
        this.binance = binance;
    }

    public List<CryptoPool> list() {
        return repository.findAll();
    }

    public List<CryptoPool> enabledList() {
        return repository.findByEnabledTrue();
    }

    public Optional<CryptoPool> findBySymbol(String symbol) {
        return repository.findBySymbol(symbol);
    }

    @Transactional
    public CryptoPool add(String rawSymbol, String name, String groupName) {
        String symbol = normalizeSymbol(rawSymbol);
        BinanceSymbolInfo info = binance.resolveSpot(symbol);
        Optional<CryptoPool> exist = repository.findBySymbol(info.getSymbol());
        CryptoPool pool = exist.orElseGet(CryptoPool::new);
        pool.setSymbol(info.getSymbol());
        pool.setQuoteAsset(info.getQuoteAsset());
        pool.setGroupName(groupName != null && !groupName.trim().isEmpty() ? groupName.trim() : "默认");
        if (name != null && !name.trim().isEmpty()) {
            pool.setName(name.trim());
        } else if (pool.getName() == null || pool.getName().isEmpty()) {
            pool.setName(info.getBaseAsset());
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
    public CryptoPool setEnabled(Long id, boolean enabled) {
        CryptoPool pool = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("监控池不存在: id=" + id));
        pool.setEnabled(enabled);
        return repository.save(pool);
    }

    public static String normalizeSymbol(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("交易对不能为空");
        }
        String symbol = raw.trim().toUpperCase(Locale.ROOT)
                .replace("-", "")
                .replace("/", "")
                .replace("_", "")
                .replace(" ", "");
        if (!SYMBOL.matcher(symbol).matches()) {
            throw new IllegalArgumentException("交易对格式无效，例如 BTCUSDT: " + raw);
        }
        return symbol;
    }
}
