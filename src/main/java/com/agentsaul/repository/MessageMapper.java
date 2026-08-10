package com.agentsaul.repository;

import com.agentsaul.entity.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;

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
}
