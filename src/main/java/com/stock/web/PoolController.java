package com.stock.web;

import com.stock.domain.StockPool;
import com.stock.service.StockPoolService;
import com.stock.web.dto.AddStockRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 自选股池管理接口。
 */
@RestController
@RequestMapping("/api/pool")
public class PoolController {

    private final StockPoolService service;

    public PoolController(StockPoolService service) {
        this.service = service;
    }

    @GetMapping
    public List<StockPool> list() {
        return service.list();
    }

    @PostMapping
    public StockPool add(@Valid @RequestBody AddStockRequest req) {
        return service.add(req.getCode(), req.getName(), req.getGroup());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PutMapping("/{id}/enabled")
    public StockPool setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setEnabled(id, enabled);
    }
}
