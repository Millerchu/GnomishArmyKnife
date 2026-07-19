package com.gak.passwordmemo.vo;

import java.util.List;

/**
 * 校验查看密码响应。
 */
public record VerifyAccessResponse(String password,
                                   String maskedPassword,
                                   List<PasswordMemoHistoryVO> passwordHistory) {
}
