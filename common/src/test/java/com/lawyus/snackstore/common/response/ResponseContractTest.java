package com.lawyus.snackstore.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应体的特征化测试：`Result` / `PageResult` 是六个服务共同的对外契约，
 * 分页总页数的取整与除零守卫一旦改动，前端翻页会直接错乱。
 */
@DisplayName("Result 与 PageResult 契约")
class ResponseContractTest {

    @Nested
    @DisplayName("PageResult.success 计算总页数")
    class PageComputation {

        @Test
        @DisplayName("总页数向上取整：25 条每页 10 条为 3 页")
        void roundsUp() {
            PageResult<String> result = PageResult.success(List.of("a", "b"), 1L, 10L, 25L);

            assertThat(result.getPages()).isEqualTo(3L);
            assertThat(result.getPageNum()).isEqualTo(1L);
            assertThat(result.getPageSize()).isEqualTo(10L);
            assertThat(result.getTotal()).isEqualTo(25L);
        }

        @Test
        @DisplayName("整除场景不多算一页：20 条每页 10 条为 2 页")
        void exactDivisionDoesNotAddPage() {
            assertThat(PageResult.success(List.of(), 1L, 10L, 20L).getPages()).isEqualTo(2L);
        }

        @Test
        @DisplayName("零总数为 0 页，1 条小于每页容量为 1 页")
        void boundaryTotals() {
            assertThat(PageResult.success(List.of(), 1L, 10L, 0L).getPages()).isZero();
            assertThat(PageResult.success(List.of("a"), 1L, 100L, 1L).getPages()).isEqualTo(1L);
        }

        @Test
        @DisplayName("每页数量为 null 或 0 时返回 0 页而不是抛除零异常")
        void guardAgainstNonPositivePageSize() {
            assertThat(PageResult.success(List.of(), 1L, 0L, 25L).getPages()).isZero();
            assertThat(PageResult.success(List.of(), 1L, null, 25L).getPages()).isZero();
        }

        @Test
        @DisplayName("分页结果继承成功码与数据载荷")
        void carriesSuccessCodeAndData() {
            PageResult<String> result = PageResult.success(List.of("a", "b"), 2L, 10L, 25L);

            assertThat(result.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
            assertThat(result.getMessage()).isEqualTo(ResultCode.SUCCESS.getMessage());
            assertThat(result.getData()).containsExactly("a", "b");
        }
    }

    @Nested
    @DisplayName("Result 工厂方法")
    class ResultFactories {

        @Test
        @DisplayName("success 带数据与自定义消息")
        void successVariants() {
            assertThat(Result.success("x").getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
            assertThat(Result.success("x").getData()).isEqualTo("x");
            assertThat(Result.success("x", "已创建").getMessage()).isEqualTo("已创建");
        }

        @Test
        @DisplayName("failed 按错误码置码且数据为 null")
        void failedCarriesNoData() {
            Result<Object> result = Result.failed(ResultCode.FORBIDDEN);

            assertThat(result.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
            assertThat(result.getMessage()).isEqualTo(ResultCode.FORBIDDEN.getMessage());
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("failed 可用自定义消息覆盖默认文案")
        void failedWithCustomMessage() {
            assertThat(Result.failed(ResultCode.FORBIDDEN, "只能管理员操作").getMessage())
                    .isEqualTo("只能管理员操作");
            assertThat(Result.failed(4001, "自定义").getCode()).isEqualTo(4001);
        }

        @Test
        @DisplayName("参数校验失败与未登录/未授权的快捷方法用对应错误码")
        void shortcutFactoriesUseExpectedCodes() {
            assertThat(Result.validateFailed().getCode()).isEqualTo(ResultCode.VALIDATE_FAILED.getCode());
            assertThat(Result.validateFailed("数量必须大于0").getMessage()).isEqualTo("数量必须大于0");
            assertThat(Result.unauthorized().getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
            assertThat(Result.forbidden().getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
        }
    }
}
