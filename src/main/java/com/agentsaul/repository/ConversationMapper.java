package com.agentsaul.repository;

import com.agentsaul.entity.Conversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationMapper {

    @Select("SELECT * FROM conversations ORDER BY updated_at DESC")
    List<Conversation> findAll();

    @Select("SELECT * FROM conversations WHERE id = #{id}")
    Conversation findById(Long id);

    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    @Insert("INSERT INTO conversations (title, created_at, updated_at) VALUES (#{title}, NOW(), NOW())")
    int insert(Conversation c);

    @Update("UPDATE conversations SET title = #{title}, updated_at = NOW() WHERE id = #{id}")
    int updateTitle(Conversation c);

    @Delete("DELETE FROM conversations WHERE id = #{id}")
    int deleteById(Long id);
}
