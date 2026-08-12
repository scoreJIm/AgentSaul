package com.agentsaul.repository;

import com.agentsaul.entity.Conversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationMapper {

    @Select("SELECT * FROM conversations WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<Conversation> findByUserId(Long userId);

    @Select("SELECT * FROM conversations WHERE user_id IS NULL ORDER BY updated_at DESC")
    List<Conversation> findAll();

    @Select("SELECT * FROM conversations WHERE id = #{id}")
    Conversation findById(Long id);

    @Select("SELECT * FROM conversations WHERE id = #{id} AND (user_id = #{userId} OR user_id IS NULL)")
    Conversation findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    @Insert("INSERT INTO conversations (user_id, title, created_at, updated_at) VALUES (#{userId}, #{title}, NOW(), NOW())")
    int insert(Conversation c);

    @Update("UPDATE conversations SET title = #{title}, updated_at = NOW() WHERE id = #{id}")
    int updateTitle(Conversation c);

    @Delete("DELETE FROM conversations WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM conversations WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
