package com.example.repository;

import com.example.model.JiraConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JiraConnectionRepository extends JpaRepository<JiraConnection, Long> {
} 