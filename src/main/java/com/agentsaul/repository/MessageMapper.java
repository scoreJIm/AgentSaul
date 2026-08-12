package com.agentsaul.repository;

import com.agentsaul.entity.Message;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper {

    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<Message> findByConversationId(Long conversationId);

    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    @Insert("INSERT INTO messages (conversation_id, role, content, tool_name, created_at) " +
            "VALUES (#{conversationId}, #{role}, #{content}, #{toolName}, NOW())")
    int insert(Message m);

    @Delete("DELETE FROM messages WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(Long conversationId);

    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} AND role IN ('tool_call', 'tool_result') ORDER BY created_at")
    List<Message> findToolCallsByConversationId(Long conversationId);

    @Select("SELECT COUNT(*) FROM messages WHERE created_at >= #{since}")
    long countByCreatedAfter(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM messages WHERE tool_name IS NOT NULL AND created_at >= #{since}")
    long countToolCallsByCreatedAfter(@Param("since") LocalDateTime since);

    @Select("SELECT tool_name, COUNT(*) as cnt FROM messages WHERE tool_name IS NOT NULL AND created_at >= #{since} GROUP BY tool_name ORDER BY cnt DESC")
    List<Map<String, Object>> groupToolUsageByCreatedAfter(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM messages WHERE content LIKE 'Something went wrong:%' AND created_at >= #{since}")
    long countLlmApiErrorsByCreatedAfter(@Param("since") LocalDateTime since);
}
