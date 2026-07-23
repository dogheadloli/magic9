package com.stock.web;

import com.stock.domain.ManualTrade;
import com.stock.journal.CloseRequest;
import com.stock.journal.JournalRequest;
import com.stock.journal.JournalService;
import com.stock.journal.JournalView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实盘记账接口：建仓 / 平仓 / 删除 / 列表(含现价盈亏)。
 */
@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    public JournalView list() {
        return journalService.list();
    }

    @PostMapping
    public ManualTrade create(@RequestBody JournalRequest req) {
        return journalService.create(req);
    }

    @PutMapping("/{id}/close")
    public ManualTrade close(@PathVariable Long id, @RequestBody CloseRequest req) {
        return journalService.close(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        journalService.delete(id);
    }
}
