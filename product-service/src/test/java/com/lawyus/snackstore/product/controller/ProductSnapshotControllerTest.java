package com.lawyus.snackstore.product.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.product.exception.BusinessException;
import com.lawyus.snackstore.product.model.dto.ProductSnapshotQueryDTO;
import com.lawyus.snackstore.product.model.vo.ProductSnapshotVO;
import com.lawyus.snackstore.product.service.ProductSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品变更快照的访问控制：网关已按 admin-only 拦截，这里是第二层断言。
 *
 * <p>快照包含价格/库存变更历史，属管理数据。缺角色头（内网直连）与普通用户
 * 都必须被拒，且不得触达 service；管理员放行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductSnapshotController 管理员断言")
class ProductSnapshotControllerTest {

    @Mock
    private ProductSnapshotService snapshotService;

    private ProductSnapshotController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductSnapshotController(snapshotService);
    }

    @Test
    @DisplayName("管理员可查快照详情")
    void adminCanReadSnapshot() {
        ProductSnapshotVO vo = new ProductSnapshotVO();
        vo.setId(9L);
        when(snapshotService.getSnapshotById(9L)).thenReturn(vo);

        Result<ProductSnapshotVO> result = controller.getSnapshotById(9L, "ADMIN");

        assertThat(result.getData()).isSameAs(vo);
    }

    @Test
    @DisplayName("普通用户被拒且不触达 service")
    void nonAdminIsRejected() {
        BusinessException error = catchThrowableOfType(BusinessException.class,
                () -> controller.getSnapshotById(9L, "user")
        );

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(snapshotService, never()).getSnapshotById(9L);
    }

    @Test
    @DisplayName("缺角色头（内网直连绕过网关）同样被拒")
    void missingRoleHeaderIsRejected() {
        BusinessException error = catchThrowableOfType(BusinessException.class,
                () -> controller.getSnapshotList(new ProductSnapshotQueryDTO(), null));

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(snapshotService, never()).getSnapshotList(org.mockito.ArgumentMatchers.any());
    }
}
