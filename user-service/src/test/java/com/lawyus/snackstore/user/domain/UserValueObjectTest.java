package com.lawyus.snackstore.user.domain;

import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.domain.common.model.PageSpecification;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用户域值对象与分页规范的特征化测试：这些是「构造即校验」的入口，
 * 口令的字节上限（BCrypt 静默截断风险）、手机号格式、以及分页兜底区间都属于安全与稳定性约束。
 */
@DisplayName("用户域值对象与分页规范")
class UserValueObjectTest {

    private static final PasswordEncoder ENCODER_STUB = stubEncoder();

    private static PasswordEncoder stubEncoder() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(org.mockito.ArgumentMatchers.any())).thenReturn("hashed");
        return encoder;
    }

    @Test
    @DisplayName("口令：拒绝空白，并按 UTF-8 字节数而非字符数限制 72")
    void passwordEnforcesByteLimit() {
        assertThatThrownBy(() -> Password.fromRaw(null, ENCODER_STUB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码不能为空");
        assertThatThrownBy(() -> Password.fromRaw("   ", ENCODER_STUB))
                .isInstanceOf(IllegalArgumentException.class);

        // 72 个 ASCII 字符 = 72 字节：BCrypt 边界内，必须放行
        assertThat(Password.fromRaw("a".repeat(72), ENCODER_STUB).getEncodedValue()).isEqualTo("hashed");

        // 73 个 ASCII 字符：超出 BCrypt 上限（会被静默截断，因此直接拒绝）
        assertThatThrownBy(() -> Password.fromRaw("a".repeat(73), ENCODER_STUB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");

        // 24 个中文字符 = 72 字节：字符数远小于 72 但字节数达上限，仍然合法
        assertThat(Password.fromRaw("密".repeat(24), ENCODER_STUB)).isNotNull();

        // 25 个中文字符 = 75 字节：必须拒绝，证明口径是字节而不是字符
        assertThatThrownBy(() -> Password.fromRaw("密".repeat(25), ENCODER_STUB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("口令：toString 输出掩码，绝不泄漏散列值到日志")
    void passwordMasksItselfInToString() {
        Password password = Password.fromEncoded("hashed-value-that-must-not-leak");

        assertThat(password.toString()).isEqualTo("********");
        assertThat(password.getEncodedValue()).startsWith("hashed-value");
    }

    @Test
    @DisplayName("口令：fromEncoded 也拒绝空白，matches 委托编码器")
    void passwordDelegatesMatching() {
        assertThatThrownBy(() -> Password.fromEncoded(" "))
                .isInstanceOf(IllegalArgumentException.class);

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("right", "hashed")).thenReturn(true);
        assertThat(Password.fromEncoded("hashed").matches("right", encoder)).isTrue();
    }

    @Test
    @DisplayName("手机号：只接受 1 开头、第二位 3-9 的 11 位号码")
    void phoneFormatIsValidated() {
        assertThat(Phone.of("13800000000").getValue()).isEqualTo("13800000000");

        for (String bad : new String[]{"12800000000", "1380000000", "138000000000", "23800000000", null, " "}) {
            assertThatThrownBy(() -> Phone.of(bad))
                    .as("应拒绝手机号: %s", bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("手机号格式不正确");
        }
    }

    @Test
    @DisplayName("收货人：姓名必填，手机号沿用 Phone 校验")
    void receiverInfoIsValidated() {
        assertThatThrownBy(() -> ReceiverInfo.of(" ", "13800000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收货人姓名不能为空");
        assertThatThrownBy(() -> ReceiverInfo.of("张三", "12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号格式不正确");
    }

    @Test
    @DisplayName("详细地址：detail 必填，拼接时跳过缺失的行政区划")
    void addressDetailConcatenates() {
        assertThatThrownBy(() -> AddressDetail.of("浙江省", "杭州市", "西湖区", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("详细地址不能为空");

        assertThat(AddressDetail.of("浙江省", "杭州市", "西湖区", "文一西路 1 号").getFullAddress())
                .isEqualTo("浙江省杭州市西湖区文一西路 1 号");
        assertThat(AddressDetail.of(null, null, null, "文一西路 1 号").getFullAddress())
                .isEqualTo("文一西路 1 号");
    }

    @Test
    @DisplayName("用户状态：0=禁用 1=启用，其余值拒绝")
    void userStatusMapping() {
        assertThat(UserStatus.fromCode(0).isEnabled()).isFalse();
        assertThat(UserStatus.fromCode(1).isEnabled()).isTrue();
        assertThatThrownBy(() -> UserStatus.fromCode(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无效的用户状态: 2");
    }

    @Test
    @DisplayName("分页规范：非法页码与页容量被兜底而不是原样下发")
    void pageSpecificationClamps() {
        assertThat(PageSpecification.of(0, 10).pageNum()).isEqualTo(1);
        assertThat(PageSpecification.of(-5, 10).pageNum()).isEqualTo(1);
        assertThat(PageSpecification.of(1, 0).pageSize()).isEqualTo(10);
        assertThat(PageSpecification.of(1, 101).pageSize()).isEqualTo(10);
        assertThat(PageSpecification.of(2, 20)).isEqualTo(new PageSpecification(2, 20));
    }

    @Test
    @DisplayName("用户聚合：新建即启用，禁用/启用切换生效，仅 ADMIN 为管理员")
    void userAggregateStateTransitions() {
        User user = User.create(Phone.of("13800000000"), Password.fromEncoded("hashed"), UserRole.USER);

        assertThat(user.isActive()).isTrue();
        assertThat(user.isAdmin()).isFalse();

        user.disable();
        assertThat(user.isActive()).isFalse();

        user.enable();
        assertThat(user.isActive()).isTrue();

        User admin = User.create(Phone.of("13800000001"), Password.fromEncoded("hashed"), UserRole.ADMIN);
        assertThat(admin.isAdmin()).isTrue();
    }
}
