package com.stock.web;

import com.stock.alert.AlertService;
import com.stock.signal.SignalFactor;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 预警渠道自测接口。
 */
@RestController
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 发送一条样例预警，验证通知渠道是否连通。 */
    @PostMapping("/api/alert/test")
    public Map<String, Object> test(@RequestParam(defaultValue = "BUY_LOW9") SignalType type) {
        SignalResult s = new SignalResult();
        s.setCode("600519");
        s.setName("贵州茅台");
        s.setTradeDate(LocalDate.now());
        s.setType(type);
        s.setScore(3);
        s.setMaxScore(3);
        s.setStrong(true);
        if (type == SignalType.BUY_LOW9) {
            s.getFactors().add(SignalFactor.TD_LOW9);
            s.getFactors().add(SignalFactor.MACD_BULL_DIV);
            s.getFactors().add(SignalFactor.VOL_SHRINK);
            s.getFactors().add(SignalFactor.MA_SUPPORT);
        } else {
            s.getFactors().add(SignalFactor.TD_HIGH9);
            s.getFactors().add(SignalFactor.MACD_BEAR_DIV);
            s.getFactors().add(SignalFactor.VOL_SURGE_STALL);
            s.getFactors().add(SignalFactor.MA_FAR);
        }
        s.getDetail().put("close", 1207.68);
        s.getDetail().put("changePct", -1.21);
        s.getDetail().put("ma20", 1268.18);
        s.getDetail().put("ma60", 1352.64);
        s.getDetail().put("bias20", -4.77);
        s.getDetail().put("bias60", -10.7);
        if (type == SignalType.BUY_LOW9) {
            s.setEntryPrice(1207.68);
            s.setStopPrice(1175.6);
            s.setTargetPrice(1268.18);
            s.setLatestExitDate(LocalDate.now().plusDays(21));
        }
        String channels = alertService.dispatch(s);
        Map<String, Object> result = new HashMap<>();
        result.put("dispatchedChannels", channels);
        return result;
    }
}
