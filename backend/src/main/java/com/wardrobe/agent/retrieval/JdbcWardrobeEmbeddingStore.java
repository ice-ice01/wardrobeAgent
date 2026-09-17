package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
/**
 * 使用 MySQL/JDBC 持久化衣物向量。
 *
 * <p>当前向量以小端序 BLOB 保存，查询时加载到 Java 内计算相似度。这适合 MVP，
 * 但不是带 ANN 索引的专用向量数据库；数据量很大时应替换 Store 实现。</p>
 */
public class JdbcWardrobeEmbeddingStore implements WardrobeEmbeddingStore {
    private final JdbcTemplate jdbc;

    public JdbcWardrobeEmbeddingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /** 只读取当前用户、当前 Embedding 模型和当前索引版本的数据。 */
    public List<WardrobeEmbeddingRecord> findByUserAndVersion(String userId, String model, String indexVersion) {
        return jdbc.query("""
                        select item_id, user_id, slot, model_name, index_version, embedding_dimension,
                               content_hash, metadata_json, embedding
                        from wardrobe_item_embedding
                        where user_id = ? and model_name = ? and index_version = ?
                        """,
                (rs, rowNum) -> new WardrobeEmbeddingRecord(
                        rs.getString("item_id"), rs.getString("user_id"),
                        WardrobeSlot.valueOf(rs.getString("slot")), rs.getString("model_name"),
                        rs.getString("index_version"), rs.getInt("embedding_dimension"),
                        rs.getString("content_hash"), rs.getString("metadata_json"),
                        decode(rs.getBytes("embedding"), rs.getInt("embedding_dimension"))),
                userId, model, indexVersion);
    }

    @Override
    /** 以 itemId + userId 为业务键覆盖旧向量，避免同一衣物保留多个活动版本。 */
    public void saveAll(List<WardrobeEmbeddingRecord> records) {
        Instant now = Instant.now();
        for (WardrobeEmbeddingRecord record : records) {
            jdbc.update("delete from wardrobe_item_embedding where item_id = ? and user_id = ?",
                    record.itemId(), record.userId());
            jdbc.update("""
                            insert into wardrobe_item_embedding
                            (item_id, user_id, slot, model_name, index_version, embedding_dimension,
                             content_hash, metadata_json, embedding, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    record.itemId(), record.userId(), record.slot().name(), record.model(), record.indexVersion(),
                    record.dimension(), record.contentHash(), record.metadataJson(), encode(record.vector()),
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    @Override
    public void delete(String userId, String itemId) {
        jdbc.update("delete from wardrobe_item_embedding where user_id = ? and item_id = ?", userId, itemId);
    }

    /** 将 float[] 编码为固定字节序的二进制，便于写入 BLOB。 */
    private static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) buffer.putFloat(value);
        return buffer.array();
    }

    /** 从 BLOB 还原向量，并先校验字节长度和声明维度是否一致。 */
    private static float[] decode(byte[] bytes, int dimension) {
        if (bytes == null || bytes.length != dimension * Float.BYTES) {
            throw new IllegalStateException("Invalid persisted wardrobe embedding dimension");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[dimension];
        for (int index = 0; index < dimension; index++) vector[index] = buffer.getFloat();
        return vector;
    }
}
