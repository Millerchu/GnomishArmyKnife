package com.gak.passwordmemo.vo;
/** 可供外部应用引用的安全摘要，不包含密码或联系方式。 */
public record PasswordMemoReferenceVO(Long id,String siteName,String username) {}
