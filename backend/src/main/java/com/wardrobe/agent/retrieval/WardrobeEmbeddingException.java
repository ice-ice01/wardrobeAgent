package com.wardrobe.agent.retrieval;

/** Embedding 生成、序列化或索引一致性失败时使用的领域异常。 */
public class WardrobeEmbeddingException extends RuntimeException {
    public WardrobeEmbeddingException(String message) { super(message); }
    public WardrobeEmbeddingException(String message, Throwable cause) { super(message, cause); }
}
