package com.lawyus.snackstore.user.infrastructure.persistence.do_;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_user")
public class UserDO {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String phone;
    
    private String password;
    
    private String nickname;
    
    private String avatar;
    
    private String role;
    
    private Integer status;
    
    @TableLogic
    private Integer deleted;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
