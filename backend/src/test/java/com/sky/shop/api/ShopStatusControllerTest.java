package com.sky.shop.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.sky.common.domain.EnableStatus;
import com.sky.shop.api.dto.ShopStatusUpdateRequest;
import com.sky.shop.api.dto.ShopStatusViewResponse;
import com.sky.shop.domain.ShopStatus;
import com.sky.shop.service.ShopStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopStatusControllerTest {

    private final ShopStatusService shopStatusService = mock(ShopStatusService.class);
    private final ShopStatusAdminController adminController = new ShopStatusAdminController(shopStatusService);
    private final ShopStatusCustomerController customerController =
            new ShopStatusCustomerController(shopStatusService);

    private static ShopStatus openShop() {
        ShopStatus status = new ShopStatus();
        status.setId(ShopStatus.SINGLETON_ID);
        status.setIsOpen(EnableStatus.ENABLED);
        status.setOpenTime(LocalTime.of(9, 0));
        status.setCloseTime(LocalTime.of(22, 0));
        status.setNotice("本店新开张");
        status.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        return status;
    }

    @Test
    void adminGetFormatsTimesAndExposesUpdatedAt() {
        when(shopStatusService.requireCurrent()).thenReturn(openShop());

        var response = adminController.get();

        assertThat(response.isOpen()).isTrue();
        assertThat(response.openTime()).isEqualTo("09:00:00");
        assertThat(response.closeTime()).isEqualTo("22:00:00");
        assertThat(response.notice()).isEqualTo("本店新开张");
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void adminUpdatePassesEveryFieldThrough() {
        when(shopStatusService.update(false, "08:30", "20:00", "提前打烊")).thenReturn(openShop());

        adminController.update(new ShopStatusUpdateRequest(false, "08:30", "20:00", "提前打烊"));

        verify(shopStatusService).update(false, "08:30", "20:00", "提前打烊");
    }

    @Test
    void adminUpdateToleratesMissingOptionalFields() {
        when(shopStatusService.update(true, null, null, null)).thenReturn(openShop());

        adminController.update(new ShopStatusUpdateRequest(true, null, null, null));

        verify(shopStatusService).update(true, null, null, null);
    }

    /** 顾客端视图必须是"薄"的:结构上就没有审计字段,而不是靠人记得不填。 */
    @Test
    void customerViewHasExactlyFourFieldsAndNoAuditTrail() {
        when(shopStatusService.requireCurrent()).thenReturn(openShop());

        ShopStatusViewResponse response = customerController.get();

        assertThat(response.isOpen()).isTrue();
        assertThat(response.openTime()).isEqualTo("09:00:00");
        assertThat(ShopStatusViewResponse.class.getRecordComponents()).hasSize(4);
    }

    @Test
    void closedShopStillReturns200ForCustomers() {
        ShopStatus closed = openShop();
        closed.setIsOpen(EnableStatus.DISABLED);
        when(shopStatusService.requireCurrent()).thenReturn(closed);

        assertThat(customerController.get().isOpen()).isFalse();
    }
}
