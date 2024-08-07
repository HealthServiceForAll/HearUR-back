package com.example.hearurbackend.repository;

import com.example.hearurbackend.entity.community.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
}
