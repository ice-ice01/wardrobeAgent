package com.wardrobe.agent.media;

/** 上传成功后返回的安全文件引用，不包含服务器磁盘路径。 */
public record MediaFileView(String id, String purpose, String mimeType, long sizeBytes, String url) {}
