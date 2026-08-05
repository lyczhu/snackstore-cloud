package com.lawyus.snackstore.user.infrastructure.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.infrastructure.persistence.do_.UserDO;
import com.lawyus.snackstore.user.infrastructure.persistence.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 管理员口令初始化：启动时若配置了 ADMIN_DEFAULT_PASSWORD 环境变量，则将其同步为管理员口令。
 * 生产环境从密钥库/环境注入口令，避免弱口令或仓库明文泄露；未配置时使用 V2 迁移中的默认强口令。
 */
@Component
public class AdminPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordInitializer.class);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${user.admin.init-phone:13800000000}")
    private String adminPhone;

    @Value("${ADMIN_DEFAULT_PASSWORD:}")
    private String adminDefaultPassword;

    public AdminPasswordInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminDefaultPassword)) {
            log.info("未配置 ADMIN_DEFAULT_PASSWORD，管理员口令保持 V2 迁移默认值");
            return;
        }
        if (adminDefaultPassword.length() < 12) {
            log.warn("ADMIN_DEFAULT_PASSWORD 长度不足 12 位，口令强度过低");
        }

        UserDO admin = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getPhone, adminPhone)
                .eq(UserDO::getRole, UserRole.ADMIN.getCode()));
        if (admin == null) {
            log.warn("未找到管理员账号，跳过口令初始化: phone={}", adminPhone);
            return;
        }
        if (encoder.matches(adminDefaultPassword, admin.getPassword())) {
            log.info("管理员口令已是最新，无需更新: phone={}", adminPhone);
            return;
        }

        admin.setPassword(encoder.encode(adminDefaultPassword));
        userMapper.updateById(admin);
        log.info("管理员口令已从环境变量初始化: phone={}", adminPhone);
    }
}
