package com.lawyus.snackstore.common.dto;

import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨服务数据契约的特征化测试。
 *
 * <p>搜索读模型有两条通道、两个 DTO：事件通道用 {@link ProductSearchSyncMessage}（带事件元数据），
 * 拉取通道用 {@link ProductSearchItemDTO}（只有商品快照字段）。AGENTS.md 明确两者不得混用。
 * 这类「一个 DTO 加了字段、另一个忘了加」的漂移只能靠编译期之外的断言拦住，因此本测试用反射
 * 比对字段集合，而不是逐个 getter 断言。
 */
@DisplayName("搜索数据契约与认证常量")
class SearchDataContractTest {

    private static Set<String> instanceFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    @DisplayName("拉取通道的 ItemDTO 不得携带事件元数据")
    void itemDtoMustNotLeakEventMetadata() {
        assertThat(instanceFieldNames(ProductSearchItemDTO.class))
                .doesNotContain("eventId", "eventTime", "changeType");
    }

    @Test
    @DisplayName("事件通道的 SyncMessage 必须携带事件元数据")
    void syncMessageCarriesEventMetadata() {
        assertThat(instanceFieldNames(ProductSearchSyncMessage.class))
                .contains("eventId", "eventTime", "changeType");
    }

    @Test
    @DisplayName("SyncMessage 的字段必须覆盖 ItemDTO，否则事件转索引文档会静默丢字段")
    void syncMessageCoversAllItemDtoFields() {
        Set<String> messageFields = instanceFieldNames(ProductSearchSyncMessage.class);

        assertThat(instanceFieldNames(ProductSearchItemDTO.class))
                .isSubsetOf(messageFields);
    }

    @Test
    @DisplayName("构造 SyncMessage 即自动生成 eventId 与 eventTime")
    void syncMessageInitializesEventMetadataInConstructor() {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();

        assertThat(message.getEventId()).isNotBlank();
        assertThat(UUID.fromString(message.getEventId())).isNotNull();
        assertThat(message.getEventTime()).isNotNull();
        assertThat(new ProductSearchSyncMessage().getEventId()).isNotEqualTo(message.getEventId());
    }

    @Test
    @DisplayName("变更类型枚举保持五个值（下架与库存变更是独立语义）")
    void changeTypeKeepsFiveValues() {
        assertThat(Arrays.stream(ProductSearchSyncMessage.ChangeType.values())
                .map(Enum::name).toList())
                .containsExactlyInAnyOrder(
                        "CREATED", "UPDATED", "DELETED", "STATUS_CHANGED", "STOCK_CHANGED");
    }

    @Test
    @DisplayName("搜索条件默认只兜分页与排序，不兜 status")
    void searchDtoDefaultsDoNotFilterStatus() {
        ProductSearchDTO dto = new ProductSearchDTO();

        // 「默认只搜上架商品」是在服务层补的，DTO 本身把 status 留空；
        // 这条断言防止有人误以为 DTO 已过滤，或偷偷在 DTO 上加默认值改变语义。
        assertThat(dto.getStatus()).isNull();
        assertThat(dto.getPageNum()).isEqualTo(1);
        assertThat(dto.getPageSize()).isEqualTo(10);
        assertThat(dto.getSortOrder()).isEqualTo("desc");
    }

    @Test
    @DisplayName("网关注入下游的身份头名与管理员角色字面量不得漂移")
    void authConstantsKeepHeaderAndRoleLiterals() {
        // 下游服务只认这三个头名，网关与五个服务共享同一份字面量
        assertThat(AuthConstants.HEADER_USER_ID).isEqualTo("X-User-Id");
        assertThat(AuthConstants.HEADER_USERNAME).isEqualTo("X-Username");
        assertThat(AuthConstants.HEADER_USER_ROLE).isEqualTo("X-User-Role");
        // 角色值是小写 admin，比较处按大小写不敏感处理
        assertThat(AuthConstants.ROLE_ADMIN).isEqualTo("admin");
    }
}
