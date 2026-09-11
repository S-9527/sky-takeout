package com.sky.shop.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalTime;

import com.sky.common.domain.EnableStatus;
import com.sky.common.error.BusinessException;
import com.sky.shop.domain.ShopErrorCode;
import com.sky.shop.domain.ShopStatus;
import com.sky.shop.mapper.ShopStatusMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopStatusServiceTest {

    private final ShopStatusMapper shopStatusMapper = mock(ShopStatusMapper.class);
    private final ShopStatusService shopStatusService = new ShopStatusService(shopStatusMapper);

    private static ShopStatus seeded() {
        ShopStatus status = new ShopStatus();
        status.setId(ShopStatus.SINGLETON_ID);
        status.setIsOpen(EnableStatus.ENABLED);
        status.setOpenTime(LocalTime.of(9, 0));
        status.setCloseTime(LocalTime.of(22, 0));
        status.setNotice("本店新开张");
        return status;
    }

    @Test
    void requireCurrentReturnsSingleRow() {
        ShopStatus seeded = seeded();
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded);

        assertThat(shopStatusService.requireCurrent()).isSameAs(seeded);
    }

    /** 生产可能只跑 V1(不灌种子),那时这张表是空的,必须 404 而不是当成长年打烊。 */
    @Test
    void missingRowIs404() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(null);

        assertThatThrownBy(shopStatusService::requireCurrent)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ShopErrorCode.SHOP_STATUS_NOT_FOUND));
    }

    @Test
    void isOpenReflectsTheFlag() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());
        assertThat(shopStatusService.isOpen()).isTrue();

        ShopStatus closed = seeded();
        closed.setIsOpen(EnableStatus.DISABLED);
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(closed);
        assertThat(shopStatusService.isOpen()).isFalse();
    }

    /** 未传的字段必须保持原值:UPDATE 语句里不能出现这些列。 */
    @Test
    void updateOnlyIsOpenLeavesEverythingElseUntouched() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        shopStatusService.update(false, null, null, null);

        ShopStatus update = capturedUpdate();
        assertThat(update.getIsOpen()).isEqualTo(EnableStatus.DISABLED);
        assertThat(update.getOpenTime()).isNull();
        assertThat(update.getCloseTime()).isNull();
        assertThat(update.getNotice()).isNull();
    }

    @Test
    void updateAcceptsShortTimeFormatAndNormalisesIt() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        shopStatusService.update(true, "08:30", "20:00:00", "今日 20:00 打烊");

        ShopStatus update = capturedUpdate();
        assertThat(update.getOpenTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(update.getCloseTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(update.getNotice()).isEqualTo("今日 20:00 打烊");
    }

    @Test
    void updateWithEmptyNoticeClearsIt() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        shopStatusService.update(true, null, null, "");

        assertThat(capturedUpdate().getNotice()).isEmpty();
    }

    @Test
    void updateRejectsIllegalTimeFormatWithTheContractErrorCode() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        assertThatThrownBy(() -> shopStatusService.update(true, "9am", null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ShopErrorCode.SHOP_BUSINESS_HOURS_INVALID);
                    assertThat(ex.details()).extracting("field").containsExactly("openTime");
                });
        verify(shopStatusMapper, never()).updateById(any(ShopStatus.class));
    }

    @Test
    void updateRejectsStartNotBeforeEnd() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        assertThatThrownBy(() -> shopStatusService.update(true, "23:00", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ShopErrorCode.SHOP_BUSINESS_HOURS_INVALID));
        verify(shopStatusMapper, never()).updateById(any(ShopStatus.class));
    }

    @Test
    void updateRejectsEqualStartAndEnd() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        assertThatThrownBy(() -> shopStatusService.update(true, "09:00", "09:00", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ShopErrorCode.SHOP_BUSINESS_HOURS_INVALID));
    }

    @Test
    void updateRejectsCorruptTimeFormatWithFieldDetail() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(seeded());

        assertThatThrownBy(() -> shopStatusService.update(true, null, "24:00", null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ShopErrorCode.SHOP_BUSINESS_HOURS_INVALID);
                    assertThat(ex.details()).extracting("field").containsExactly("closeTime");
                });
    }

    @Test
    void updateRejectsUnknownRowWithoutWriting() {
        when(shopStatusMapper.selectById(ShopStatus.SINGLETON_ID)).thenReturn(null);

        assertThatThrownBy(() -> shopStatusService.update(true, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ShopErrorCode.SHOP_STATUS_NOT_FOUND));
        verify(shopStatusMapper, never()).updateById(any(ShopStatus.class));
    }

    private ShopStatus capturedUpdate() {
        ArgumentCaptor<ShopStatus> captor = ArgumentCaptor.forClass(ShopStatus.class);
        verify(shopStatusMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(ShopStatus.SINGLETON_ID);
        return captor.getValue();
    }
}
