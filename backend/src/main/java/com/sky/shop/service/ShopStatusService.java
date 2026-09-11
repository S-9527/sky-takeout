package com.sky.shop.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.sky.common.domain.EnableStatus;
import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorResponse;
import com.sky.common.util.Times;
import com.sky.shop.domain.ShopErrorCode;
import com.sky.shop.domain.ShopStatus;
import com.sky.shop.mapper.ShopStatusMapper;

/**
 * 门店营业状态:读单行配置、切换营业状态。
 *
 * <p>单行表没有 id 参数也没有并发版本号:全员改的都是同一行,"最后写入者生效"是符合预期的语义
 * (营业开关不是需要乐观锁保护的财务数据)。
 */
@Service
public class ShopStatusService {

    private final ShopStatusMapper shopStatusMapper;

    public ShopStatusService(ShopStatusMapper shopStatusMapper) {
        this.shopStatusMapper = shopStatusMapper;
    }

    /**
     * 读取单行配置,缺失即 404。
     *
     * <p>这不是"理论上不会发生":生产若用 {@code spring.flyway.target=1} 跳过种子迁移,
     * 表结构有而配置行没有,此时必须明确报错,而不是悄悄当成长年打烊或长年营业。
     */
    public ShopStatus requireCurrent() {
        ShopStatus status = shopStatusMapper.selectById(ShopStatus.SINGLETON_ID);
        if (status == null) {
            throw new BusinessException(ShopErrorCode.SHOP_STATUS_NOT_FOUND);
        }
        return status;
    }

    /**
     * 门店当前是否营业。
     *
     * <p>跨上下文(下单校验 R1)只应该调用这个方法,不要引用 {@link ShopStatus} 实体——
     * 领域层类型不跨上下文共享(后端架构 §3 L4)。
     */
    public boolean isOpen() {
        return requireCurrent().isOpen();
    }

    /**
     * 切换营业状态 / 改公告 / 改营业时间。
     *
     * <p>{@code isOpen} 必填;其余字段传 {@code null} 表示保持原值(契约原文:"不传表示保持原值")。
     * 因此 {@code notice} 传空串是"清空公告",传 {@code null} 是"别动公告"。
     */
    @Transactional
    public ShopStatus update(Boolean isOpen, String openTime, String closeTime, String notice) {
        ShopStatus current = requireCurrent();

        LocalTime targetOpenTime = openTime == null ? current.getOpenTime() : parseTime(openTime, "openTime");
        LocalTime targetCloseTime = closeTime == null ? current.getCloseTime() : parseTime(closeTime, "closeTime");
        requireValidBusinessHours(targetOpenTime, targetCloseTime);

        ShopStatus update = new ShopStatus();
        update.setId(ShopStatus.SINGLETON_ID);
        // 只有传了值的字段才会进 UPDATE 语句(MyBatis-Plus 默认忽略 null),null 即"保持原值"
        update.setIsOpen(Boolean.TRUE.equals(isOpen) ? EnableStatus.ENABLED : EnableStatus.DISABLED);
        if (openTime != null) {
            update.setOpenTime(targetOpenTime);
        }
        if (closeTime != null) {
            update.setCloseTime(targetCloseTime);
        }
        if (notice != null) {
            update.setNotice(notice);
        }
        shopStatusMapper.updateById(update);

        return requireCurrent();
    }

    /** 营业时间格式非法与逻辑矛盾共用一个错误码:对调用方来说都是"这组时间不能用"。 */
    private static LocalTime parseTime(String raw, String field) {
        try {
            return Times.parseTime(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ShopErrorCode.SHOP_BUSINESS_HOURS_INVALID,
                    "营业时间设置不合理:格式应为 HH:mm 或 HH:mm:ss",
                    List.of(new ErrorResponse.Detail(field, "格式应为 HH:mm 或 HH:mm:ss")));
        }
    }

    /**
     * 开始时间必须早于结束时间。
     *
     * <p>刻意不支持"跨零点营业"(如 22:00–02:00):领域文档没有这个语义,营业状态本来就不由时间推导,
     * 跨零点只会让展示层无法判断"现在属于哪一天"。真需要时再单独定义。
     */
    private static void requireValidBusinessHours(LocalTime openTime, LocalTime closeTime) {
        if (openTime != null && closeTime != null && !openTime.isBefore(closeTime)) {
            throw new BusinessException(ShopErrorCode.SHOP_BUSINESS_HOURS_INVALID,
                    "营业时间设置不合理:开始时间必须早于结束时间",
                    List.of(new ErrorResponse.Detail("openTime", "必须早于 closeTime")));
        }
    }
}
